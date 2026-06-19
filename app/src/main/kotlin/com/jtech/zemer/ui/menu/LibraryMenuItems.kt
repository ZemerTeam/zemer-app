package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.Material3MenuItemData

/**
 * THE Add/Remove-from-library menu row, shared by every item/collection menu so the icon and label
 * can't drift between surfaces. `inLibrary` is derived from the single source of truth
 * (`SongEntity.inLibrary != null`); the caller's [onToggle] performs the DB write and gates the remote
 * library-feedback call on a personal account (anonymous = local-only, per the pooled-account rule).
 */
fun libraryMenuItem(
    inLibrary: Boolean,
    onToggle: () -> Unit,
): Material3MenuItemData = Material3MenuItemData(
    icon = {
        Icon(
            painterResource(if (inLibrary) R.drawable.library_add_check else R.drawable.library_add),
            null,
            Modifier.size(24.dp),
        )
    },
    title = {
        Text(stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library))
    },
    onClick = onToggle,
)
