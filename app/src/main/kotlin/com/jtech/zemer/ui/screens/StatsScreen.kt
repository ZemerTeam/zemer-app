package com.jtech.zemer.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.MyTopFilter
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.stats.ListeningStats
import com.jtech.zemer.db.entities.SongPlayStats
import com.jtech.zemer.stats.StatsSummary
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.ArtistListItem
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.ChartRankCell
import com.jtech.zemer.ui.component.ChartRankMetrics
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.ChipsRowBottomPadding
import com.jtech.zemer.ui.component.ChipsRowTopPadding
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.HideOnScrollFAB
import com.jtech.zemer.ui.component.IconCategoryCard
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.ZemerLoadingSection
import com.jtech.zemer.ui.component.rememberChartRankMetrics
import com.jtech.zemer.ui.component.runtimeLabel
import com.jtech.zemer.ui.menu.ArtistMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.utils.bidiIsolate
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.viewmodels.StatsViewModel
import java.text.NumberFormat

private enum class StatsCategory { SONGS, ARTISTS }

/** The periods offered, shortest first; the labels are the My top playlist's. */
private val STATS_PERIODS = listOf(
    MyTopFilter.DAY, MyTopFilter.WEEK, MyTopFilter.MONTH, MyTopFilter.YEAR, MyTopFilter.ALL_TIME,
)

/**
 * Local listening stats: a period selector, the period's headline numbers, then the ranked songs
 * or artists. Built entirely from shared pieces (ChipsRow, IconCategoryCard, the library
 * rows and the chart rank column) so it reads like the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val period by viewModel.period.collectAsState()
    val stats by viewModel.stats.collectAsState()
    var category by rememberSaveable { mutableStateOf(StatsCategory.SONGS) }
    val lazyListState = rememberLazyListState()
    val title = stringResource(R.string.stats)
    val rankMetrics = rememberRankMetrics(
        if (category == StatsCategory.SONGS) stats?.songs?.size ?: 0 else stats?.artists?.size ?: 0,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "period") {
                ChipsRow(
                    chips = STATS_PERIODS.map { it to stringResource(it.labelRes) },
                    currentValue = period,
                    onValueUpdate = { viewModel.period.value = it },
                    modifier = Modifier.padding(top = ChipsRowTopPadding, bottom = ChipsRowBottomPadding),
                )
            }

            val current = stats
            when {
                current == null -> item(key = "loading") { ZemerLoadingSection() }

                current.summary.plays == 0 -> item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.stats,
                        text = stringResource(R.string.stats_empty),
                        modifier = Modifier.animateItem(),
                    )
                }

                else -> {
                    item(key = "summary") { StatsSummaryTiles(current.summary) }
                    item(key = "category") {
                        ChipsRow(
                            chips = listOf(
                                StatsCategory.SONGS to stringResource(R.string.filter_songs),
                                StatsCategory.ARTISTS to stringResource(R.string.filter_artists),
                            ),
                            currentValue = category,
                            onValueUpdate = { category = it },
                            modifier = Modifier.padding(top = ChipsRowTopPadding, bottom = ChipsRowBottomPadding),
                        )
                    }
                    rankedItems(
                        stats = current,
                        category = category,
                        navController = navController,
                        title = title,
                        currentMediaId = mediaMetadata?.id,
                        isPlaying = isPlaying,
                        metrics = rankMetrics,
                    )
                }
            }
        }

        val songs = stats?.songs.orEmpty()
        HideOnScrollFAB(
            visible = category == StatsCategory.SONGS && songs.isNotEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(title = context.getString(R.string.stats), items = songs.map { it.song.toMediaItem() }.shuffled()),
                )
            },
        )
    }

    BackTopAppBar(
        title = { AppBarTitle(title) },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSummaryTiles(summary: StatsSummary) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 2,
    ) {
        StatTile(R.drawable.history, durationLabel(summary.timeListenedMs).orEmpty(), stringResource(R.string.listening_time))
        StatTile(R.drawable.equalizer, numbers.format(summary.plays), stringResource(R.string.plays))
        StatTile(R.drawable.genre_zemer_note, numbers.format(summary.songs), stringResource(R.string.songs))
        StatTile(R.drawable.artist, numbers.format(summary.artists), stringResource(R.string.artists))
    }
}

@Composable
private fun RowScope.StatTile(@DrawableRes icon: Int, value: String, label: String) {
    IconCategoryCard(iconRes = icon, title = value, subtitle = label, onClick = null, modifier = Modifier.weight(1f))
}

/** The ranked list for [category]: a rank column, then the shared library row with its plays. */
private fun LazyListScope.rankedItems(
    stats: ListeningStats,
    category: StatsCategory,
    navController: NavController,
    title: String,
    currentMediaId: String?,
    isPlaying: Boolean,
    metrics: ChartRankMetrics,
) {
    when (category) {
        StatsCategory.SONGS -> itemsIndexed(stats.songs, key = { _, it -> "song_${it.song.id}" }) { index, stat ->
            SongStatRow(stats.songs, index, stat, navController, title, currentMediaId, isPlaying, metrics, Modifier.animateItem())
        }

        StatsCategory.ARTISTS -> itemsIndexed(stats.artists, key = { _, it -> "artist_${it.artist.id}" }) { index, stat ->
            val artist = remember(stat) { stat.asArtist() }
            val menuState = LocalMenuState.current
            val haptic = LocalHapticFeedback.current
            val coroutineScope = rememberCoroutineScope()
            val showMenu = { menuState.show { ArtistMenu(artist, coroutineScope, menuState::dismiss) } }
            RankedRow(index + 1, metrics, Modifier.animateItem()) {
                ArtistListItem(
                    artist = artist,
                    subtitle = playsAndTime(stat.plays, stat.timeListened),
                    trailingContent = { MoreVertMenuButton(onClick = showMenu) },
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { navController.navigateToArtist(artist.id) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu()
                            },
                        ),
                )
            }
        }

    }
}

@Composable
private fun SongStatRow(
    songs: List<SongPlayStats>,
    index: Int,
    stat: SongPlayStats,
    navController: NavController,
    title: String,
    currentMediaId: String?,
    isPlaying: Boolean,
    metrics: ChartRankMetrics,
    modifier: Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isActive = stat.song.id == currentMediaId
    val showMenu = { menuState.show { SongMenu(originalSong = stat.song, navController = navController, onDismiss = menuState::dismiss) } }
    RankedRow(index + 1, metrics, modifier) {
        SongListItem(
            song = stat.song,
            subtitle = joinByBullet(bidiIsolate(stat.song.artists.joinToString { it.name }), playsAndTime(stat.plays, stat.timeListened)),
            isActive = isActive,
            isPlaying = isPlaying,
            trailingContent = { MoreVertMenuButton(onClick = showMenu) },
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {
                        if (activeRowTapTogglesPlayPause(isActive, playerConnection.isStationBroadcast.value)) {
                            playerConnection.playPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(title = title, items = songs.map { it.song.toMediaItem() }, startIndex = index),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu()
                    },
                ),
        )
    }
}

/** A list row behind the chart rank column, so every row's artwork lines up down the list. */
@Composable
private fun RankedRow(rank: Int, metrics: ChartRankMetrics, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        ChartRankCell(rank = rank, movement = null, metrics = metrics)
        content()
    }
}

/** One rank column width per list length, shared by every row of that list. */
@Composable
private fun rememberRankMetrics(count: Int): ChartRankMetrics =
    rememberChartRankMetrics(maxRank = count, maxDelta = 0, withMarkers = false)

@Composable
private fun playsAndTime(plays: Int, timeListenedMs: Long?): String =
    joinByBullet(pluralStringResource(R.plurals.n_time, plays, plays), durationLabel(timeListenedMs ?: 0L))

@Composable
private fun durationLabel(ms: Long): String? = runtimeLabel((ms / 1000).toInt())
