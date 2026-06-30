package com.jtech.zemer.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.ArtistBlacklistKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BlacklistedArtist(
    val artistId: String,
    val artistName: String,
)

object ArtistBlacklistManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, BlacklistedArtist>()

    fun initialize(context: Context) {
        val raw = context.dataStore[ArtistBlacklistKey] ?: return
        try {
            val entries = json.decodeFromString<List<BlacklistedArtist>>(raw)
            cache.putAll(entries.associateBy { it.artistId })
        } catch (_: Exception) {
            cache.clear()
        }
    }

    fun getBlacklist(): Set<BlacklistedArtist> = cache.values.toSet()

    suspend fun addToBlacklist(context: Context, artistId: String, artistName: String) {
        val entry = BlacklistedArtist(artistId, artistName)
        cache[artistId] = entry
        persist(context)
    }

    suspend fun removeFromBlacklist(context: Context, artistId: String) {
        cache.remove(artistId)
        persist(context)
    }

    fun isBlacklisted(artistId: String): Boolean = cache.containsKey(artistId)

    fun blacklistedIds(): Set<String> = cache.keys.toSet()

    private suspend fun persist(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[ArtistBlacklistKey] = json.encodeToString(cache.values.toList())
        }
    }
}
