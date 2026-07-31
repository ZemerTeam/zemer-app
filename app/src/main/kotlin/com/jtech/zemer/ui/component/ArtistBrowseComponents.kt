package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.LibraryViewType

/**
 * Search field shared by the KidZone and Whitelisted-Artists browse screens: leading search icon,
 * trailing clear icon (shown only while non-empty), D-pad down-focus handoff to [downTarget] both
 * via [focusProperties] and the DirectionDown key preview.
 */
@Composable
fun ArtistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    searchFocus: FocusRequester,
    downTarget: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(searchFocus)
            .focusProperties {
                down = downTarget
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                    downTarget.requestFocus()
                    false
                } else {
                    false
                }
            },
        placeholder = { Text(stringResource(R.string.search_artists)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Title + artist count + list/grid view-toggle header shared by the KidZone and
 * Whitelisted-Artists browse screens.
 */
@Composable
fun ArtistCountHeader(
    titleRes: Int,
    artistCount: Int,
    viewType: LibraryViewType,
    onToggleViewType: () -> Unit,
    firstFocus: FocusRequester,
    searchFocus: FocusRequester,
    downTarget: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = pluralStringResource(
                R.plurals.n_artist,
                artistCount,
                artistCount
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        IconButton(
            onClick = onToggleViewType,
            modifier = Modifier
                .padding(start = 6.dp)
                .focusRequester(firstFocus)
                .focusProperties {
                    up = searchFocus
                    down = downTarget
                },
        ) {
            Icon(
                painter =
                painterResource(
                    when (viewType) {
                        LibraryViewType.LIST -> R.drawable.list
                        LibraryViewType.GRID -> R.drawable.grid_view
                    },
                ),
                contentDescription = null,
            )
        }
    }
}
