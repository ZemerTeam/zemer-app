package com.jtech.zemer.ui.component

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.utils.backToMain

/**
 * The shared "list detail" top app bar whose title cycles between an idle screen title, a transparent
 * in-bar search field, and a selection-count title — used by every playlist-detail / history screen
 * that offers in-bar search AND multi-select. ONE copy so the (identical, error-prone) back-button
 * precedence, transparent search-field styling and selection-count title can't drift per screen.
 *
 * Per-screen differences are parameters:
 * - [idleTitle] is the title shown when neither searching nor selecting; pass `null` to render no title
 *   (e.g. the online/local playlist screens only show it once scrolled past the header).
 * - [selectionCount] is the number of selected items, or `null` when NOT in selection mode. When
 *   non-null the bar shows the close nav icon, the `[selectionCountPlural]`-formatted count, and the
 *   caller's [actions] (its select-all / overflow cluster); otherwise it shows a search action.
 * - [actions] is the selection-mode action cluster (e.g. [SelectionActions] or a custom select-all +
 *   overflow pair). It is only invoked in selection mode.
 *
 * The nav icon's back precedence is fixed: exit search -> exit selection ([onExitSelection]) ->
 * [NavController.navigateUp]; long-press jumps to Home ([NavController.backToMain]). By default the
 * long-press is active only in the idle state (not searching, not selecting); [backToMainDuringSelection]
 * keeps it active during selection (the local-playlist variant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableSelectableTopAppBar(
    navController: NavController,
    idleTitle: String?,
    isSearching: Boolean,
    onIsSearchingChange: (Boolean) -> Unit,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    selectionCount: Int?,
    @PluralsRes selectionCountPlural: Int,
    onExitSelection: () -> Unit,
    modifier: Modifier = Modifier,
    backToMainDuringSelection: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val inSelection = selectionCount != null

    TopAppBar(
        modifier = modifier,
        title = {
            when {
                inSelection -> {
                    AppBarTitle(
                        text = pluralStringResource(
                            selectionCountPlural,
                            selectionCount,
                            selectionCount,
                        )
                    )
                }
                isSearching -> {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                idleTitle != null -> {
                    AppBarTitle(text = idleTitle)
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    when {
                        isSearching -> {
                            onIsSearchingChange(false)
                            onQueryChange(TextFieldValue())
                            focusManager.clearFocus()
                        }
                        inSelection -> {
                            onExitSelection()
                        }
                        else -> {
                            navController.navigateUp()
                        }
                    }
                },
                onLongClick = {
                    val allowed =
                        if (backToMainDuringSelection) !isSearching else (!isSearching && !inSelection)
                    if (allowed) {
                        navController.backToMain()
                    }
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (inSelection) R.drawable.close else R.drawable.arrow_back
                    ),
                    contentDescription = null
                )
            }
        },
        actions = {
            if (inSelection) {
                actions()
            } else if (!isSearching) {
                Material3IconButton(
                    onClick = { onIsSearchingChange(true) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null
                    )
                }
            }
        },
        colors = zemerTopAppBarColors(),
    )
}
