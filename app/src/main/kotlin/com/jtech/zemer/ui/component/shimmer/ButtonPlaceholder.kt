package com.jtech.zemer.ui.component.shimmer

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ButtonPlaceholder(modifier: Modifier = Modifier) {
    BoxPlaceholder(
        modifier = modifier.height(ButtonDefaults.MinHeight),
        shape = RoundedCornerShape(50),
    )
}
