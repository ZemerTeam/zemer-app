package com.jtech.zemer.playback

import com.jtech.zemer.models.MediaMetadata
import org.fcast.sender_sdk.DeviceInfo

/** Result of a user-initiated cast connect, for the picker to surface. */
sealed interface CastConnectResult {
    data object Connected : CastConnectResult

    /** No playable stream URL could be resolved for the current item — nothing to cast. */
    data object NoStream : CastConnectResult

    /** The device couldn't be connected: unresolvable address, connect error, refusal, or timeout. */
    data class Failed(val deviceName: String) : CastConnectResult
}

/**
 * Whether the tapped device should be dropped from the picker after this result. Only [CastConnectResult.Failed]
 * proves the *device* unreachable (see [FCastDiscoveryHandler.pruneDevice]); [CastConnectResult.NoStream] is
 * our stream resolution failing — the device was never even tried and must stay listed.
 */
fun CastConnectResult.shouldPruneDevice(): Boolean = this is CastConnectResult.Failed

/**
 * Orchestrates a user-initiated connect from the picker: pause local playback, resolve the stream,
 * fill in the device's addresses if discovery never resolved them, issue the connect, and wait for
 * the receiver to actually report Connected — so the UI can tell the user what happened instead of
 * failing silently (the old flow dismissed the sheet immediately and swallowed every failure into
 * Crashlytics, which read as "tap connect and nothing happens"). Owned by [MusicService]
 * (process-scoped) like the rest of the cast control plane; must be called on the main thread
 * (it reads the player).
 */
class CastConnector(private val service: MusicService) {
    private val addressResolver by lazy { CastDeviceAddressResolver(service) }

    suspend fun connect(device: DeviceInfo, metadata: MediaMetadata?): CastConnectResult {
        val handler = service.discoveryHandler
        val result = doConnect(device, metadata, handler)
        if (result.shouldPruneDevice()) handler.pruneDevice(device.name)
        return result
    }

    private suspend fun doConnect(
        device: DeviceInfo,
        metadata: MediaMetadata?,
        handler: FCastDiscoveryHandler,
    ): CastConnectResult {
        val player = service.player
        player.pause()
        val currentId = player.currentMediaItem?.mediaId
        val streamUrl = currentId?.let { service.resolveStreamUrl(it) }
            ?: service.currentStreamUrl
            ?: return CastConnectResult.NoStream
        // Resume position, clamped away from the track end. Connecting at the exact instant a track is
        // ending locally would otherwise cast the outgoing item at pos==duration — a full progress bar
        // that immediately trips the end-of-track auto-advance. At the boundary, start from 0 instead;
        // the queue's upcoming PLAYLIST_CHANGED reloads the correct (now-current) item either way.
        val posMs = player.currentPosition
        val durMs = player.duration
        val resumeSec = if (durMs > 0 && posMs >= durMs - 1500) 0.0 else (posMs / 1000.0).coerceAtLeast(0.0)
        if (!addressResolver.refreshAddresses(device)) return CastConnectResult.Failed(device.name)
        // Route the stream through the phone-side relay (Stage 2 of the cast-403 fix) whenever
        // possible; relayedStreamUrl falls back to the direct googlevideo URL when the relay can't
        // serve. currentId can be null only on the currentStreamUrl fallback path above, where no
        // media id exists to token — direct URL there too.
        service.castStreamRelay.receiverAddress = CastConnect.relayTargetAddress(device.addresses)
        val castUrl = currentId?.let { service.relayedStreamUrl(it, streamUrl) } ?: streamUrl
        val issued = handler.connectTo(
            deviceInfo = device,
            streamUrl = castUrl,
            contentType = service.currentContentType,
            metadata = metadata?.toCastMetadata(),
            resumePosition = resumeSec,
            // Captures the (process-scoped) controller, so the SDK's end-of-track callback keeps
            // auto-advancing even if the UI Activity is later destroyed.
            onTrackEnded = { service.castController.advanceRemoteAfterEnd() },
            // Receiver fetch failures (e.g. googlevideo 403ing the receiver's connection) escalate
            // through the recovery ladder instead of silently killing the session.
            onPlaybackError = { message -> service.castController.onRemotePlaybackError(message) },
        )
        if (!issued) return CastConnectResult.Failed(device.name)
        // Record what the receiver will be playing so the first PLAYLIST_CHANGED doesn't reload it.
        service.castController.markRemoteLoaded(currentId)
        return when (CastConnect.awaitOutcome(handler.remoteConnectionState)) {
            CastConnectOutcome.CONNECTED -> CastConnectResult.Connected
            CastConnectOutcome.FAILED -> CastConnectResult.Failed(device.name)
            CastConnectOutcome.TIMED_OUT -> {
                // Abort the still-pending attempt so it can't surprise-connect after the user was
                // already told it failed (and recover the local player via the disconnect path).
                handler.disconnect()
                CastConnectResult.Failed(device.name)
            }
        }
    }
}
