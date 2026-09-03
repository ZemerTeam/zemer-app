package com.jtech.zemer.db

/**
 * SQLite binds at most 999 variables per statement on Android below 11 (SQLITE_MAX_VARIABLE_NUMBER), so a
 * `WHERE id IN (:ids)` over a long list throws "too many SQL variables" and kills the DB thread. Every DAO
 * `IN (:list)` query is split through [chunks]; the crash hit `getSongsByIds` from the player-cache scan on an
 * Android 8 device with more than 999 cached ids.
 */
object SqliteBindLimit {
    /** Below the 999 hard limit, leaving room for the query's other bound arguments. */
    const val MAX_IN_VARIABLES = 900

    fun <T> chunks(ids: List<T>): List<List<T>> = if (ids.isEmpty()) emptyList() else ids.chunked(MAX_IN_VARIABLES)
}
