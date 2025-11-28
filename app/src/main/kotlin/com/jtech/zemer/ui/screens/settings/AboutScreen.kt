package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Zemer",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "An audio-first YouTube Music experience",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Version ${BuildConfig.VERSION_NAME}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    val buildLabel =
                        if (BuildConfig.DEBUG) "Debug build" else "${BuildConfig.ARCHITECTURE.uppercase()} build"
                    AssistChip(
                        onClick = {},
                        label = { Text(buildLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth(0.7f))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Maintained by Ars18",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "With TripleU, Chatzie & the community",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = "Based on Metrolist by MO AGAMY",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                FilledTonalButton(
                    onClick = { uriHandler.openUri("https://github.com/alltechdev") },
                    modifier = Modifier
                        .widthIn(min = 0.dp, max = 220.dp)
                        .focusRequester(firstFocus)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.github),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}
