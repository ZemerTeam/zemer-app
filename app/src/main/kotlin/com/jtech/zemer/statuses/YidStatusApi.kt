package com.jtech.zemer.statuses

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Self-contained client for YidStatus.com - a second, larger Jewish/kosher status platform. Backed by a
 * Supabase project fronted by the custom domain `api.yidstatus.com` + Supabase Storage. Full API doc:
 * `docs/status/yidstatus-api.md`.
 *
 * Unlike JewishStatus (per-creator pagination), YidStatus serves ONE global feed for a rolling window;
 * we fetch it once, filter to the MUSIC categories, and group statuses by creator. Media URLs come back
 * fully qualified, so they pass straight through [statusMediaUrl]/[statusAvatarUrl].
 *
 * Two non-obvious rules (see the doc):
 *  - The `/feed` edge function returns 403 unless the request carries `Origin: https://yidstatus.com`.
 *    A native client may set it freely; treat the whole thing as fail-soft (the row hides on failure).
 *  - [KEY] is the platform's public anon JWT (client-safe, read-only), NOT a secret.
 */
private const val YID_BASE = "https://api.yidstatus.com"
private const val YID_FEED_URL = "$YID_BASE/functions/v1/feed"
private const val YID_ORIGIN = "https://yidstatus.com"
private const val YID_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZzaW53YWxxaGd3YXBldndpYm1k" +
        "Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI2ODEyODUsImV4cCI6MjA5ODI1NzI4NX0." +
        "ZwrXgeUknPSDAWsOzdI8jdj7wCO9xOe7glLSj3OB_vA"

// Rolling window (days). Kept at 1 deliberately: the feed is GLOBAL (all categories) and costs ~3.35 MB
// per day before filtering, and the edge function hard-errors past ~15 days (WORKER_RESOURCE_LIMIT).
// YidStatus exposes NO per-creator history endpoint, so a deep "jump to date" like JewishStatus is not
// achievable from its public API - one day already spans ~2 calendar dates, which is the practical cap.
private const val YID_FEED_DAYS = 1

// A creator is "music" if any of its categories contains one of these (case-insensitive). Owner choice
// (2026-08-02): music acts + simchas/events + concerts; NOT comedy/entertainment/news/business/etc.
private val YID_MUSIC_KEYWORDS = listOf("music", "singer", "kumzits", "simcha", "concert")

/** A creator's music-filtered YidStatus feed: creators (with statuses in the window) + their posts. */
data class YidFeed(
    val creators: List<StatusCreator>,
    val postsByCreator: Map<String, List<StatusPost>>,
)

/**
 * Fetch the YidStatus feed and reduce it to music creators + their statuses (oldest-first per creator).
 * Blocking; run off the main thread. Throws on network/HTTP error (callers are fail-soft).
 */
fun fetchYidStatusFeed(days: Int = YID_FEED_DAYS): YidFeed {
    val root = JSONObject(postFeed("""{"days":$days,"since":null}"""))
    val musicCreators = parseYidCreators(root.optJSONArray("influencers") ?: JSONArray())
    val musicIds = musicCreators.associateBy { it.id }
    val byCreator = parseYidStatuses(root.optJSONArray("statuses") ?: JSONArray(), musicIds.keys)
    // Keep only creators that actually have a status in the window; attach their status ids as the ring.
    val creators = musicCreators.mapNotNull { c ->
        val posts = byCreator[c.id]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        c.copy(recentPostIds = posts.map { it.id }, recentPostKinds = posts.map { it.kind })
    }
    return YidFeed(creators, byCreator)
}

// --- Parsing ---

internal fun parseYidCreators(arr: JSONArray): List<StatusCreator> =
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        // Exclude hidden/paused/unlisted creators and anything outside the music categories.
        if (o.optBoolean("paused") || o.optBoolean("unlisted") || o.optBoolean("review_hidden")) return@mapNotNull null
        if (!isMusicCreator(o)) return@mapNotNull null
        StatusCreator(
            id = o.getString("id"),
            slug = o.optStringOrNull("slug") ?: o.getString("id"),
            displayName = o.optStringOrNull("name") ?: "",
            avatarPath = o.optStringOrNull("avatar_url"), // already a full URL
            source = StatusSource.YID_STATUS,
        )
    }

/**
 * Group music-creator statuses by creator, oldest-first. Drops ads, audio (the viewer renders only
 * video/image/text), and statuses whose creator was filtered out.
 */
internal fun parseYidStatuses(arr: JSONArray, musicIds: Set<String>): Map<String, List<StatusPost>> {
    val byCreator = mutableMapOf<String, MutableList<StatusPost>>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val creatorId = o.optStringOrNull("influencer_id") ?: continue
        if (creatorId !in musicIds) continue
        if (o.optBoolean("is_ad")) continue
        val kind = o.optStringOrNull("type") ?: continue
        if (kind !in ALLOWED_YID_KINDS) continue // skip audio / unknown
        val isText = kind == "text"
        val caption = o.optStringOrNull("caption")
        byCreator.getOrPut(creatorId) { mutableListOf() }.add(
            StatusPost(
                id = o.getString("id"),
                kind = kind,
                mediaPath = o.optStringOrNull("media_url"),   // full URL
                thumbPath = o.optStringOrNull("poster_url"),  // full URL
                caption = if (isText) null else caption,
                textBody = if (isText) caption else null,
                textBgColor = o.optStringOrNull("background_color"),
                linkUrl = o.optStringOrNull("link_title"),
                durationSeconds = if (o.isNull("duration_seconds")) null else o.optInt("duration_seconds"),
                postedAt = o.optStringOrNull("timestamp") ?: "",
                source = StatusSource.YID_STATUS,
            )
        )
    }
    // Oldest-first per creator (the ring + resume logic assume ascending time).
    byCreator.values.forEach { it.sortBy { p -> p.postedAt } }
    return byCreator
}

private val ALLOWED_YID_KINDS = setOf("video", "image", "text")

private fun isMusicCreator(o: JSONObject): Boolean {
    val cats = buildList {
        o.optStringOrNull("category")?.let { add(it) }
        o.optJSONArray("categories")?.let { a -> for (i in 0 until a.length()) add(a.optString(i)) }
    }
    return cats.any { c -> YID_MUSIC_KEYWORDS.any { kw -> c.lowercase().contains(kw) } }
}

// --- HTTP ---

// MUST be OkHttp, not HttpURLConnection: `Origin` is a JDK/Android "restricted header" that
// HttpURLConnection.setRequestProperty SILENTLY DROPS, and the feed 403s without it. OkHttp sends it.
private val yidHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
}
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private fun postFeed(body: String): String {
    val request = Request.Builder()
        .url(YID_FEED_URL)
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .header("apikey", YID_KEY)
        .header("Origin", YID_ORIGIN) // required; see docs/status/yidstatus-api.md
        .build()
    yidHttpClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("YidStatus feed HTTP ${resp.code}")
        return resp.body?.string() ?: throw IOException("YidStatus feed empty body")
    }
}
