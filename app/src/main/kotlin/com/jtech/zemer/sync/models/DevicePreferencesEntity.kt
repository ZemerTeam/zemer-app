package com.jtech.zemer.sync.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * A timestamp on the content server's `/prefs` wire: UTC ISO-8601 with `Z`. Written with millis (the
 * server rejects any other shape); read with any fraction length (0-9 digits, as imported docs carry).
 */
object IsoDateSerializer : KSerializer<Date> {
    private val writer = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    override val descriptor = PrimitiveSerialDescriptor("IsoDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(writer.format(value.toInstant()))
    override fun deserialize(decoder: Decoder): Date = Date.from(Instant.parse(decoder.decodeString()))
}

/**
 * Content filter configuration for a device
 */
@Serializable
data class DeviceContentFilters(
    val enableContentFilters: Boolean = true,
    val allowFemaleSingers: Boolean = false,
    val blockVideos: Boolean = false,
    // Nullable so an UNSET podcast field (a doc written before podcast-blocking shipped, or by an authority
    // who never touched it) is distinguishable from an explicit false. When unset, toConfig() derives it
    // from blockVideos — so a sync-account video-blocker is locked out of podcasts too. Once a client
    // writes an explicit value it is respected.
    val blockPodcasts: Boolean? = null,
    val femalePasscodeHash: String? = null
) {
    companion object {
    }

    /**
     * Convert from local ContentFilterConfig to the synced model
     */
    fun fromConfig(config: com.jtech.zemer.utils.ContentFilterConfig): DeviceContentFilters {
        return DeviceContentFilters(
            enableContentFilters = config.filtersEnabled,
            allowFemaleSingers = config.allowFemaleSingers,
            blockVideos = config.blockVideos,
            blockPodcasts = config.blockPodcasts,
            femalePasscodeHash = config.femalePasscodeHash
        )
    }

    /**
     * Convert to local ContentFilterConfig. An unset blockPodcasts is coupled to blockVideos so a
     * video-blocking account restores with podcasts blocked without needing the server field backfilled.
     */
    fun toConfig(): com.jtech.zemer.utils.ContentFilterConfig {
        return com.jtech.zemer.utils.ContentFilterConfig(
            filtersEnabled = enableContentFilters,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
            blockPodcasts = blockPodcasts ?: blockVideos,
            femalePasscodeHash = femalePasscodeHash
        )
    }
}

@Serializable
data class DeviceMetadata(
    val deviceName: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val appVersion: String = "",
    @Serializable(with = IsoDateSerializer::class) val firstSeen: Date? = null,
    @Serializable(with = IsoDateSerializer::class) val lastSeen: Date? = null
) {
    companion object {
    }

}

/**
 * Simple device data for storing in user document
 */
@Serializable
data class UserDeviceData(
    val deviceId: String = "",
    val deviceInfo: DeviceMetadata = DeviceMetadata(),
    val contentFilters: DeviceContentFilters = DeviceContentFilters(),
    @Serializable(with = IsoDateSerializer::class) val createdAt: Date? = null,
    val lastSyncTime: Long = -1
)

/**
 * The synced `/prefs` document: content filter preferences for a user with multiple devices.
 * All devices are stored in one document per user for easy browsing and searching.
 */
@Serializable
data class DevicePreferencesEntity(
    val userId: String = "",
    val userEmail: String = "",
    val contentFilters: DeviceContentFilters = DeviceContentFilters(),
    val deviceInfo: DeviceMetadata = DeviceMetadata(),
    val devices: List<UserDeviceData> = emptyList(),
    @Serializable(with = IsoDateSerializer::class) val createdAt: Date? = null,
    @Serializable(with = IsoDateSerializer::class) val updatedAt: Date? = null
) {
    companion object {
    }
}