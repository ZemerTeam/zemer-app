package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.recognition.RecognitionAudioCapture
import com.jtech.zemer.recognition.RecognitionResolver
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

/**
 * Drives the "Recognize music" screen.
 *
 * The flow is: record a fingerprint → ask Shazam what the song is → hand the recognized
 * `(title, artist)` to [RecognitionResolver], which searches YouTube Music and returns ONLY a
 * whitelist-confirmed [SongItem] (or nothing). The raw Shazam response is never surfaced, so a song
 * by a non-whitelisted artist can never be shown or played. The widget uses the same resolver.
 */
@HiltViewModel
class RecognizeMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow<RecognizeUiState>(RecognizeUiState.Idle)
    val state = _state.asStateFlow()

    private var job: Job? = null

    /** Starts (or restarts) a recognition attempt. */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                if (!RecognitionAudioCapture.hasRecordPermission(context)) {
                    _state.value = RecognizeUiState.PermissionRequired
                    return@launch
                }

                _state.value = RecognizeUiState.Listening
                val fingerprint = RecognitionAudioCapture.capture(context)

                _state.value = RecognizeUiState.Identifying
                val recognition = when (
                    val outcome = Shazam.recognize(fingerprint.signature, fingerprint.sampleDurationMs)
                ) {
                    is Shazam.Outcome.Found -> outcome.result
                    Shazam.Outcome.NoMatch -> {
                        _state.value = RecognizeUiState.NoMatch
                        return@launch
                    }
                    is Shazam.Outcome.Failed -> {
                        Timber.tag(TAG).w(outcome.error, "Shazam recognition failed")
                        _state.value = RecognizeUiState.Error
                        return@launch
                    }
                }

                _state.value = RecognizeUiState.Searching
                _state.value = when (
                    val outcome = RecognitionResolver.resolveWhitelisted(database, recognition.title, recognition.artist)
                ) {
                    is RecognitionResolver.Outcome.Resolved -> RecognizeUiState.Result(outcome.song)
                    RecognitionResolver.Outcome.NoMatch -> RecognizeUiState.NoMatch
                    RecognitionResolver.Outcome.Error -> RecognizeUiState.Error
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
