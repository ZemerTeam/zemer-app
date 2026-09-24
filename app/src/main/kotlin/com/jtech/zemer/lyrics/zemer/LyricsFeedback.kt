package com.jtech.zemer.lyrics.zemer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The user's lyrics feedback to the Zemer queue (report wrong / submit an edit), launched on a scope that
 * OUTLIVES the menu sheet. The report action dismisses the sheet first, and the sheet's own
 * `rememberCoroutineScope` is cancelled the moment it leaves composition, so a POST launched there was
 * cancelled mid-flight and never reached the server. The ViewModel owns one of these on `viewModelScope`.
 * The device id and the two client calls are injected so the mechanism is JVM-tested.
 */
class LyricsFeedback(
    private val scope: CoroutineScope,
    private val deviceId: suspend () -> String?,
    private val report: suspend (videoId: String, device: String) -> Boolean = ZemerLyricsClient::reportLyrics,
    private val submit: suspend (videoId: String, text: String, device: String) -> Boolean = { id, text, device -> ZemerLyricsClient.submitLyrics(id, text, device) },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    /** Two distinct devices reporting within 30 days make the server hide the row until re-verified. */
    fun reportWrong(videoId: String, onAccepted: suspend () -> Unit) = launch(onAccepted) { device -> report(videoId, device) }

    /** A saved edit is also a submission: served to others only once a second device agrees or the recording confirms it. */
    fun submitEdit(videoId: String, text: String, onAccepted: suspend () -> Unit) = launch(onAccepted) { device -> submit(videoId, text, device) }

    private fun launch(onAccepted: suspend () -> Unit, call: suspend (device: String) -> Boolean) {
        scope.launch {
            val device = deviceId() ?: return@launch
            val accepted = runCatching { call(device) }.getOrDefault(false)
            if (accepted) withContext(mainDispatcher) { onAccepted() }
        }
    }
}
