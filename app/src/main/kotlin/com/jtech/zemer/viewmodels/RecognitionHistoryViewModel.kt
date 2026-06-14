package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecognitionHistoryViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    val history = database.recognitionHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entity: RecognitionHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) { database.deleteRecognitionHistory(entity) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { database.clearRecognitionHistory() }
    }
}
