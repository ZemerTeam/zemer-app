package com.jtech.zemer.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.DefaultDialog

@Composable
fun LoadingScreen(
    isVisible: Boolean,
    value: Int,
) {
    if (isVisible) {
        DefaultDialog(onDismiss = { /* not dismissable while in progress */ }) {
            Text(
                text = stringResource(R.string.progress_percent, value),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
