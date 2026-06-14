package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThumbnailMenu(
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.insert_photo), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.choose_from_library)) },
                            onClick = {
                                onEdit()
                                onDismiss()
                            },
                        )
                    )
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.delete), null, Modifier.size(24.dp)) },
                            title = { Text(stringResource(R.string.remove_custom_image)) },
                            onClick = {
                                onRemove()
                                onDismiss()
                            },
                        )
                    )
                },
            )
        }
    }
}
