package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import com.jtech.zemer.sync.ContentReportRepository
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Backs [com.jtech.zemer.ui.menu.ReportContentDialog]: a thin DI seam over the report repository.
 * Submission state (in-flight) is owned by the dialog so it is per-dialog and resets on dismissal;
 * the call runs in the dialog's own coroutine scope, which cancels when the dialog leaves composition.
 */
@HiltViewModel
class ReportContentViewModel @Inject constructor(
    private val reportRepository: ContentReportRepository,
) : ViewModel() {

    /**
     * Submits the report; returns true on success, false on failure. Rethrows
     * [CancellationException] so the caller's structured-concurrency cancellation is preserved.
     */
    suspend fun submit(
        subject: Map<String, Any?>,
        reason: String,
        comment: String,
    ): Boolean = try {
        reportRepository.submitReport(subject, reason, comment)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportException(e, "ReportContentViewModel")
        false
    }
}
