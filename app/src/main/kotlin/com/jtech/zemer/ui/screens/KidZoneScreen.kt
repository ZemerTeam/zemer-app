package com.jtech.zemer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.ArtistViewTypeKey
import com.jtech.zemer.constants.CONTENT_TYPE_ARTIST
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.WhitelistedArtistGridItem
import com.jtech.zemer.ui.component.WhitelistedArtistListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.KidZoneViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KidZoneScreen(
    navController: NavController,
    viewModel: KidZoneViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    LocalHapticFeedback.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstArtistFocus = remember { FocusRequester() }

    val artists by viewModel.allArtists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val searchContent = @Composable {
        val downTarget = if (artists.isNotEmpty()) firstArtistFocus else firstFocus
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier
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
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
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

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.kid_zone),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_artist,
                    artists.size,
                    artists.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier
                    .padding(start = 6.dp)
                    .focusRequester(firstFocus)
                    .focusProperties {
                        up = searchFocus
                        down = if (artists.isNotEmpty()) firstArtistFocus else FocusRequester.Default
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

    when (viewType) {
        LibraryViewType.LIST ->
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(
                    key = "search",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    searchContent()
                }

                item(
                    key = "header",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    headerContent()
                }

                if (artists.isEmpty()) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.kid_zone,
                            text = if (searchQuery.isEmpty()) {
                                stringResource(R.string.kid_zone_empty)
                            } else {
                                stringResource(R.string.no_results_found)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                itemsIndexed(
                    items = artists.distinctBy { it.artist.name },
                    key = { _, item -> item.id },
                    contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                ) { index, artist ->
                    WhitelistedArtistListItem(
                        navController = navController,
                        menuState = menuState,
                        coroutineScope = coroutineScope,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstArtistFocus) else Modifier)
                            .animateItem(),
                        artist = artist,
                        onRequestThumb = { viewModel.requestThumb(artist.id) }
                    )
                }
            }

        LibraryViewType.GRID ->
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Fixed(3),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(
                    key = "search",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    searchContent()
                }

                item(
                    key = "header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    headerContent()
                }

                if (artists.isEmpty()) {
                    item(
                        key = "empty_placeholder",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        EmptyPlaceholder(
                            icon = R.drawable.kid_zone,
                            text = if (searchQuery.isEmpty()) {
                                stringResource(R.string.kid_zone_empty)
                            } else {
                                stringResource(R.string.no_results_found)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                itemsIndexed(
                    items = artists.distinctBy { it.artist.name },
                    key = { _, item -> item.id },
                    contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                ) { index, artist ->
                    WhitelistedArtistGridItem(
                        navController = navController,
                        menuState = menuState,
                        coroutineScope = coroutineScope,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstArtistFocus) else Modifier)
                            .animateItem(),
                        artist = artist,
                        onRequestThumb = { viewModel.requestThumb(artist.id) }
                    )
                }
            }
    }
}
