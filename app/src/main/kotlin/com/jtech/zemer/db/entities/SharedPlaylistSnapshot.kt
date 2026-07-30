package com.jtech.zemer.db.entities

/**
 * One shared playlist's current local state, as observed by the live-share auto-updater (issue
 * #176): identity + credentials + the ordered member ids (CSV - videoIds never contain commas)
 * and the hash of the state last successfully pushed. Not a table - a query projection.
 */
data class SharedPlaylistSnapshot(
    val playlistId: String,
    val name: String,
    val shareId: String,
    val shareOwnerToken: String,
    val shareSyncedHash: String?,
    /** The name THIS share was created with (null = anonymous) - what updates must keep sending. */
    val shareSharedBy: String?,
    val songIdsCsv: String?,
) {
    val songIds: List<String>
        get() = songIdsCsv?.split(',')?.filter { it.isNotBlank() }.orEmpty()
}
