package com.jtech.zemer.viewmodels

import com.jtech.zemer.R
import com.jtech.zemer.payments.DonationException
import com.stripe.android.core.exception.APIConnectionException
import com.stripe.android.model.StripeIntent
import com.stripe.android.payments.paymentlauncher.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DonateViewModelTest {

    @Test
    fun `a succeeded confirm thanks the donor`() {
        assertEquals(DonateUiState(thanked = true), DonateViewModel.confirmedState(StripeIntent.Status.Succeeded, false, "s"))
    }

    @Test
    fun `a browser redirect fails with its own message and never reaches the launcher`() {
        val state = DonateViewModel.confirmedState(StripeIntent.Status.RequiresAction, needsBrowser = true, clientSecret = "s")
        assertEquals(DonateUiState(failed = true, errorRes = R.string.donate_needs_browser), state)
    }

    @Test
    fun `a native next action goes to the launcher once`() {
        val state = DonateViewModel.confirmedState(StripeIntent.Status.RequiresAction, needsBrowser = false, clientSecret = "pi_secret")
        assertEquals(DonateUiState(busy = true, pendingAction = "pi_secret"), state)
    }

    @Test
    fun `any other status fails with the generic message`() {
        assertEquals(DonateUiState(failed = true), DonateViewModel.confirmedState(StripeIntent.Status.RequiresPaymentMethod, false, "s"))
        assertEquals(DonateUiState(failed = true), DonateViewModel.confirmedState(null, false, "s"))
    }

    @Test
    fun `failures show the server's text, else the generic message`() {
        assertEquals(DonateUiState(failed = true, errorText = "Too many attempts."), DonateViewModel.failureState(DonationException("Too many attempts.")))
        assertEquals(DonateUiState(failed = true), DonateViewModel.failureState(IOException("offline")))
        assertEquals(DonateUiState(failed = true), DonateViewModel.failureState(APIConnectionException("IOException during API request")))
        assertEquals(DonateUiState(failed = true), DonateViewModel.failureState(IllegalStateException("bug")))
    }

    @Test
    fun `only real bugs are reported`() {
        assertFalse(DonateViewModel.shouldReport(DonationException("no")))
        assertFalse(DonateViewModel.shouldReport(IOException("offline")))
        assertFalse(DonateViewModel.shouldReport(APIConnectionException("offline")))
        assertTrue(DonateViewModel.shouldReport(IllegalStateException("bug")))
    }

    @Test
    fun `launcher results map to thanks, reset or failure`() {
        assertEquals(DonateUiState(thanked = true), DonateViewModel.resultState(PaymentResult.Completed))
        assertEquals(DonateUiState(), DonateViewModel.resultState(PaymentResult.Canceled))
        assertEquals(DonateUiState(failed = true, errorText = "Declined"), DonateViewModel.resultState(PaymentResult.Failed(Exception("Declined"))))
    }
}
