package com.jtech.zemer.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.utils.backToMain

@Composable
fun ResizableIconButton(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    indication: Indication? = null,
    onClick: () -> Unit = {},
) {
    val isFocused = remember { mutableStateOf(false) }
    val borderColor = animateColorAsState(
        targetValue = if (isFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "button_focus_border"
    )
    val bgColor = animateColorAsState(
        targetValue = if (isFocused.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        label = "button_focus_bg"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.value)
            .border(3.dp, borderColor.value, RoundedCornerShape(8.dp))
            .focusable()
            .onFocusChanged { isFocused.value = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick,
                )
                .alpha(if (enabled) 1f else 0.5f)
                .size(32.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(color = colors.containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = 24.dp
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = colors.contentColor
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/**
 * The shared back-arrow [TopAppBar] navigation icon: tap navigates up, long-press jumps straight
 * back to Home ([backToMain]). Used at every screen-level `TopAppBar`'s `navigationIcon` slot;
 * screens with a different navigation-icon pattern (e.g. an icon that branches by UI state) should
 * not be forced into this shared component.
 */
@Composable
fun BackNavigationIcon(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = navController::navigateUp,
        onLongClick = navController::backToMain,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = null,
        )
    }
}

/**
 * A plain top-app-bar action icon: a transparent (container-less) [androidx.compose.material3.IconButton]
 * holding one drawable. This is the shared look for every screen-level `TopAppBar` action here (Home's
 * history/search/now-playing, the Artists/KidZone refresh) so they can't drift into per-site chrome
 * (the old filled `surfaceContainerHigh` circles). Pass [modifier] through for D-pad focus wiring.
 */
@Composable
fun TopAppBarActionButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Material3IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}

/**
 * The shared trailing 3-dot overflow-menu [IconButton]. Most call sites are a plain
 * [androidx.compose.material3.IconButton] with tap-only `onClick`; passing [onLongClick] switches
 * to the app's [IconButton] (combined-clickable) instead, preserving each site's exact widget
 * (touch target / ripple / focus-border chrome) rather than forcing one shape onto every caller.
 */
@Composable
fun MoreVertMenuButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onLongClick != null) {
        IconButton(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null,
            )
        }
    } else {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null,
            )
        }
    }
}
