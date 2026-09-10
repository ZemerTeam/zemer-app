package com.jtech.zemer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song

/**
 * The live Room row for a song rendered from a snapshot list (Quick Picks, Forgotten Favorites and their
 * See-all), so liked / downloaded state stays current - falling back to the snapshot when the row is gone.
 * The row CAN vanish under the composable: the whitelist sync deletes a de-whitelisted artist's songs
 * while Home still lists them, and unwrapping the emitted null crashed Home (Crashlytics: HomeScreen
 * LazyGrid NPE). One helper so no row re-rolls the `!!`.
 */
@Composable
fun rememberLiveSong(database: MusicDatabase, snapshot: Song): Song {
    val live by database.song(snapshot.id).collectAsState(initial = snapshot)
    return live ?: snapshot
}
