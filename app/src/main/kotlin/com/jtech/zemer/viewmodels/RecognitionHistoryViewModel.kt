package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import com.jtech.zemer.recognition.RecognitionHistoryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecognitionHistoryViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    /**
     * History, filtered against the *current* whitelist. The whitelist is mutable, so an entry whose
     * artists were whitelisted at recognition time but have since been removed is dropped here —
     * which is the only place history is exposed, so a de-whitelisted song can never be replayed.
     * Combining with the whitelist flow makes this re-evaluate the moment the whitelist changes.
     */
    val history = combine(
        database.recognitionHistory(),
        database.getAllWhitelistedArtistIds(),
    ) { entries, whitelistedIds ->
        val whitelisted = whitelistedIds.toHashSet()
        entries.filter { RecognitionHistoryFilter.isAllowed(it.artistIds, whitelisted) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entity: RecognitionHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) { database.deleteRecognitionHistory(entity) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { database.clearRecognitionHistory() }
    }
}
