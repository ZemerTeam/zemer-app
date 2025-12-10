package com.jtech.zemer.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.core.content.edit
import androidx.navigation.NavController
import java.util.Collections
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.ChipSortTypeKey
import com.jtech.zemer.constants.CustomDensityScaleKey
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.DefaultOpenTabKey
import com.jtech.zemer.constants.DensityScale
import com.jtech.zemer.constants.DensityScaleKey
import com.jtech.zemer.constants.DynamicThemeKey
import com.jtech.zemer.constants.FloatingMiniPlayerKey
import com.jtech.zemer.constants.GridItemSize
import com.jtech.zemer.constants.GridItemsSizeKey
import com.jtech.zemer.constants.HidePlayerThumbnailKey
import com.jtech.zemer.constants.LibraryFilter
import com.jtech.zemer.constants.LyricsClickKey
import com.jtech.zemer.constants.LyricsScrollKey
import com.jtech.zemer.constants.LyricsTextPositionKey
import com.jtech.zemer.constants.PlayerBackgroundStyle
import com.jtech.zemer.constants.PlayerBackgroundStyleKey
import com.jtech.zemer.constants.PlayerButtonsStyle
import com.jtech.zemer.constants.PlayerButtonsStyleKey
import com.jtech.zemer.constants.PureBlackKey
import com.jtech.zemer.constants.ShowCachedPlaylistKey
import com.jtech.zemer.constants.ShowDownloadedPlaylistKey
import com.jtech.zemer.constants.ShowLikedPlaylistKey
import com.jtech.zemer.constants.ShowTopPlaylistKey
import com.jtech.zemer.constants.ShowUploadedPlaylistKey
import com.jtech.zemer.constants.SliderStyle
import com.jtech.zemer.constants.SliderStyleKey
import com.jtech.zemer.constants.SlimNavBarKey
import com.jtech.zemer.constants.SwipeSensitivityKey
import com.jtech.zemer.constants.SwipeThumbnailKey
import com.jtech.zemer.constants.SwipeToRemoveSongKey
import com.jtech.zemer.constants.SwipeToSongKey
import com.jtech.zemer.constants.BottomNavigationBarEnabledKey
import com.jtech.zemer.constants.BottomNavigationItemsKey
import com.jtech.zemer.constants.UseNewMiniPlayerDesignKey
import com.jtech.zemer.constants.UseNewPlayerDesignKey
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.EnumListPreference
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PlayerSliderTrack
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

@SuppressLint("UseKtx")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (useNewMiniPlayerDesign, onUseNewMiniPlayerDesignChange) = rememberPreference(
        UseNewMiniPlayerDesignKey,
        defaultValue = true
    )
    val (floatingMiniPlayerEnabled, onFloatingMiniPlayerEnabledChange) = rememberPreference(
        FloatingMiniPlayerKey,
        defaultValue = true
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(
        HidePlayerThumbnailKey,
        defaultValue = false
    )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (customDensityValue, setCustomDensityValue) = rememberPreference(CustomDensityScaleKey, defaultValue = 0.85f)
    val context = LocalContext.current
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomDensityDialog by rememberSaveable { mutableStateOf(false) }

    // Check SharedPreferences first for onboarding density value, then fallback to DataStore
    val sharedPreferences = remember { context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE) }
    val prefDensityScale = remember(sharedPreferences) {
        sharedPreferences.getFloat("density_scale_factor", 1.0f)
    }
    val (densityScale, setDensityScale) = rememberPreference(DensityScaleKey, defaultValue = prefDensityScale)

    val onDensityScaleChange: (Float) -> Unit = { newScale ->
        if (newScale == -1f) {
            // Custom option selected - show dialog for input
            showCustomDensityDialog = true
        } else {
            // Preset option selected - apply immediately
            setDensityScale(newScale)
            // Also write to SharedPreferences for DensityScaler to read on next startup
            context.getSharedPreferences("metrolist_settings", android.content.Context.MODE_PRIVATE)
                .edit {
                    putFloat("density_scale_factor", newScale)
                }
            showRestartDialog = true
        }
    }

    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) = rememberEnumPreference(
        PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.CENTER
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)

    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.DEFAULT
    )
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    // Check SharedPreferences first for onboarding bottom nav value, then fallback to DataStore
    val prefBottomNavEnabled = remember(sharedPreferences) {
        sharedPreferences.getBoolean("bottomNavigationBarEnabled", false)
    }
    val (bottomNavEnabled, onBottomNavEnabledChange) = rememberPreference(
        BottomNavigationBarEnabledKey,
        defaultValue = prefBottomNavEnabled
    )

    val (slimNav, onSlimNavChange) = rememberPreference(
        SlimNavBarKey,
        defaultValue = false
    )

    // Check SharedPreferences first for onboarding bottom nav items, then fallback to DataStore
    val prefBottomNavItems = remember(sharedPreferences) {
        sharedPreferences.getString("bottomNavigationItems", null)
    }
    val (bottomNavigationItems, onBottomNavigationItemsChange) = rememberPreference(
        BottomNavigationItemsKey,
        defaultValue = prefBottomNavItems ?: "home,artists,search,library"
    )

    var showBottomNavCustomizationDialog by rememberSaveable { mutableStateOf(false) }
    var currentSelectedItems by remember(bottomNavigationItems) {
        mutableStateOf(
            bottomNavigationItems.split(",").toSet()
        )
    }

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (swipeToRemoveSong, onSwipeToRemoveSongChange) = rememberPreference(
        SwipeToRemoveSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )
    val (showUploadedPlaylist, onShowUploadedPlaylistChange) = rememberPreference(
        ShowUploadedPlaylistKey,
        defaultValue = true
    )

    PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSliderOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.DEFAULT)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.default_),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SQUIGGLY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.SQUIGGLY)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    SquigglySlider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.squiggly),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SLIM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.SLIM)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {}
                                )
                            }
                    )

                    Text(
                        text = stringResource(R.string.slim),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.theme),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_dynamic_theme)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            checked = dynamicTheme,
            onCheckedChange = onDynamicThemeChange,
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.dark_theme)) },
            icon = { Icon(painterResource(R.drawable.dark_mode), null) },
            selectedValue = darkMode,
            onValueSelected = onDarkModeChange,
            valueText = {
                when (it) {
                    DarkMode.ON -> stringResource(R.string.dark_theme_on)
                    DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                    DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                }
            },
        )

        AnimatedVisibility(useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.pure_black)) },
                icon = { Icon(painterResource(R.drawable.contrast), null) },
                checked = pureBlack,
                onCheckedChange = onPureBlackChange,
            )
        }

        ListPreference(
            title = { Text("Display Density") },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = densityScale,
            values = DensityScale.entries.map { it.value },
            valueText = { scale ->
                val densityEnum = DensityScale.fromValue(scale)
                if (densityEnum == DensityScale.CUSTOM) {
                    // Show the actual custom percentage value
                    "Custom (${(customDensityValue * 100).toInt()}%)"
                } else {
                    densityEnum.label
                }
            },
            onValueSelected = onDensityScaleChange,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.player),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.new_player_design)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            checked = useNewPlayerDesign,
            onCheckedChange = onUseNewPlayerDesignChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.new_mini_player_design)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = useNewMiniPlayerDesign,
            onCheckedChange = onUseNewMiniPlayerDesignChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.floating_mini_player)) },
            description = stringResource(R.string.floating_mini_player_desc),
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = floatingMiniPlayerEnabled,
            onCheckedChange = onFloatingMiniPlayerEnabledChange,
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.player_background_style)) },
            icon = { Icon(painterResource(R.drawable.gradient), null) },
            selectedValue = playerBackground,
            onValueSelected = onPlayerBackgroundChange,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.hide_player_thumbnail)) },
            description = stringResource(R.string.hide_player_thumbnail_desc),
            icon = { Icon(painterResource(R.drawable.hide_image), null) },
            checked = hidePlayerThumbnail,
            onCheckedChange = onHidePlayerThumbnailChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.player_buttons_style)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            selectedValue = playerButtonsStyle,
            onValueSelected = onPlayerButtonsStyleChange,
            valueText = {
                when (it) {
                    PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                    PlayerButtonsStyle.SECONDARY -> stringResource(R.string.secondary_color_style)
                }
            },
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.player_slider_style)) },
            description =
                when (sliderStyle) {
                    SliderStyle.DEFAULT -> stringResource(R.string.default_)
                    SliderStyle.SQUIGGLY -> stringResource(R.string.squiggly)
                    SliderStyle.SLIM -> stringResource(R.string.slim)
                },
            icon = { Icon(painterResource(R.drawable.sliders), null) },
            onClick = {
                showSliderOptionDialog = true
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeThumbnail,
            onCheckedChange = onSwipeThumbnailChange,
        )

        AnimatedVisibility(swipeThumbnail) {
            var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }
            
            if (showSensitivityDialog) {
                var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }
                
                DefaultDialog(
                    onDismiss = { 
                        tempSensitivity = swipeSensitivity
                        showSensitivityDialog = false 
                    },
                    buttons = {
                        TextButton(
                            onClick = { 
                                tempSensitivity = 0.73f
                            }
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        TextButton(
                            onClick = { 
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false 
                            }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(
                            onClick = { 
                                onSwipeSensitivityChange(tempSensitivity)
                                showSensitivityDialog = false 
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.swipe_sensitivity),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
    
                        Text(
                            text = stringResource(R.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
    
                        Slider(
                            value = tempSensitivity,
                            onValueChange = { tempSensitivity = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            PreferenceEntry(
                title = { Text(stringResource(R.string.swipe_sensitivity)) },
                description = stringResource(R.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                icon = { Icon(painterResource(R.drawable.tune), null) },
                onClick = { showSensitivityDialog = true }
            )
        }

        EnumListPreference(
            title = { Text(stringResource(R.string.lyrics_text_position)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsPosition,
            onValueSelected = onLyricsPositionChange,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_click_change)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsClick,
            onCheckedChange = onLyricsClickChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsScroll,
            onCheckedChange = onLyricsScrollChange,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc),
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.SEARCH -> stringResource(R.string.search)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
        )

        ListPreference(
            title = { Text(stringResource(R.string.default_lib_chips)) },
            icon = { Icon(painterResource(R.drawable.tab), null) },
            selectedValue = defaultChip,
            values = listOf(
                LibraryFilter.LIBRARY, LibraryFilter.PLAYLISTS, LibraryFilter.SONGS,
                LibraryFilter.VIDEOS, LibraryFilter.ALBUMS, LibraryFilter.ARTISTS
            ),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.VIDEOS -> stringResource(R.string.videos)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
            onValueSelected = onDefaultChipChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_song_to_add)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeToSong,
            onCheckedChange = onSwipeToSongChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_song_to_remove)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeToRemoveSong,
            onCheckedChange = onSwipeToRemoveSongChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.bottom_nav_bar)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = bottomNavEnabled,
            onCheckedChange = { enabled ->
                onBottomNavEnabledChange(enabled)
                // Reset to default when toggling
                if (!enabled) {
                    onBottomNavigationItemsChange("home,artists,search,library")
                }
            }
        )

        AnimatedVisibility(visible = bottomNavEnabled) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.customize_bottom_navigation)) },
                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                onClick = { showBottomNavCustomizationDialog = true }
            )
        }

        EnumListPreference(
            title = { Text(stringResource(R.string.grid_cell_size)) },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = gridItemSize,
            onValueSelected = onGridItemSizeChange,
            valueText = {
                when (it) {
                    GridItemSize.BIG -> stringResource(R.string.big)
                    GridItemSize.SMALL -> stringResource(R.string.small)
                }
            },
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.auto_playlists)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_liked_playlist)) },
            icon = { Icon(painterResource(R.drawable.favorite), null) },
            checked = showLikedPlaylist,
            onCheckedChange = onShowLikedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_downloaded_playlist)) },
            icon = { Icon(painterResource(R.drawable.offline), null) },
            checked = showDownloadedPlaylist,
            onCheckedChange = onShowDownloadedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_top_playlist)) },
            icon = { Icon(painterResource(R.drawable.trending_up), null) },
            checked = showTopPlaylist,
            onCheckedChange = onShowTopPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_cached_playlist)) },
            icon = { Icon(painterResource(R.drawable.cached), null) },
            checked = showCachedPlaylist,
            onCheckedChange = onShowCachedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_uploaded_playlist)) },
            icon = { Icon(painterResource(R.drawable.backup), null) },
            checked = showUploadedPlaylist,
            onCheckedChange = onShowUploadedPlaylistChange
        )
    }

    if (showCustomDensityDialog) {
        TextFieldDialog(
            onDismiss = { showCustomDensityDialog = false },
            title = { Text("Custom Display Density") },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue((customDensityValue * 100).toInt().toString()),
            keyboardType = KeyboardType.Decimal,
            isInputValid = { input ->
                val value = input.toFloatOrNull()?.let { percent ->
                    if (percent > 1.5f) percent / 100f else percent
                }
                value != null && value in 0.5f..1.2f
            },
            onDone = { input ->
                val value = input.toFloatOrNull()?.let { percent ->
                    // Accept both percentage (85) and decimal (0.85) format
                    if (percent > 1.5f) percent / 100f else percent
                }

                if (value != null && value in 0.5f..1.2f) {
                    setCustomDensityValue(value)
                    setDensityScale(value)

                    // Write to SharedPreferences
                    context.getSharedPreferences("metrolist_settings", android.content.Context.MODE_PRIVATE)
                        .edit {
                            putFloat("density_scale_factor", value)
                        }

                    showCustomDensityDialog = false
                    showRestartDialog = true
                }
            },
            extraContent = {
                Text(
                    text = "Enter a value between 50% and 120%.\n\nExamples: 85 or 0.85 for 85%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    if (showRestartDialog) {
        DefaultDialog(
            onDismiss = { showRestartDialog = false },
            buttons = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        // Restart the app
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                ) {
                    Text(text = "Restart")
                }
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Restart Required",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "The display density change will take effect after restarting the app. Do you want to restart now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Bottom Navigation Customization Dialog
    if (showBottomNavCustomizationDialog) {
        DefaultDialog(
            onDismiss = { showBottomNavCustomizationDialog = false },
            pureBlack = pureBlack,
            buttons = {
                TextButton(
                    onClick = { showBottomNavCustomizationDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel), color = if (pureBlack) Color.White else Color.Unspecified)
                }
                TextButton(
                    onClick = {
                        // Build the selected items string
                        val selectedScreens = listOf("home", "artists", "kid_zone", "search", "library")
                            .filter { it in currentSelectedItems }
                            .joinToString(",")
                        onBottomNavigationItemsChange(selectedScreens)
                        showBottomNavCustomizationDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok), color = if (pureBlack) Color.White else Color.Unspecified)
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(if (pureBlack) Color(0xFF0A0A0A) else Color.Transparent),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bottom Navigation Items",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${currentSelectedItems.size}/5 selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pureBlack) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )

                // Available navigation items
                val availableItems = listOf(
                    "home" to stringResource(R.string.home),
                    "artists" to stringResource(R.string.artists),
                    "kid_zone" to stringResource(R.string.kid_zone),
                    "search" to stringResource(R.string.search),
                    "library" to stringResource(R.string.filter_library)
                )

                val listState = rememberLazyListState()
                Box(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    items(availableItems.size) { index ->
                        val (key, title) = availableItems[index]
                        val isSelected = key in currentSelectedItems
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected && currentSelectedItems.size > 1) {
                                        currentSelectedItems = currentSelectedItems - key
                                    } else if (!isSelected && currentSelectedItems.size < 5) {
                                        currentSelectedItems = currentSelectedItems + key
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked && currentSelectedItems.size < 5) {
                                        currentSelectedItems = currentSelectedItems + key
                                    } else if (!checked && currentSelectedItems.size > 1) {
                                        currentSelectedItems = currentSelectedItems - key
                                    }
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = if (pureBlack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                    uncheckedColor = if (pureBlack) Color.Gray else Color.Unspecified,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                }

                if (currentSelectedItems.isEmpty()) {
                    Text(
                        text = "Select at least 1 item",
                        color = if (pureBlack) Color.Red else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}
