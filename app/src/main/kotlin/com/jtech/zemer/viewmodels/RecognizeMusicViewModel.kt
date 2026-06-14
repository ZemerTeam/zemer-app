package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.recognition.RecognitionAudioCapture
import com.jtech.zemer.recognition.RecognitionMatchSelector
import com.jtech.zemer.recognition.shazam.Shazam
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the "Recognize music" screen.
 *
 * The flow is: record a fingerprint → ask Shazam what the song is → use ONLY the recognized
 * `(title, artist)` as a query into YouTube Music search → run the results through the same
 * [filterWhitelisted] used everywhere else → pick the best match. The only thing ever exposed to the
 * UI is a whitelist-filtered [SongItem]; the raw Shazam response is never surfaced, so a recognized
 * song by a non-whitelisted artist can never be shown or played.
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
                val recognition = Shazam.recognize(fingerprint.signature, fingerprint.sampleDurationMs)
                    .getOrElse { error ->
                        _state.value = errorStateFor(error)
                        return@launch
                    }

                _state.value = RecognizeUiState.Searching
                _state.value = resolveWhitelisted(recognition.title, recognition.artist)
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

    /**
     * The whitelist bridge: search YouTube Music for the recognized song, keep only whitelisted
     * results, and pick the best match. Returns a [RecognizeUiState.Result] holding a filtered
     * [SongItem], or [RecognizeUiState.NoMatch] when nothing whitelisted corresponds.
     */
    private suspend fun resolveWhitelisted(title: String, artist: String): RecognizeUiState =
        withContext(Dispatchers.IO) {
            val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ").trim()
            if (query.isBlank()) return@withContext RecognizeUiState.NoMatch

            val searchResult = YouTube.search(query, SearchFilter.FILTER_SONG).getOrElse { error ->
                Timber.tag(TAG).w(error, "YouTube search failed for recognized track")
                return@withContext RecognizeUiState.Error
            }

            val candidates = searchResult.items
                .filterWhitelisted(database)
                .filterIsInstance<SongItem>()

            val match = RecognitionMatchSelector.select(title, artist, candidates)
            if (match != null) RecognizeUiState.Result(match) else RecognizeUiState.NoMatch
        }

    private fun errorStateFor(error: Throwable): RecognizeUiState {
        val message = error.message.orEmpty()
        return if (message.contains("No match", ignoreCase = true)) {
            RecognizeUiState.NoMatch
        } else {
            Timber.tag(TAG).w(error, "Shazam recognition failed")
            RecognizeUiState.Error
        }
    }

    companion object {
        private const val TAG = "RecognizeMusicVM"
    }
}

/** UI state for the recognition screen. [Result] is the only state that carries content, and that
 * content is always a whitelist-filtered [SongItem]. */
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
