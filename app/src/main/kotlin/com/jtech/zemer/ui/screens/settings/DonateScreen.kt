package com.jtech.zemer.ui.screens.settings

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.payments.DonationClient
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.ChipsRowTopPadding
import com.jtech.zemer.ui.component.InfoLabel
import com.jtech.zemer.ui.component.InlineErrorPanel
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.viewmodels.DonateViewModel
import com.stripe.android.payments.paymentlauncher.rememberPaymentLauncher
import com.stripe.android.view.CardMultilineWidget

/**
 * One-time donation, paid inside the app: Stripe's card field, confirmed by [DonateViewModel], with
 * PaymentLauncher only for native 3-D Secure (not the much larger PaymentSheet). No browser may ever
 * open: the PaymentIntent is card only, and a bank that wants a browser check fails with a message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: DonateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var amountText by rememberSaveable { mutableStateOf(DonationClient.PRESET_DOLLARS[1].toString()) }
    var email by rememberSaveable { mutableStateOf("") }
    val cents = DonationClient.cents(amountText)

    // Stripe's own card field: the card goes only to Stripe, never to Zemer's servers. Created once
    // (AndroidView keeps its first view), so a later theme change keeps the typed card.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardWidget = remember {
        val theme = if (darkTheme) {
            com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar
        } else {
            com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
        }
        CardMultilineWidget(ContextThemeWrapper(context, theme)).apply {
            setShouldShowPostalCode(true)
            // Shown but optional: many donors' cards (Israel and others) have no postal code.
            postalCodeRequired = false
        }
    }
    // Only for a native 3-D Secure step; reads the publishable key the VM put in PaymentConfiguration.
    val paymentLauncher = rememberPaymentLauncher(viewModel::onPaymentResult)
    LaunchedEffect(state.pendingAction) {
        val clientSecret = state.pendingAction ?: return@LaunchedEffect
        try {
            paymentLauncher.handleNextActionForPaymentIntent(clientSecret)
            viewModel.presented()
        } catch (e: Exception) {
            viewModel.presentFailed(e)
        }
    }

    if (state.thanked) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .padding(16.dp),
        ) {
            AppStateView(
                title = stringResource(R.string.donate_thanks_title),
                subtitle = stringResource(R.string.donate_thanks),
                icon = R.drawable.favorite,
                actionLabel = stringResource(R.string.donate_done),
                onAction = { navController.navigateUp() },
            )
        }
    } else Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        // Keyed by cents, so a typed "18.00" still highlights the $18 chip.
        ChipsRow(
            chips = DonationClient.PRESET_DOLLARS.map { it * 100 to stringResource(R.string.donate_preset, it) },
            currentValue = cents,
            onValueUpdate = { preset -> preset?.let { amountText = (it / 100).toString() } },
            modifier = Modifier.padding(vertical = ChipsRowTopPadding),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.donate_amount)) },
                prefix = { Text(stringResource(R.string.donate_currency_symbol)) },
                singleLine = true,
                isError = amountText.isNotBlank() && cents == null,
                supportingText = {
                    Text(stringResource(R.string.donate_amount_range, DonationClient.MIN_CENTS / 100, DonationClient.MAX_CENTS / 100))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.donate_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            AndroidView(factory = { cardWidget }, modifier = Modifier.fillMaxWidth())
            OnboardingPrimaryButton(
                text = cents?.let { stringResource(R.string.donate_button, DonationClient.amountParam(it)) }
                    ?: stringResource(R.string.donate),
                onClick = {
                    // Null means the card is incomplete; the widget marks the bad fields itself.
                    val card = cardWidget.paymentMethodCreateParams
                    if (card != null) cents?.let { viewModel.donate(it, email, card) }
                },
                enabled = cents != null,
                loading = state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.failed) InlineErrorPanel(state.errorText ?: stringResource(state.errorRes))
            InfoLabel(stringResource(R.string.donate_stripe_note))
        }
    }

    BackTopAppBar(
        title = { AppBarTitle(stringResource(R.string.donate)) },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}
