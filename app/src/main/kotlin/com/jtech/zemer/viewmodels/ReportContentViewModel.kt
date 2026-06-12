package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.sync.ContentReportRepository
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs [com.jtech.zemer.ui.menu.ReportContentDialog]: owns submission state and the network call. */
@HiltViewModel
class ReportContentViewModel @Inject constructor(
    private val reportRepository: ContentReportRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    fun submit(
        subject: Map<String, Any?>,
        reason: String,
        comment: String,
        onResult: (success: Boolean) -> Unit,
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                reportRepository.submitReport(subject, reason, comment)
                onResult(true)
            } catch (e: Exception) {
                reportException(e, "ReportContentViewModel")
                onResult(false)
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
