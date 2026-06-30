package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.IconButton as ZemerIconButton
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.ArtistBlacklistManager
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistBlacklistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var blacklistItems by remember { mutableStateOf(ArtistBlacklistManager.getBlacklist().toList()) }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint_artist)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(firstFocus),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                isSearching = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val results = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_ARTIST)
                                        .getOrNull()?.items?.filterIsInstance<ArtistItem>() ?: emptyList()
                                    withContext(Dispatchers.Main) {
                                        searchResults = results
                                        isSearching = false
                                    }
                                }
                            }
                        }) {
                            Icon(painterResource(R.drawable.search), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
            )

            if (searchResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                searchResults.forEach { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch(Dispatchers.IO) {
                                    ArtistBlacklistManager.addToBlacklist(
                                        context, artist.id, artist.title
                                    )
                                }
                                blacklistItems = ArtistBlacklistManager.getBlacklist().toList()
                                searchResults = emptyList()
                                searchQuery = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = artist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.block_artist),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    HorizontalDivider()
                }
            }

            if (blacklistItems.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.blocked_artists),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                blacklistItems.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.artistName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                ArtistBlacklistManager.removeFromBlacklist(context, entry.artistId)
                            }
                            blacklistItems = ArtistBlacklistManager.getBlacklist().toList()
                        }) {
                            Text(
                                stringResource(R.string.unblock_artist),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            } else if (searchResults.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_blocked_artists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.artist_blacklist)) },
        navigationIcon = {
            ZemerIconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}
