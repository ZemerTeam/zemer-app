package com.jtech.zemer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.extensions.openUrlOrCopy
import com.jtech.zemer.net.UnofficialCopy
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingStepTitle

/**
 * Replaces the whole app UI once the servers have refused this copy ([UnofficialCopy]): the server's [message] (the
 * built-in text when blank), the site address as selectable text (many phones have no browser) and a button that
 * opens it. Centered when it fits, scrollable when a long message or large text does not. It cannot be dismissed;
 * Back leaves the app.
 */
@Composable
fun UnofficialCopyScreen(message: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingStepTitle(text = message.ifBlank { stringResource(R.string.unofficial_copy_message) })
            SelectionContainer {
                Text(
                    text = UnofficialCopy.SITE_URL.substringAfter("://"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OnboardingPrimaryButton(
                text = stringResource(R.string.unofficial_copy_open),
                onClick = { context.openUrlOrCopy(UnofficialCopy.SITE_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
