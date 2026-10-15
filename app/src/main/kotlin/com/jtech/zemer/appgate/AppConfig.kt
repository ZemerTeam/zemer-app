package com.jtech.zemer.appgate

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * The server-driven version gate, `GET content.zemer.io/app-config`:
 * `{ minVersion, latestVersion, graceUntil, message, updateUrl, updatedAt }`, every field nullable.
 * Pure + JVM-tested; the fetch/persist lives in [AppConfigStore], the UI in `ui/screens/AppVersionGate.kt`.
 *
 * FAIL OPEN: nothing here can turn a network or parse failure into a gate. Garbage parses to null (the
 * caller keeps its last good config, or has none), and a missing/ill-typed field is simply absent.
 */
data class AppConfig(
    val minVersion: Int? = null,
    val latestVersion: Int? = null,
    val graceUntil: Instant? = null,
    val message: String? = null,
    val updateUrl: String? = null,
)

/** What the app must do for this install. */
sealed interface AppGate {
    /** No gate: the normal app. */
    data object None : AppGate

    /** Below `latestVersion` while `graceUntil` is ahead: a dismissible "please update by" prompt. */
    data class Grace(val deadline: Instant, val message: String?, val updateUrl: String?) : AppGate

    /** Below `minVersion`: the full-screen, non-dismissible "update required" screen. */
    data class Blocked(val message: String?, val updateUrl: String?) : AppGate
}

/** Parse the `/app-config` body; null when it is not a JSON object at all (never a gate). */
fun parseAppConfig(text: String?): AppConfig? {
    if (text.isNullOrBlank()) return null
    val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
    return AppConfig(
        minVersion = o.intOrNull("minVersion"),
        latestVersion = o.intOrNull("latestVersion"),
        graceUntil = o.stringOrNull("graceUntil")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
        message = o.stringOrNull("message"),
        updateUrl = o.stringOrNull("updateUrl")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
    )
}

/** The gate for [versionCode] at [now]: blocked below `minVersion`, else grace below `latestVersion`. */
fun appGate(config: AppConfig?, versionCode: Int, now: Instant): AppGate {
    if (config == null) return AppGate.None
    if (config.minVersion != null && versionCode < config.minVersion) {
        return AppGate.Blocked(config.message, config.updateUrl)
    }
    val deadline = config.graceUntil
    if (config.latestVersion != null && versionCode < config.latestVersion && deadline != null && deadline.isAfter(now)) {
        return AppGate.Grace(deadline, config.message, config.updateUrl)
    }
    return AppGate.None
}

/** The grace prompt shows at most once per local day: due unless it already showed on [today]. */
fun gracePromptDue(lastShownDay: String?, today: String): Boolean = lastShownDay != today

// A real JSON number only (a string "39" or a fraction is ignored, not coerced).
private fun JSONObject.intOrNull(key: String): Int? =
    (opt(key) as? Number)?.takeIf { it.toDouble() == it.toInt().toDouble() }?.toInt()

private fun JSONObject.stringOrNull(key: String): String? =
    (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
