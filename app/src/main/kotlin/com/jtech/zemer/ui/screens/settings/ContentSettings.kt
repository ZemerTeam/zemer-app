package com.jtech.zemer.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.AllowChasidishKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.AppLanguageKey
import com.jtech.zemer.constants.ContentCountryKey
import com.jtech.zemer.constants.ContentLanguageKey
import com.jtech.zemer.constants.CountryCodeToName
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.EnableLrcLibKey
import com.jtech.zemer.constants.LanguageCodeToName
import com.jtech.zemer.constants.QuickPicks
import com.jtech.zemer.constants.QuickPicksKey
import com.jtech.zemer.constants.SYSTEM_DEFAULT
import com.jtech.zemer.constants.TopSize
import com.jtech.zemer.ui.component.EditTextPreference
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.setAppLocale
import com.metrolist.innertube.YouTube
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    rememberCoroutineScope()

    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (enableContentFilters, onEnableContentFiltersChange) = rememberPreference(key = EnableContentFiltersKey, defaultValue = true)
    val (allowFemaleSingers, onAllowFemaleSingersChange) = rememberPreference(key = AllowFemaleSingersKey, defaultValue = false)
    val (allowChasidish, onAllowChasidishChange) = rememberPreference(key = AllowChasidishKey, defaultValue = false)
    val (blockVideos, onBlockVideosChange) = rememberPreference(key = BlockVideosKey, defaultValue = false)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.general))
        ListPreference(
            title = { Text(stringResource(R.string.content_language)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
                val languageTag = locale.toLanguageTag().replace("-Hant", "")
 
                YouTube.locale = YouTube.locale.copy(
                    hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en"
                )
 
                onContentLanguageChange(newValue)
            }
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_country)) },
            icon = { Icon(painterResource(R.drawable.location_on), null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
 
                YouTube.locale = YouTube.locale.copy(
                    gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.country.takeIf { it in CountryCodeToName }
                        ?: "US"
                )
 
                onContentCountryChange(newValue)
           }
        )

        PreferenceGroupTitle(title = stringResource(R.string.app_language))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.app_language)) },
                icon = { Icon(painterResource(R.drawable.language), null) },
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            )
        }
        // Support for Android versions before Android 13
        else {
            ListPreference(
                title = { Text(stringResource(R.string.app_language)) },
                icon = { Icon(painterResource(R.drawable.language), null) },
                selectedValue = appLanguage,
                values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                valueText = {
                    LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                },
                onValueSelected = { langTag ->
                    val newLocale = langTag
                        .takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()

                    onAppLanguageChange(langTag)
                    setAppLocale(context, newLocale)

                }
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.lyrics))
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_lrclib)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableLrclib,
            onCheckedChange = onEnableLrclibChange,
        )

        PreferenceGroupTitle(title = stringResource(R.string.content_filters))
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_personal_filters)) },
            icon = { Icon(painterResource(R.drawable.settings), null) },
            checked = enableContentFilters,
            onCheckedChange = onEnableContentFiltersChange,
            isEnabled = true
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.allow_female_singers)) },
            icon = { Icon(painterResource(R.drawable.person), null) },
            checked = allowFemaleSingers,
            onCheckedChange = onAllowFemaleSingersChange,
            isEnabled = enableContentFilters
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.block_videos)) },
            icon = { Icon(painterResource(R.drawable.ic_video_hd), null) },
            checked = blockVideos,
            onCheckedChange = onBlockVideosChange,
            isEnabled = enableContentFilters
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.i_am_chasidish)) },
            icon = { Icon(painterResource(R.drawable.person), null) },
            checked = allowChasidish,
            onCheckedChange = onAllowChasidishChange,
            isEnabled = enableContentFilters
        )
        PreferenceGroupTitle(title = stringResource(R.string.misc))
        EditTextPreference(
            title = { Text(stringResource(R.string.top_length)) },
            icon = { Icon(painterResource(R.drawable.trending_up), null) },
            value = lengthTop,
            isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
            onValueChange = onLengthTopChange,
        )
        ListPreference(
            title = { Text(stringResource(R.string.set_quick_picks)) },
            icon = { Icon(painterResource(R.drawable.home_outlined), null) },
            selectedValue = quickPicks,
            values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
            valueText = {
                when (it) {
                    QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                    QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                }
            },
            onValueSelected = onQuickPicksChange,)
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
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
