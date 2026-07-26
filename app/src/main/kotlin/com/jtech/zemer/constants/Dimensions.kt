@file:Suppress("unused")

package com.jtech.zemer.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val NavigationBarHeight = 80.dp
val SlimNavBarHeight = 64.dp
val MiniPlayerHeight = 64.dp
val MiniPlayerBottomSpacing = 8.dp // Space between MiniPlayer and NavigationBar
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val ListThumbnailSize = 48.dp

/**
 * The two insets a shared list row places before its artwork: the row's own horizontal padding,
 * then the box around the thumbnail. Named here because anything drawn to the LEFT of a row — the
 * chart rank cell is the only one today — has to know how much empty space follows it in order to
 * align against the artwork. Same values as before; naming them only removes the need to copy them.
 */
val ListItemHorizontalPadding = 8.dp
val ListThumbnailPadding = 6.dp

/** Total empty space between a leading cell and the artwork beside it. */
val ListArtLeadingSpace = ListItemHorizontalPadding + ListThumbnailPadding
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 6.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMediumLow)
