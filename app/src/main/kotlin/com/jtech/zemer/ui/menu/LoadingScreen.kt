package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R

@Composable
fun LoadingScreen(
    isVisible: Boolean,
    value: Int,
) {
    if (isVisible) {
        Dialog (
            onDismissRequest = {}
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = stringResource(R.string.progress_percent, value),
                    color = Color.White,
                    fontSize = 26.sp,
                )

            }
        }
    }
}
