package com.jtech.zemer.statuses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-contained client for JewishStatus.com — a platform of Jewish/kosher music creators posting
 * short video/image/text "statuses" (WhatsApp/Stories style). Backed by a public Supabase (PostgREST)
 * API + a Cloudflare R2 CDN. Ported from the standalone `jewishstatus` spike.
 *
 * This is a THIRD-PARTY service the app cannot guarantee is up, so every consumer treats a failure as
 * "no data" (the Home row hides itself). Kept deliberately dependency-free (raw [HttpURLConnection], no
 * coupling to the app's OkHttp / YouTube proxy) — it talks to a different backend.
 *
 * [KEY] is a Supabase **publishable** anon key: client-safe by design (the JewishStatus web app ships
 * the same one publicly), NOT a secret. Do not treat it like a service-account credential.
 */
private const val BASE = "https://raiodurvjneoehnphkrs.supabase.co/rest/v1"
private const val CDN = "https://pub-0dd407ad34e240909673d1619658d5c2.r2.dev"
private const val KEY = "sb_publishable_Pj9SDOxf5Xxw9LavwAl5yw_5ldleSyD"

// The three music categories on JewishStatus (a creator may appear in more than one → deduped).
private const val CAT_JEWISH_MUSIC = "dc207cab-3514-4ae8-a5c1-8a69fb27ced3"
private const val CAT_MUSIC_IND = "02ed4e29-d461-43f4-9aab-e16d05d3f795"
private const val CAT_CONCERTS = "5a08c0ba-400a-4576-aa33-97fa9ec38d0e"

data class StatusCreator(
    val id: String,
    val slug: String,
    val displayName: String,
    val avatarPath: String?,
    val liveNow: Boolean,
    // The recent status ids (`recent_post_ids`) — drives the segmented story ring (one segment each)
    // and the WhatsApp "read" state (all seen => muted ring). `updates_count` is 0 on the browse RPC,
    // so the length of this list is the count to use.
    val recentPostIds: List<String> = emptyList(),
    val isVerified: Boolean = false,
    val downloadsEnabled: Boolean = true,
)

data class StatusPost(
    val id: String,
    val kind: String,               // "video" | "image" | "text"
    val mediaPath: String?,
    val thumbPath: String?,
    val caption: String?,
    val textBody: String?,          // the body of a text-only status (text posts have no media/caption)
    val textBgColor: String?,       // optional "#RRGGBB" background for a text status
    val linkUrl: String?,
    val durationSeconds: Int?,      // actual video length; the site defaults images/text to 7s
    val postedAt: String,           // ISO-8601, e.g. "2026-08-01T19:32:52+00:00"
    val viewCount: Int = 0,
    val downloadCount: Int = 0,
)

/**
 * Whether EVERY one of a creator's recent statuses has been viewed (WhatsApp "read"). A creator with no
 * known statuses is not "all seen". Used to sink fully-viewed creators to the end of the row/grid.
 */
fun StatusCreator.allStatusesSeen(seenPostIds: Set<String>): Boolean =
    recentPostIds.isNotEmpty() && recentPostIds.all { it in seenPostIds }

/**
 * Creators ordered for the Home row AND the See-all grid (one definition so the two can't drift):
 * fully-viewed creators sink to the end (WhatsApp), preserving recency order within each group.
 */
fun List<StatusCreator>.sortedByUnseenFirst(seenPostIds: Set<String>): List<StatusCreator> =
    sortedBy { it.allStatusesSeen(seenPostIds) }

fun statusAvatarUrl(path: String?): String? = path?.let { "$CDN/avatars/$it" }
fun statusMediaUrl(path: String?): String? = path?.let { "$CDN/status-media/$it" }

// --- Public API. All calls are blocking; run them off the main thread (the repository uses IO). ---

/** All creators across the three music categories, deduplicated, most recent first. */
suspend fun fetchStatusCreators(): List<StatusCreator> = coroutineScope {
    // The three categories are independent, so fetch them concurrently instead of summing their
    // latencies (each is a paginating series of blocking round-trips).
    val seen = mutableSetOf<String>()
    val base = listOf(CAT_JEWISH_MUSIC, CAT_MUSIC_IND, CAT_CONCERTS)
        .map { cat -> async(Dispatchers.IO) { fetchByCategory(cat) } }
        .awaitAll()
        .flatten()
        .filter { seen.add(it.id) }

    // Batch is_verified + downloads_enabled (the browse RPC omits them), chunked so a large creator
    // list never builds an over-length `id=in.(...)` URL that a proxy 414s.
    val details = fetchCreatorDetails(base.map { it.id })
    base.map { c ->
        val d = details[c.id]
        c.copy(
            isVerified = d?.first ?: false,
            downloadsEnabled = d?.second ?: true,
        )
    }
}

/**
 * All visible posts for one creator, in CHRONOLOGICAL order (oldest first) so the story viewer plays
 * them the WhatsApp way (oldest -> newest). No artificial cap (paginates 100/page so a creator with
 * 100+ posts is fully covered). We deliberately do NOT prioritize `is_featured` here — pinning featured
 * posts to the front would scramble the timeline.
 */
fun fetchStatusPosts(creatorId: String): List<StatusPost> {
    val all = mutableListOf<StatusPost>()
    val pageSize = 100
    var offset = 0
    while (true) {
        val url = "$BASE/public_posts" +
            "?creator_id=eq.$creatorId" +
            "&select=id,kind,media_path,thumb_path,caption,text_body,text_bg_color,link_url," +
            "duration_seconds,posted_at,view_count,download_count" +
            "&order=posted_at.asc" +
            "&limit=$pageSize&offset=$offset"
        val page = parsePosts(JSONArray(getJson(url)))
        all += page
        if (page.size < pageSize) break
        offset += pageSize
    }
    return all
}

// --- Private helpers ---

private fun fetchByCategory(catId: String): List<StatusCreator> {
    val all = mutableListOf<StatusCreator>()
    val pageSize = 100
    var offset = 0
    while (true) {
        val body = """{"p_section":"all","p_search":null,"p_limit":$pageSize,"p_offset":$offset,
            "p_category":"$catId","p_location":null,"p_sort":"recent"}"""
        val page = parseCreators(JSONArray(postJson("$BASE/rpc/browse_creators_sorted", body)))
        all += page
        if (page.size < pageSize) break
        offset += pageSize
    }
    return all
}

/**
 * Returns Map<id, Pair<isVerified, downloadsEnabled>>, chunked so the `id=in.(...)` URL stays under
 * any proxy length limit. Best-effort per chunk: a failed chunk is skipped (those creators just miss
 * their verified badge / default to downloads-enabled) rather than failing the whole creators load.
 */
private fun fetchCreatorDetails(ids: List<String>): Map<String, Pair<Boolean, Boolean>> {
    if (ids.isEmpty()) return emptyMap()
    val out = mutableMapOf<String, Pair<Boolean, Boolean>>()
    ids.chunked(DETAILS_CHUNK).forEach { chunk ->
        runCatching {
            val url = "$BASE/public_creators?select=id,is_verified,downloads_enabled&id=in.(${chunk.joinToString(",")})"
            val arr = JSONArray(getJson(url))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getString("id")] = Pair(
                    o.optBoolean("is_verified", false),
                    o.optBoolean("downloads_enabled", true),
                )
            }
        }
    }
    return out
}

private const val DETAILS_CHUNK = 100

// A nullable string field. Guards the org.json gotcha: on Android's runtime `optString` returns the
// literal "null" for a JSON `null` value (the reference impl returns ""), which was rendering a text
// status body as "null". Checking isNull first is correct on BOTH implementations.
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

// parseCreators / parsePosts are internal so the JSON mapping is unit-testable without a live request.
internal fun parseCreators(arr: JSONArray): List<StatusCreator> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        StatusCreator(
            id = o.getString("id"),
            slug = o.getString("slug"),
            displayName = o.getString("display_name"),
            avatarPath = o.optStringOrNull("avatar_path"),
            liveNow = o.optBoolean("live_now", false),
            recentPostIds = o.optJSONArray("recent_post_ids")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
            } ?: emptyList(),
        )
    }

internal fun parsePosts(arr: JSONArray): List<StatusPost> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        StatusPost(
            id = o.getString("id"),
            kind = o.getString("kind"),
            mediaPath = o.optStringOrNull("media_path"),
            thumbPath = o.optStringOrNull("thumb_path"),
            caption = o.optStringOrNull("caption"),
            textBody = o.optStringOrNull("text_body"),
            textBgColor = o.optStringOrNull("text_bg_color"),
            linkUrl = o.optStringOrNull("link_url"),
            durationSeconds = if (o.isNull("duration_seconds")) null else o.getInt("duration_seconds"),
            postedAt = o.optString("posted_at"),
            viewCount = o.optInt("view_count", 0),
            downloadCount = o.optInt("download_count", 0),
        )
    }

private fun postJson(url: String, body: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = TIMEOUT_MS
    conn.readTimeout = TIMEOUT_MS
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("apikey", KEY)
    conn.setRequestProperty("Authorization", "Bearer $KEY")
    conn.doOutput = true
    conn.outputStream.use { it.write(body.toByteArray()) }
    return conn.inputStream.use { it.reader().readText() }
}

private fun getJson(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = TIMEOUT_MS
    conn.readTimeout = TIMEOUT_MS
    conn.setRequestProperty("apikey", KEY)
    conn.setRequestProperty("Authorization", "Bearer $KEY")
    return conn.inputStream.use { it.reader().readText() }
}

private const val TIMEOUT_MS = 10_000
