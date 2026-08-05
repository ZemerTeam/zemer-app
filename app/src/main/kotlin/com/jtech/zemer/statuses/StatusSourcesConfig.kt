package com.jtech.zemer.statuses

import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-driven config for the Music Status sources: which categories / keywords each third-party
 * platform is filtered down to. Synced (version-gated) from the PRIVATE content mirror
 * (`content.zemer.io/status-sources`); the status DATA still comes straight from JewishStatus /
 * YidStatus - only the *filter* config is centralized so it can change without an APK release.
 * Contract: `ZemerTeam/handoff-docs/zemer-status-sources-config-request.md`.
 *
 * SERVER-ONLY: there is NO baked-in fallback config - the mirror is the single source of truth. The app
 * caches the last-good config (persisted to DataStore, reloaded at startup) so it survives restarts /
 * offline. Until a device's first successful sync it has NO config and the feature is simply hidden
 * (fail-soft) - the same graceful state as the third-party sources being down. Nothing wrong is ever
 * shown; the worst case is an absent row.
 *
 * Fail-soft rules (agreed with the mirror maintainer, and the reason parse returns config-or-null):
 *  - CANNOT obtain a VALID config (unreachable, 503, non-JSON, `providers` not an array) -> parse returns
 *    null and the caller KEEPS its last-good config (or stays hidden if it has none). A transient failure
 *    never blanks a working feature.
 *  - A VALID config is HONORED as-is, even when its usable set is empty (every provider disabled /
 *    unknown-type / empty-filter). That is an intentional dark - the row shows nothing.
 *  - An unknown `type`, a disabled provider, or an enabled provider with an empty filter list is SKIPPED
 *    non-fatally; the others still load. A new-`type` descriptor can be added to the config before its
 *    handler ships; old installs ignore it while still running the types they know.
 *
 * A descriptor carries only the tunable DATA (baseUrl, apiKey, category ids / keywords, enabled).
 * Protocol details welded to a handler - the JewishStatus R2 CDN host, the YidStatus feed edge path and
 * its required `Origin` header - stay baked into the handler for that `type`, never in the config.
 */

/** The handler family a provider descriptor maps to. Unknown slugs resolve to null (-> skipped). */
enum class StatusProviderType(val slug: String) {
    /** JewishStatus shape: Supabase PostgREST, per-category browse, per-creator post fetch. */
    SUPABASE_CATEGORY("supabase-category"),

    /** YidStatus shape: one global feed for a rolling window, client-side keyword filter. */
    KEYWORD_FEED("keyword-feed");

    companion object {
        fun fromSlug(slug: String?): StatusProviderType? = entries.firstOrNull { it.slug == slug }
    }
}

/**
 * One status source. [categoryIds] is used by [StatusProviderType.SUPABASE_CATEGORY] and [musicKeywords]
 * by [StatusProviderType.KEYWORD_FEED]; [filterList] resolves whichever applies to this provider's type.
 */
data class StatusProvider(
    val id: String,
    val type: StatusProviderType,
    val baseUrl: String,
    val apiKey: String,
    val categoryIds: List<String> = emptyList(),
    val musicKeywords: List<String> = emptyList(),
    val enabled: Boolean = true,
) {
    /** The per-type filter list an enabled provider needs (non-empty) to fetch anything. */
    val filterList: List<String>
        get() = when (type) {
            StatusProviderType.SUPABASE_CATEGORY -> categoryIds
            StatusProviderType.KEYWORD_FEED -> musicKeywords
        }
}

/**
 * A resolved set of status sources. [providers] holds ONLY usable descriptors (enabled, known type,
 * non-empty filter list); an empty list is a legitimate "all sources darked" state (honored, not fallback).
 */
data class StatusSourcesConfig(
    val version: Long,
    val providers: List<StatusProvider>,
) {
    fun providersOfType(type: StatusProviderType): List<StatusProvider> = providers.filter { it.type == type }
}

/**
 * Parse the mirror's `/status-sources` JSON.
 *
 * Returns null ONLY when a valid config cannot be obtained - blank, non-JSON, or no `providers` array. The
 * caller then keeps its last-good config (or stays hidden if it has none); a transient failure never
 * blanks a working feature.
 *
 * Otherwise returns the config, HONORED as-is with its usable providers - even when that set is empty
 * (every provider disabled / unknown-type / empty-filter), which is an intentional dark (the row shows
 * nothing). Usable = enabled, known type, and a non-empty per-type filter list; anything else is skipped
 * non-fatally so the remaining providers still load.
 */
fun parseStatusSourcesConfig(text: String?): StatusSourcesConfig? {
    if (text.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(text)
        val arr = root.optJSONArray("providers") ?: return null // no valid config -> keep last-good
        val version = root.optLong("version", 0L)

        val usable = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!o.optBoolean("enabled", true)) return@mapNotNull null // darked source: skip
            val type = StatusProviderType.fromSlug(o.optStringOrNull("type")) ?: return@mapNotNull null // unknown -> skip
            val id = o.optStringOrNull("id") ?: return@mapNotNull null
            // Normalized once here so EVERY handler can safely concatenate "$baseUrl/path" - the config is
            // hand-authored and a trailing slash must not silently 404 a whole provider family.
            val baseUrl = o.optStringOrNull("baseUrl")?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val apiKey = o.optStringOrNull("apiKey") ?: return@mapNotNull null
            StatusProvider(
                id = id,
                type = type,
                baseUrl = baseUrl,
                apiKey = apiKey,
                categoryIds = o.optJSONArray("categoryIds").toStringList(),
                musicKeywords = o.optJSONArray("musicKeywords").toStringList(),
                enabled = true,
            ).takeIf { it.filterList.isNotEmpty() } // an enabled provider with no filter is misconfigured -> skip
        }
        StatusSourcesConfig(version, usable)
    }.getOrNull()
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotEmpty) }
}

/**
 * Process-wide holder for the active status-sources config. Mirrors [com.jtech.zemer.utils.BlockedIdsCache]:
 * a validated mirror config is installed atomically via [update]; [current] returns it, or an EMPTY config
 * (no providers -> feature hidden) until the first successful sync. There is no baked-in fallback - the
 * mirror is the single source of truth. A failed/invalid sync never calls [update], so the last-good
 * config stays live.
 */
object StatusSourcesCache {
    private val EMPTY = StatusSourcesConfig(version = -1L, providers = emptyList())

    @Volatile
    private var installed: StatusSourcesConfig? = null

    /**
     * Install a validated mirror config. Never pass null (null from the parser means "keep last-good").
     * Never rolls back: an older-versioned config is ignored, so the startup restore of the persisted
     * snapshot can safely race a concurrent sync that already installed something newer.
     */
    @Synchronized
    fun update(config: StatusSourcesConfig) {
        val current = installed
        if (current != null && config.version < current.version) return
        installed = config
    }

    /** The config to use now: the installed mirror config, else an empty config (feature hidden). */
    fun current(): StatusSourcesConfig = installed ?: EMPTY

    /** The version currently installed from the mirror, or -1 if none has synced yet. */
    val syncedVersion: Long
        get() = installed?.version ?: -1L

    /** Test-only: clear the installed config (the object is process-wide; JVM tests need isolation). */
    internal fun resetForTest() {
        installed = null
    }
}
