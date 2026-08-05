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
 * The base URL, the public anon JWT (client-safe, read-only, NOT a secret), and which category keywords
 * count as "music" are supplied by the caller from [StatusSourcesConfig] (server-driven; the mirror is
 * the single source of truth). Two YidStatus-specific PROTOCOL details stay baked in here,
 * because they are welded to this platform rather than tunable:
 *  - The `/functions/v1/feed` edge path ([yidFeedUrl]).
 *  - The `Origin: https://yidstatus.com` header the feed requires (a 403 without it). It is a JDK/Android
 *    "restricted header" that HttpURLConnection silently drops, which is why this client uses OkHttp.
 */
private const val YID_ORIGIN = "https://yidstatus.com"
private fun yidFeedUrl(base: String) = "${base.trimEnd('/')}/functions/v1/feed"

// Rolling window (days). Kept at 1 deliberately: the feed is GLOBAL (all categories) and costs ~3.35 MB
// per day before filtering, and the edge function hard-errors past ~15 days (WORKER_RESOURCE_LIMIT).
// YidStatus exposes NO per-creator history endpoint, so a deep "jump to date" like JewishStatus is not
// achievable from its public API - one day already spans ~2 calendar dates, which is the practical cap.
private const val YID_FEED_DAYS = 1

/** A creator's music-filtered YidStatus feed: creators (with statuses in the window) + their posts. */
data class YidFeed(
    val creators: List<StatusCreator>,
    val postsByCreator: Map<String, List<StatusPost>>,
)

/**
 * Fetch the YidStatus feed and reduce it to music creators + their statuses (oldest-first per creator).
 * Blocking; run off the main thread. Throws on network/HTTP error (callers are fail-soft).
 */
fun fetchYidStatusFeed(base: String, key: String, keywords: List<String>, days: Int = YID_FEED_DAYS): YidFeed {
    val root = JSONObject(postFeed(yidFeedUrl(base), key, """{"days":$days,"since":null}"""))
    val musicCreators = parseYidCreators(root.optJSONArray("influencers") ?: JSONArray(), keywords)
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

internal fun parseYidCreators(arr: JSONArray, keywords: List<String>): List<StatusCreator> =
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        // Exclude hidden/paused/unlisted creators and anything outside the music categories.
        if (o.optBoolean("paused") || o.optBoolean("unlisted") || o.optBoolean("review_hidden")) return@mapNotNull null
        if (!isMusicCreator(o, keywords)) return@mapNotNull null
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

private fun isMusicCreator(o: JSONObject, keywords: List<String>): Boolean {
    val cats = buildList {
        o.optStringOrNull("category")?.let { add(it) }
        o.optJSONArray("categories")?.let { a -> for (i in 0 until a.length()) add(a.optString(i)) }
    }
    return cats.any { c -> keywords.any { kw -> c.lowercase().contains(kw.lowercase()) } }
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

private fun postFeed(feedUrl: String, key: String, body: String): String {
    val request = Request.Builder()
        .url(feedUrl)
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .header("apikey", key)
        .header("Origin", YID_ORIGIN) // required; see docs/status/yidstatus-api.md
        .build()
    yidHttpClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("YidStatus feed HTTP ${resp.code}")
        return resp.body?.string() ?: throw IOException("YidStatus feed empty body")
    }
}
