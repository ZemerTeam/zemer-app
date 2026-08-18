package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R

/**
 * The active library-filter chip (the selected section, e.g. "Artists", with a leading close icon that
 * clears the filter). Material 3 Expressive: a permanently-checked [TonalToggleButton] so it matches the
 * sub-filter toggles in [ChipsRow] instead of the old FilterChip. Shared by every Library*Screen so the
 * five copies can't drift.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFilterChip(
    label: String,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalToggleButton(
        checked = true,
        onCheckedChange = { onDeselect() },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.close),
            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
        Text(label)
    }
}
