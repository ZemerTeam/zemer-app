package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.LibraryViewType

/**
 * Search field shared by the KidZone / Whitelisted-Artists / Whitelisted-Podcasts browse screens and the
 * Music Status See-all: a filled theme pill (`surfaceContainerHigh`) with a muted leading search icon and
 * a trailing clear icon (shown only while non-empty). No border - the fill defines it; the blinking
 * accent cursor marks focus. Optional D-pad down-focus handoff to [downTarget] (via [focusProperties] +
 * the DirectionDown key preview). [placeholderRes] labels it per caller. Every color is a theme token -
 * nothing hardcoded.
 *
 * Built on [BasicTextField] + a decoration box with ZERO vertical content padding, so it sits at a
 * compact 48dp WITHOUT clipping the text (the stock OutlinedTextField padding needs ~56dp and clips when
 * forced shorter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    searchFocus: FocusRequester,
    downTarget: FocusRequester? = null,
    modifier: Modifier = Modifier,
    placeholderRes: Int = R.string.search_artists,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .focusRequester(searchFocus)
            .then(
                if (downTarget != null) {
                    Modifier
                        .focusProperties { down = downTarget }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                                downTarget.requestFocus()
                            }
                            false
                        }
                } else {
                    Modifier
                }
            ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(accent),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text(stringResource(placeholderRes)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                shape = shape,
                // The fill is the Modifier.clip/background above; keep the box transparent (no double
                // fill, no indicator) and drop the padding so the content fits the 48dp height.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = accent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 4.dp),
                container = {},
            )
        },
    )
}

/**
 * Title + item count + list/grid view-toggle header shared by the KidZone / Whitelisted-Artists /
 * Whitelisted-Podcasts browse screens. The title uses the shared [AppBarTitle] so it matches the app-bar
 * back+title and Home-row titles. [countPluralRes] labels the count per caller (artists vs channels).
 */
@Composable
fun ArtistCountHeader(
    titleRes: Int,
    count: Int,
    viewType: LibraryViewType,
    onToggleViewType: () -> Unit,
    firstFocus: FocusRequester,
    searchFocus: FocusRequester,
    downTarget: FocusRequester,
    modifier: Modifier = Modifier,
    countPluralRes: Int = R.plurals.n_artist,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AppBarTitle(stringResource(titleRes))

        Spacer(Modifier.weight(1f))

        Text(
            text = pluralStringResource(
                countPluralRes,
                count,
                count
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
