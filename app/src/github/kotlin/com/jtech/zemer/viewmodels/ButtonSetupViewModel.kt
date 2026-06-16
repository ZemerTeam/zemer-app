package com.jtech.zemer.viewmodels

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.models.DpadDirection
import com.jtech.zemer.utils.ButtonInputCapture
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ButtonSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val skipKeyCodes = setOf(
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_POWER
    )

    private val _uiState = MutableStateFlow(ButtonSetupUiState())
    val uiState: StateFlow<ButtonSetupUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null
    private var currentIndex = 0

    init {
        viewModelScope.launch {
            context.dataStore.data
                .map { prefs -> DpadDirection.entries.associateWith { prefs[it.prefKey] } }
                .collect { assignments ->
                    _uiState.update { it.copy(assignments = assignments) }
                }
        }
    }

    fun startSetup() {
        currentIndex = 0
        ButtonInputCapture.beginCapture()
        startPriming()
    }

    fun cancelSetup() {
        captureJob?.cancel()
        captureJob = null
        ButtonInputCapture.endCapture()
        _uiState.update { it.copy(step = ButtonSetupStep.Idle) }
    }

    fun finishAndReset() {
        captureJob?.cancel()
        captureJob = null
        ButtonInputCapture.endCapture()
        _uiState.update { it.copy(step = ButtonSetupStep.Idle) }
    }

    fun skipCurrentDirection() {
        when (val step = _uiState.value.step) {
            is ButtonSetupStep.Priming -> startListening(true)
            is ButtonSetupStep.Awaiting -> {
                viewModelScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs.remove(step.direction.prefKey)
                    }
                }
                currentIndex = step.index + 1
                startListening(cancelCurrent = true)
            }
            else -> Unit
        }
    }

    fun clear(direction: DpadDirection) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs.remove(direction.prefKey)
            }
        }
    }

    private fun startListening(cancelCurrent: Boolean) {
        if (cancelCurrent) {
            captureJob?.cancel()
        }
        if (currentIndex == -1) {
            _uiState.update { it.copy(step = ButtonSetupStep.Priming) }
            captureJob?.cancel()
            captureJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                ButtonInputCapture.events.first()
                currentIndex = 0
                startListening(cancelCurrent = false)
            }
            return
        }
        val steps = DpadDirection.wizardOrder
        if (currentIndex >= steps.size) {
            captureJob = null
            ButtonInputCapture.endCapture()
            _uiState.update { it.copy(step = ButtonSetupStep.Completed) }
            return
        }
        val direction = steps[currentIndex]
        _uiState.update { it.copy(step = ButtonSetupStep.Awaiting(direction, currentIndex)) }
        captureJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val event = ButtonInputCapture.events
                .filter { it.action == KeyEvent.ACTION_DOWN }
                .filter { direction == DpadDirection.CENTER || it.keyCode !in skipKeyCodes }
                .first()
            saveAssignment(direction, event.keyCode)
            ButtonInputCapture.clear()
            currentIndex += 1
            startListening(cancelCurrent = false)
        }
    }

    private fun startPriming() {
        captureJob?.cancel()
        _uiState.update { it.copy(step = ButtonSetupStep.Priming) }
        captureJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            ButtonInputCapture.events.first()
            ButtonInputCapture.clear()
            startListening(cancelCurrent = false)
        }
    }

    private suspend fun saveAssignment(direction: DpadDirection, keyCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[direction.prefKey] = keyCode
        }
    }
}

data class ButtonSetupUiState(
    val assignments: Map<DpadDirection, Int?> = DpadDirection.entries.associateWith { null },
    val step: ButtonSetupStep = ButtonSetupStep.Idle,
)

sealed interface ButtonSetupStep {
    data object Idle : ButtonSetupStep
    data class Awaiting(val direction: DpadDirection, val index: Int) : ButtonSetupStep
    data object Priming : ButtonSetupStep
    data object Completed : ButtonSetupStep
}
