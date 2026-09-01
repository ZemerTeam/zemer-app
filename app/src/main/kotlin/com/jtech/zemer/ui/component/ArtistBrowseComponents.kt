package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TonalToggleButton
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
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
    val shape = SearchPillShape
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .height(SearchPillHeight)
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

        // Material 3 Expressive: a LIST|GRID TonalToggleButton pair (the ChipsRow family), each
        // morphing shape when it becomes the active view — instead of the old single icon button.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 6.dp),
        ) {
            ViewTypeToggle(
                checked = viewType == LibraryViewType.LIST,
                onSelect = { if (viewType != LibraryViewType.LIST) onToggleViewType() },
                iconRes = R.drawable.list,
                modifier = Modifier
                    .focusRequester(firstFocus)
                    .focusProperties {
                        up = searchFocus
                        down = downTarget
                    },
            )
            ViewTypeToggle(
                checked = viewType == LibraryViewType.GRID,
                onSelect = { if (viewType != LibraryViewType.GRID) onToggleViewType() },
                iconRes = R.drawable.grid_view,
                modifier = Modifier.focusProperties {
                    up = searchFocus
                    down = downTarget
                },
            )
        }
    }
}

/** One half of the LIST|GRID view toggle: a shape-morphing tonal toggle with a single icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewTypeToggle(
    checked: Boolean,
    onSelect: () -> Unit,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    TonalToggleButton(
        checked = checked,
        // A tap on the already-active view is a no-op (never an un-check): onSelect only fires
        // the toggle when it switches views.
        onCheckedChange = { onSelect() },
        // Icon-only and COMPACT: zero content padding kills the default text min-width, and the
        // 36dp footprint (the back-to-top button scale) keeps the tonal fill from dominating the
        // header row the way a full 48dp square did.
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(ViewToggleSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ViewToggleIconSize),
        )
    }
}

// The ONE search-pill geometry, shared by [ArtistSearchField] and [SearchHandoffPill] so the two
// (often rendered in the same list) can never drift apart.
private val SearchPillShape = RoundedCornerShape(percent = 50)
private val SearchPillHeight = 48.dp

// The compact LIST|GRID toggle geometry (see [ViewTypeToggle]).
private val ViewToggleSize = 36.dp
private val ViewToggleIconSize = 18.dp

/**
 * A tappable pill in the [ArtistSearchField] family (same shape, fill, height) that HANDS OFF to
 * another search surface - e.g. the Podcasts browse's "Search episodes for 'X'" row jumping to the
 * global search screen. D-pad focusable via the shared focusBorder treatment.
 */
@Composable
fun SearchHandoffPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Sits lower than a plain list row: the pill needs clear air under the content above
            // it (channel matches or the no-results state) so it reads as its own affordance.
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .height(SearchPillHeight)
            .clip(SearchPillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .focusBorder(SearchPillShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
