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

/** Which third-party platform a creator/status came from. Docs: docs/status/. */
enum class StatusSource { JEWISH_STATUS, YID_STATUS }

data class StatusCreator(
    val id: String,
    val slug: String,
    val displayName: String,
    val avatarPath: String?,
    // The recent status ids (`recent_post_ids` on JewishStatus; derived from the grouped feed on
    // YidStatus) — drives the segmented story ring (one segment each) and the WhatsApp "read" state
    // (newest seen => caught up), oldest-first so the newest is the LAST id.
    val recentPostIds: List<String> = emptyList(),
    // The kind of each recent status, aligned 1:1 with [recentPostIds], so the ring can respect the
    // hide-text/hide-image content filter. Empty (or a size mismatch) means "unknown" -> show all, so
    // nothing regresses when kinds could not be resolved.
    val recentPostKinds: List<String> = emptyList(),
    val source: StatusSource = StatusSource.JEWISH_STATUS,
)

data class StatusPost(
    val id: String,
    val kind: String,               // "video" | "image" | "text"
    val mediaPath: String?,         // relative (JewishStatus) OR a full https URL (YidStatus)
    val thumbPath: String?,         // relative (JewishStatus) OR a full https URL (YidStatus)
    val caption: String?,
    val textBody: String?,          // the body of a text-only status (text posts have no media/caption)
    val textBgColor: String?,       // optional "#RRGGBB" background for a text status
    val linkUrl: String?,
    val durationSeconds: Int?,      // actual video length; the site defaults images/text to 7s
    val postedAt: String,           // ISO-8601, e.g. "2026-08-01T19:32:52+00:00"
    val viewCount: Int = 0,
    val downloadCount: Int = 0,
    val source: StatusSource = StatusSource.JEWISH_STATUS,
)

/** The user's Music Status content filter (Settings -> Appearance): which status KINDS to hide. */
data class StatusContentFilter(
    val hideText: Boolean,
    val hideImage: Boolean,
)

/**
 * Drop the status kinds the user chose to hide. Text-only and still-image are the two optional filters;
 * a video always passes. A creator left with no visible posts is auto-skipped by the viewer's existing
 * empty-posts handling, so a fully-filtered creator simply advances to the next.
 */
fun List<StatusPost>.applyStatusFilter(filter: StatusContentFilter): List<StatusPost> =
    filter { (!filter.hideText || it.kind != "text") && (!filter.hideImage || it.kind != "image") }

/**
 * The recent status ids the user can actually VIEW under their content filter (hide-text/hide-image).
 * Drives the ring segment count and the read state, so hidden-kind statuses never show as ring segments.
 * When kinds are unknown (no [recentPostKinds], or a size mismatch), every id is returned - never hides
 * more than we can prove, so nothing regresses.
 */
fun StatusCreator.visibleRecentIds(filter: StatusContentFilter): List<String> {
    if (recentPostKinds.size != recentPostIds.size) return recentPostIds
    return recentPostIds.filterIndexed { i, _ ->
        val kind = recentPostKinds[i]
        (!filter.hideText || kind != "text") && (!filter.hideImage || kind != "image")
    }
}

/**
 * Whether the user is "caught up" on a creator (WhatsApp read state): their NEWEST VISIBLE status has
 * been viewed. `recent_post_ids` is oldest-first (verified against posted_at), so the newest is the LAST
 * id. Older days left unopened do NOT keep a creator marked unread - a user who watched today's auto-play
 * is caught up even if earlier days are still openable via the jump-to-date sheet. Empty => not caught up.
 */
fun StatusCreator.caughtUpOnLatest(seenPostIds: Set<String>, filter: StatusContentFilter): Boolean =
    visibleRecentIds(filter).lastOrNull()?.let { it in seenPostIds } ?: false

/**
 * Creators ordered for the Home row AND the See-all grid (one definition so the two can't drift):
 * creators the user is caught up on (newest VISIBLE status seen) sink to the end (WhatsApp), preserving
 * recency order within each group.
 */
fun List<StatusCreator>.sortedByUnseenFirst(
    seenPostIds: Set<String>,
    filter: StatusContentFilter,
): List<StatusCreator> = sortedBy { it.caughtUpOnLatest(seenPostIds, filter) }

/**
 * Merge two platforms' creators, dropping cross-platform DUPLICATES (the same real creator appears on
 * both with different ids). Matched by a normalized name (case/spacing/punctuation-insensitive). The
 * [primary] platform wins a tie - it is kept in full; a [secondary] creator is included only when its
 * name is not already present. The Home row is uniform over the result; the See-all groups by `source`.
 */
fun mergeStatusCreators(
    primary: List<StatusCreator>,
    secondary: List<StatusCreator>,
): List<StatusCreator> {
    val seenNames = HashSet<String>()
    val out = ArrayList<StatusCreator>(primary.size + secondary.size)
    for (c in primary) { seenNames.add(statusNameKey(c)); out.add(c) }
    for (c in secondary) if (seenNames.add(statusNameKey(c))) out.add(c)
    return out
}

// Normalized identity for cross-platform dedup: letters+digits only, lowercased. A blank name (should
// not happen) falls back to the id so distinct blank-name creators are never collapsed together.
private fun statusNameKey(c: StatusCreator): String =
    c.displayName.lowercase().filter { it.isLetterOrDigit() }.ifBlank { c.id }

// Resolve a stored path to a URL. YidStatus already stores FULL https URLs (passed through unchanged);
// JewishStatus stores relative paths that get the R2 CDN prefix. One helper so the viewer is source-agnostic.
fun statusAvatarUrl(path: String?): String? =
    path?.let { if (it.startsWith("http")) it else "$CDN/avatars/$it" }
fun statusMediaUrl(path: String?): String? =
    path?.let { if (it.startsWith("http")) it else "$CDN/status-media/$it" }

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
        // Drop creators with NO recent statuses (empty `recent_post_ids`, e.g. "shmusic"): they render an
        // empty ring and open to nothing. YidStatus already only keeps creators that have posts.
        .filter { it.recentPostIds.isNotEmpty() }

    // NOTE: we deliberately do NOT resolve each recent status's KIND here. `recent_post_ids` carry no
    // kind, and a batched id->kind fetch added a blocking round-trip that noticeably slowed the (formerly
    // instant) load. So JewishStatus creators leave `recentPostKinds` empty -> the ring shows the full
    // recent list (never over-hides). YidStatus gets kinds for free from its feed, so its ring still
    // respects the content filter. This keeps loading instant.
    base
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
