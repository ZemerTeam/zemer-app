package com.jtech.zemer.ui.screens.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.SuggestionItemHeight
import com.jtech.zemer.search.zemerAlbumRoute
import com.jtech.zemer.search.zemerPlaylistRoute
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.ui.screens.Screens
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.SearchBarIconOffsetX
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.menu.ytItemMenu
import com.jtech.zemer.viewmodels.OnlineSearchSuggestionViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    firstResultFocusRequester: FocusRequester = remember { FocusRequester() },
    searchFocusRequester: FocusRequester? = null,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsState()
    // The dropdown follows the active engine (see OnlineSearchSuggestionViewModel), so a Zemer playlist
    // shown here must open through the server path — route on the same provider preference.

    val lazyListState = rememberLazyListState()
    val firstItemKey = remember(viewState) {
        when {
            viewState.history.firstOrNull() != null -> "history_${viewState.history.firstOrNull()?.query}"
            viewState.suggestions.firstOrNull() != null -> "suggestion_${viewState.suggestions.firstOrNull()}"
            viewState.items.firstOrNull() != null -> "item_${viewState.items.firstOrNull()?.id}"
            else -> null
        }
    }

    // Hide the keyboard only when the USER scrolls the list — gating on a raw scroll-offset change
    // instead fires on programmatic content updates too (the as-you-type list re-lays-out as results
    // stream in), which yanked the keyboard down mid-typing.
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }
            .filter { it }
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce(300L).collectLatest {
            viewModel.query.value = it
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // Before the user types, offer a shortcut into the whitelisted-artists browse (the Artists tab
        // was removed from the bottom bar and lives here now). Hidden once a query exists so it never
        // sits among live suggestions/results.
        if (query.isBlank()) {
            item(key = "browse_artists") {
                BrowseArtistsItem(
                    onClick = {
                        navController.navigate(Screens.Artists.route)
                        onDismiss()
                    },
                    pureBlack = pureBlack,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(viewState.history, key = { "history_${it.query}" }) { history ->
            SuggestionItem(
                query = history.query,
                online = false,
                onClick = {
                    onSearch(history.query)
                    onDismiss()
                },
                onDelete = {
                    database.query {
                        delete(history)
                    }
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                },
                modifier = Modifier
                    .then(if (firstItemKey == "history_${history.query}") Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                    .focusProperties {
                        if (searchFocusRequester != null) {
                            up = searchFocusRequester
                        }
                        down = FocusRequester.Default
                    }
                    .animateItem(),
                pureBlack = pureBlack
            )
        }

        items(viewState.suggestions, key = { "suggestion_$it" }) { query ->
            SuggestionItem(
                query = query,
                online = true,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(query, TextRange(query.length)))
                },
                modifier = Modifier
                    .then(if (firstItemKey == "suggestion_$query") Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                    .focusProperties {
                        if (searchFocusRequester != null) {
                            up = searchFocusRequester
                        }
                        down = FocusRequester.Default
                    }
                    .animateItem(),
                pureBlack = pureBlack
            )
        }

        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item(key = "search_divider") {
                HorizontalDivider(
                    modifier = Modifier.animateItem()
                )
            }
        }

        items(viewState.items, key = { "item_${it.id}" }) { item ->
            YouTubeListItem(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                trailingContent = {
                    MoreVertMenuButton(
                        onClick = {
                            menuState.show(
                                ytItemMenu(
                                    item = item,
                                    navController = navController,
                                    coroutineScope = scope,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            )
                        }
                    )
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (activeRowTapTogglesPlayPause(item.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                        playerConnection.playPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ZemerRadioQueue.song(item.toMediaMetadata(), playerConnection.service)
                                        )
                                        onDismiss()
                                    }
                                }
                                is AlbumItem -> {
                                    navController.navigate(zemerAlbumRoute(item))
                                    onDismiss()
                                }
                                is ArtistItem -> {
                                    navController.navigateToArtist(item.id)
                                    onDismiss()
                                }
                                is PlaylistItem -> {
                                    navController.navigate(zemerPlaylistRoute(item.id))
                                    onDismiss()
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show(
                                ytItemMenu(
                                    item = item,
                                    navController = navController,
                                    coroutineScope = coroutineScope,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            )
                        }
                    )
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                    .then(if (firstItemKey == "item_${item.id}") Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                    .focusProperties {
                        if (searchFocusRequester != null) {
                            up = searchFocusRequester
                        }
                        down = FocusRequester.Default
                    }
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter || event.key == Key.DirectionCenter) {
                            when (item) {
                                is SongItem -> {
                                    if (activeRowTapTogglesPlayPause(item.id == mediaMetadata?.id, playerConnection.isStationBroadcast.value)) {
                                        playerConnection.playPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ZemerRadioQueue.song(item.toMediaMetadata(), playerConnection.service)
                                        )
                                        onDismiss()
                                    }
                                }
                                is AlbumItem -> {
                                    navController.navigate(zemerAlbumRoute(item))
                                    onDismiss()
                                }
                                is ArtistItem -> {
                                    navController.navigateToArtist(item.id)
                                    onDismiss()
                                }
                                is PlaylistItem -> {
                                    navController.navigate(zemerPlaylistRoute(item.id))
                                    onDismiss()
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    .animateItem()
            )
        }
    }
}

/**
 * A "Browse all artists" shortcut styled to match [SuggestionItem] (same height, focus border, D-pad
 * focusability). Shown at the top of the pre-typing search list; opens the whitelisted-artists screen.
 */
@Composable
fun BrowseArtistsItem(
    onClick: () -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val isFocused = focusState?.isFocused ?: false

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            else -> if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
        },
        label = "browse_artists_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        label = "browse_artists_focus_border"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.5f,
        label = "browse_artists_icon_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(SuggestionItemHeight)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(end = SearchBarIconOffsetX)
            .background(backgroundColor)
            .border(width = 2.dp, color = borderColor)
            .clickable(onClick = onClick)
            .onFocusChanged { focusState = it }
            .onKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.DirectionCenter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        Icon(
            painterResource(R.drawable.artist),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(iconAlpha)
        )

        Text(
            text = stringResource(R.string.browse_artists),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(iconAlpha)
        )
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean
) {
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val isFocused = focusState?.isFocused ?: false

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            else -> if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
        },
        label = "suggestion_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        label = "suggestion_focus_border"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.5f,
        label = "suggestion_icon_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(SuggestionItemHeight)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(end = SearchBarIconOffsetX)
            .background(backgroundColor)
            .border(width = 2.dp, color = borderColor)
            .clickable(onClick = onClick)
            .onFocusChanged { focusState = it }
            .onKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.DirectionCenter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        Icon(
            painterResource(if (online) R.drawable.search else R.drawable.history),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(iconAlpha)
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(iconAlpha),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(iconAlpha),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
            )
        }
    }
}
