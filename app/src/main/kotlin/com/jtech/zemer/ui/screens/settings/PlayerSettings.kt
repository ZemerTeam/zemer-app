package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jtech.zemer.ui.component.RequestInitialDpadFocus
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AudioNormalizationKey
import com.jtech.zemer.constants.AudioOffload
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.AudioQualityKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.VideoQualityKey
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.constants.AutoDownloadOnLikeKey
import com.jtech.zemer.constants.AutoLoadMoreKey
import com.jtech.zemer.constants.AutoSkipNextOnErrorKey
import com.jtech.zemer.constants.CastEnabledKey
import com.jtech.zemer.constants.DisableLoadMoreWhenRepeatAllKey
import com.jtech.zemer.constants.HistoryDuration
import com.jtech.zemer.constants.PersistentQueueKey
import com.jtech.zemer.constants.SeekExtraSeconds
import com.jtech.zemer.constants.SkipSilenceKey
import com.jtech.zemer.constants.StopMusicOnTaskClearKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.EnumListPreference
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SliderPreference
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.player.CastDownloadDialog
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (videoQuality, onVideoQualityChange) = rememberPreference(
        VideoQualityKey,
        defaultValue = VideoQualityLogic.AUTO
    )
    val (blockVideos, _) = rememberPreference(BlockVideosKey, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = true
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )
    val (castEnabled, onCastEnabledChange) = rememberPreference(
        CastEnabledKey,
        defaultValue = false
    )
    val playerConnection = LocalPlayerConnection.current
    var showCastDownloadDialog by remember { mutableStateOf(false) }

    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        key = AudioOffload,
        defaultValue = false
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (disableLoadMoreWhenRepeatAll, onDisableLoadMoreWhenRepeatAllChange) = rememberPreference(
        DisableLoadMoreWhenRepeatAllKey,
        defaultValue = false
    )
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    RequestInitialDpadFocus(firstFocus)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {

        SettingsCardGroup(
            title = stringResource(R.string.player),
            rows = buildList {
                add {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        valueText = {
                            when (it) {
                                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                            }
                        },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }

                // Default video-mode quality target (the in-player switcher overrides it per play). Values
                // are TARGETS: playback picks the best rung the video actually serves at or below the target.
                // Hidden when videos are blocked (no video plays anywhere — the Music Status settings pattern).
                if (!blockVideos) {
                    add {
                        ListPreference(
                            title = { Text(stringResource(R.string.video_quality)) },
                            icon = { Icon(painterResource(R.drawable.ondemand_video), null) },
                            selectedValue = videoQuality,
                            values = listOf(VideoQualityLogic.AUTO) + VideoQualityLogic.TARGET_HEIGHTS.map { "${it}p" },
                            valueText = {
                                if (it == VideoQualityLogic.AUTO) {
                                    stringResource(R.string.video_quality_auto)
                                } else {
                                    stringResource(
                                        R.string.video_quality_p,
                                        VideoQualityLogic.heightOfLabel(it) ?: 0,
                                    )
                                }
                            },
                            onValueSelected = onVideoQualityChange,
                        )
                    }
                }

                add {
                    SliderPreference(
                        title = { Text(stringResource(R.string.history_duration)) },
                        icon = { Icon(painterResource(R.drawable.history), null) },
                        value = historyDuration,
                        onValueChange = onHistoryDurationChange,
                    )
                }

                add {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.skip_silence)) },
                        icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                        checked = skipSilence,
                        onCheckedChange = onSkipSilenceChange,
                    )
                }

                add {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_normalization)) },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = audioNormalization,
                        onCheckedChange = onAudioNormalizationChange,
                    )
                }

                add {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_offload)) },
                        description = stringResource(R.string.audio_offload_description),
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = audioOffload,
                        onCheckedChange = onAudioOffloadChange,
                    )
                }

                add {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.seek_seconds_addup)) },
                        description = stringResource(R.string.seek_seconds_addup_description),
                        icon = { Icon(painterResource(R.drawable.arrow_forward), null) },
                        checked = seekExtraSeconds,
                        onCheckedChange = onSeekExtraSeconds,
                    )
                }
            },
        )

        SettingsCardGroup(
            title = stringResource(R.string.queue),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.persistent_queue)) },
                        description = stringResource(R.string.persistent_queue_desc),
                        icon = { Icon(painterResource(R.drawable.queue_music), null) },
                        checked = persistentQueue,
                        onCheckedChange = onPersistentQueueChange
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_load_more)) },
                        description = stringResource(R.string.auto_load_more_desc),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        checked = autoLoadMore,
                        onCheckedChange = onAutoLoadMoreChange,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_load_more_when_repeat_all)) },
                        description = stringResource(R.string.disable_load_more_when_repeat_all_desc),
                        icon = { Icon(painterResource(R.drawable.repeat), null) },
                        checked = disableLoadMoreWhenRepeatAll,
                        onCheckedChange = onDisableLoadMoreWhenRepeatAllChange,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_download_on_like)) },
                        description = stringResource(R.string.auto_download_on_like_desc),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        checked = autoDownloadOnLike,
                        onCheckedChange = onAutoDownloadOnLikeChange,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                        description = stringResource(R.string.auto_skip_next_on_error_desc),
                        icon = { Icon(painterResource(R.drawable.skip_next), null) },
                        checked = autoSkipNextOnError,
                        onCheckedChange = onAutoSkipNextOnErrorChange,
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.cast),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_casting)) },
                        description = stringResource(R.string.enable_casting_description),
                        icon = { Icon(painterResource(R.drawable.cast), null) },
                        checked = castEnabled,
                        onCheckedChange = { enabled ->
                            onCastEnabledChange(enabled)
                            // Prompt to download the cast support lib right away when enabling (it isn't bundled).
                            if (enabled && playerConnection?.service?.castLibLoader?.isReady != true) {
                                showCastDownloadDialog = true
                            }
                        },
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.misc),
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                        icon = { Icon(painterResource(R.drawable.clear_all), null) },
                        checked = stopMusicOnTaskClear,
                        onCheckedChange = onStopMusicOnTaskClearChange,
                    )
                },
            ),
        )
    }

    if (showCastDownloadDialog) {
        playerConnection?.let { pc ->
            CastDownloadDialog(playerConnection = pc, onDismiss = { showCastDownloadDialog = false })
        }
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.player_and_audio)) },
        navigationIcon = {
            BackNavigationIcon(
                navController,
                modifier = Modifier
                    .focusRequester(backFocus)
                    .focusProperties { down = firstFocus }
            )
        },
        colors = zemerTopAppBarColors(),
    )
}
