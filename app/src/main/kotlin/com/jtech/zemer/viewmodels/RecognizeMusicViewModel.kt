package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.recognition.RecognitionAudioCapture
import com.jtech.zemer.recognition.RecognitionResolver
import com.jtech.zemer.recognition.acrcloud.Acrcloud
import com.jtech.zemer.recognition.shazam.Shazam
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class RecognitionMode {
    SHAZAM,
    HUMMING,
}

/**
 * Drives the "Recognize music" screen.
 *
 * Supports two recognition modes:
 * - [RecognitionMode.SHAZAM]: captures an audio fingerprint and sends it to the Shazam API.
 * - [RecognitionMode.HUMMING]: records raw audio and sends it to ACRCloud for humming recognition.
 *
 * Both paths hand the recognized `(title, artist)` to [RecognitionResolver], which searches
 * YouTube Music and returns ONLY a whitelist-confirmed [SongItem] (or nothing). The raw
 * recognition response is never surfaced, so a song by a non-whitelisted artist can never be
 * shown or played.
 */
@HiltViewModel
class RecognizeMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow<RecognizeUiState>(RecognizeUiState.Idle)
    val state = _state.asStateFlow()

    private val _mode = MutableStateFlow(RecognitionMode.SHAZAM)
    val mode = _mode.asStateFlow()

    private var job: Job? = null

    /** Starts (or restarts) a recognition attempt in the current mode. */
    fun start() {
        start(_mode.value)
    }

    /** Starts (or restarts) a recognition attempt in the given mode. */
    fun start(mode: RecognitionMode) {
        _mode.value = mode
        job?.cancel()
        job = viewModelScope.launch {
            try {
                if (!RecognitionAudioCapture.hasRecordPermission(context)) {
                    _state.value = RecognizeUiState.PermissionRequired
                    return@launch
                }

                _state.value = RecognizeUiState.Listening

                when (mode) {
                    RecognitionMode.SHAZAM -> runShazamFlow()
                    RecognitionMode.HUMMING -> runHummingFlow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Recognition failed")
                reportException(e)
                _state.value = RecognizeUiState.Error
            }
        }
    }

    private suspend fun runShazamFlow() {
        val fingerprint = RecognitionAudioCapture.capture(context)

        _state.value = RecognizeUiState.Identifying
        val recognition = when (
            val outcome = Shazam.recognize(fingerprint.signature, fingerprint.sampleDurationMs)
        ) {
            is Shazam.Outcome.Found -> outcome.result
            Shazam.Outcome.NoMatch -> {
                _state.value = RecognizeUiState.NoMatch
                return
            }
            is Shazam.Outcome.Failed -> {
                Timber.tag(TAG).w(outcome.error, "Shazam recognition failed")
                _state.value = RecognizeUiState.Error
                return
            }
        }

        resolveAndPresent(recognition.title, recognition.artist)
    }

    private suspend fun runHummingFlow() {
        val wavData = RecognitionAudioCapture.captureWav(context)

        _state.value = RecognizeUiState.Identifying
        val result = when (
            val outcome = Acrcloud.recognize(wavData)
        ) {
            is Acrcloud.Outcome.Found -> outcome.result
            Acrcloud.Outcome.NoMatch -> {
                _state.value = RecognizeUiState.NoMatch
                return
            }
            is Acrcloud.Outcome.Failed -> {
                Timber.tag(TAG).w(outcome.error, "ACRCloud recognition failed")
                _state.value = RecognizeUiState.Error
                return
            }
        }

        resolveAndPresent(result.title, result.artist)
    }

    private suspend fun resolveAndPresent(title: String, artist: String) {
        _state.value = RecognizeUiState.Searching
        _state.value = when (
            val outcome = RecognitionResolver.resolveWhitelisted(database, title, artist)
        ) {
            is RecognitionResolver.Outcome.Resolved -> RecognizeUiState.Result(outcome.song)
            RecognitionResolver.Outcome.NoMatch -> RecognizeUiState.NoMatch
            RecognitionResolver.Outcome.Error -> RecognizeUiState.Error
        }
    }

    /** Cancels any in-flight attempt and returns to the idle state. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = RecognizeUiState.Idle
    }

    companion object {
        private const val TAG = "RecognizeMusicVM"
    }
}

/** UI state for the recognition screen. [Result] is the only state that carries content, and that
 * content is always a whitelist-confirmed [SongItem]. */
sealed interface RecognizeUiState {
    data object Idle : RecognizeUiState
    data object PermissionRequired : RecognizeUiState
    data object Listening : RecognizeUiState
    data object Identifying : RecognizeUiState
    data object Searching : RecognizeUiState
    data class Result(val song: SongItem) : RecognizeUiState
    data object NoMatch : RecognizeUiState
    data object Error : RecognizeUiState
}
