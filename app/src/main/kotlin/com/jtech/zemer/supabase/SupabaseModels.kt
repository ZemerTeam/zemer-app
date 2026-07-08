package com.jtech.zemer.supabase

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.Serializable

/**
 * Raw Supabase response row for the 24six trending RPC.
 */
@Serializable
data class TrendingWithComparisonRow(
    val song_name: String,
    val singer_name: String,
    val thumbnail_url: String? = null,
    val current_spot: Int? = null,
    val previous_spot: Int? = null,
    val scrape_time: String? = null,
    val category: String,
    val subcategory: String? = null,
    val url: String? = null,
)

sealed interface SupabaseItem {
    data class TrendingWithComparison(
        val songName: String,
        val singerName: String,
        val thumbnailUrl: String?,
        val currentSpot: Int?,
        val previousSpot: Int?,
        val category: String,
        val subcategory: String?,
        val url: String?,
    ) : SupabaseItem
}

fun TrendingWithComparisonRow.toSupabaseItem() = SupabaseItem.TrendingWithComparison(
    songName = song_name,
    singerName = singer_name,
    thumbnailUrl = thumbnail_url,
    currentSpot = current_spot,
    previousSpot = previous_spot,
    category = category,
    subcategory = subcategory,
    url = url,
)

fun SupabaseItem.TrendingWithComparison.toSongItem() = SongItem(
    id = "24six_${songName.hashCode().toLong().toString(16).replace("-", "0")}",
    title = songName,
    artists = listOf(Artist(name = singerName, id = null)),
    thumbnail = thumbnailUrl ?: "",
)

