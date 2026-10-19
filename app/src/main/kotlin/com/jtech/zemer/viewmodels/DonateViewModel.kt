package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.R
import com.jtech.zemer.payments.DonationClient
import com.jtech.zemer.payments.DonationException
import com.jtech.zemer.search.isZemerServerUnreachable
import com.jtech.zemer.utils.reportException
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.confirmPaymentIntent
import com.stripe.android.core.exception.APIConnectionException
import com.stripe.android.core.exception.StripeException
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.payments.paymentlauncher.PaymentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DonateUiState(
    val busy: Boolean = false,
    /** A PaymentIntent client secret whose 3-D Secure the screen hands to Stripe's native UI once. */
    val pendingAction: String? = null,
    val thanked: Boolean = false,
    val failed: Boolean = false,
    /** The server's or Stripe's text for a failure; null falls back to [errorRes]. */
    val errorText: String? = null,
    @StringRes val errorRes: Int = R.string.donate_failed,
)

/**
 * Confirms the donation itself (Stripe API) instead of letting the SDK act on whatever comes back, so
 * a bank that wants a browser redirect fails here with a message: the app never opens a browser. Only
 * a native next action (3-D Secure 2) goes to the screen's PaymentLauncher. The card is a local
 * variable, never UI state.
 */
@HiltViewModel
class DonateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(DonateUiState())
    val state: StateFlow<DonateUiState> = _state.asStateFlow()

    fun donate(cents: Int, email: String, card: PaymentMethodCreateParams) {
        if (_state.value.busy) return
        _state.value = DonateUiState(busy = true)
        viewModelScope.launch {
            _state.value = try {
                val intent = DonationClient.create(cents, email)
                PaymentConfiguration.init(context, intent.publishableKey)
                val confirmed = Stripe(context, intent.publishableKey).confirmPaymentIntent(
                    ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(card, intent.clientSecret)
                        .withShouldUseStripeSdk(true),
                )
                confirmedState(
                    confirmed.status,
                    needsBrowser = confirmed.nextActionData is StripeIntent.NextActionData.RedirectToUrl,
                    clientSecret = intent.clientSecret,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldReport(e)) reportException(e, "DonateViewModel")
                failureState(e)
            }
        }
    }

    fun presented() = _state.update { it.copy(pendingAction = null) }

    /** Stripe's 3-D Secure UI could not start; never leave the screen busy. */
    fun presentFailed(e: Exception) {
        reportException(e, "DonateViewModel.present")
        _state.value = DonateUiState(failed = true)
    }

    fun onPaymentResult(result: PaymentResult) {
        _state.value = resultState(result)
    }

    internal companion object {
        fun confirmedState(
            status: StripeIntent.Status?,
            needsBrowser: Boolean,
            clientSecret: String,
        ): DonateUiState = when {
            status == StripeIntent.Status.Succeeded -> DonateUiState(thanked = true)
            needsBrowser ->
                DonateUiState(failed = true, errorRes = R.string.donate_needs_browser)
            status == StripeIntent.Status.RequiresAction -> DonateUiState(busy = true, pendingAction = clientSecret)
            else -> DonateUiState(failed = true)
        }

        fun failureState(e: Exception): DonateUiState = when {
            e is DonationException -> DonateUiState(failed = true, errorText = e.message)
            e is APIConnectionException || e.isZemerServerUnreachable() -> DonateUiState(failed = true)
            // A decline or invalid card: Stripe's message is written for the cardholder.
            e is StripeException -> DonateUiState(failed = true, errorText = e.localizedMessage)
            else -> DonateUiState(failed = true)
        }

        /** Declines and connectivity are normal outcomes, not bugs. */
        fun shouldReport(e: Exception): Boolean =
            e !is DonationException && e !is StripeException && !e.isZemerServerUnreachable()

        fun resultState(result: PaymentResult): DonateUiState = when (result) {
            is PaymentResult.Completed -> DonateUiState(thanked = true)
            is PaymentResult.Canceled -> DonateUiState()
            is PaymentResult.Failed -> DonateUiState(failed = true, errorText = result.throwable.localizedMessage)
        }
    }
}
