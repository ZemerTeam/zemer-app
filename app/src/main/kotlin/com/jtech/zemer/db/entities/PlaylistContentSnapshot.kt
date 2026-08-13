package com.jtech.zemer.db.entities

/**
 * One playlist's current local state, as observed by the share auto-updater (issue #176): id,
 * title and the ordered member ids (CSV - videoIds never contain commas). A query projection, not
 * a table; share credentials deliberately live OUTSIDE Room (DataStore) so the feature needs no
 * schema migration.
 */
data class PlaylistContentSnapshot(
    val playlistId: String,
    val name: String,
    val songIdsCsv: String?,
) {
    val songIds: List<String>
        get() = songIdsCsv?.split(',')?.filter { it.isNotBlank() }.orEmpty()
}
