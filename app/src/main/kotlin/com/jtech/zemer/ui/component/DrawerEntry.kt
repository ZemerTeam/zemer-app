package com.jtech.zemer.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * One nav-drawer row: the M3 drawer item with the drawer's shared padding, focusable only while the
 * drawer is open ([canFocus], so D-pad focus never lands on a closed drawer), and scrolled into view
 * when focused. Every drawer entry renders through this so their geometry and focus rules can't drift.
 */
@Composable
fun DrawerEntry(
    label: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    selected: Boolean,
    canFocus: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = label,
        icon = icon,
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .focusProperties { this.canFocus = canFocus }
            .bringIntoViewOnFocus(),
    )
}

/** The plain text-and-icon drawer entry. */
@Composable
fun DrawerEntry(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    selected: Boolean,
    canFocus: Boolean,
    onClick: () -> Unit,
) = DrawerEntry(
    label = { Text(stringResource(label)) },
    icon = { Icon(painter = painterResource(icon), contentDescription = null) },
    selected = selected,
    canFocus = canFocus,
    onClick = onClick,
)
