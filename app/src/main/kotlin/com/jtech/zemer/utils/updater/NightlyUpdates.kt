package com.jtech.zemer.utils.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The opt-in nightly update channel. Every push to `main` produces a signed release APK via the
 * release-build workflow, and the Zemer nightly mirror (`nightly.zemer.io`) publishes it: ONE JSON
 * document describes the current build (commit, CI run number, size, SHA-256) and carries a
 * SHA-pinned download URL, so the build the app announces and the build it installs are the same
 * object by construction. Nightlies all share the stable versionName, so "is a newer nightly
 * available" is a commit-SHA + run-number comparison against BuildConfig, never a version one.
 * Pure logic (parsing, the update rule, labels, download verification) lives here so it is
 * unit-testable; the network/UI flow stays in UpdateChecker.
 */
object NightlyUpdates {
    /** The current nightly: the only input to "is there an update" and where to download it. */
    const val API_URL = "https://nightly.zemer.io/api"

    private const val CHANGELOG_URL = "https://nightly.zemer.io/changelog"

    /** Every build between [installedSha] and the current one, newest first (the release notes). */
    fun changelogUrl(installedSha: String): String = "$CHANGELOG_URL?since=$installedSha"

    /** How many builds' notes the dialog stacks at most: a long-skipped user gets the newest. */
    const val MAX_CHANGELOG_ENTRIES = 10

    private val SHA_REGEX = Regex("^[0-9a-f]{40}$")
    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

    data class NightlyBuild(
        val sha: String,
        val runNumber: Int,
        val commitMessage: String?,
        val size: Long,
        val sha256: String,
        val downloadUrl: String,
    )

    /** Parses the mirror's `/api` document; null unless it is a complete, sane build record. */
    fun parseBuild(json: String): NightlyBuild? = runCatching {
        val o = Json.parseToJsonElement(json).jsonObject
        val sha = o["sha"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?.takeIf { SHA_REGEX.matches(it) } ?: return null
        val runNumber = o["runNumber"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
        val downloadUrl = o["downloadUrl"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("https://") } ?: return null
        val size = o["size"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
        val sha256 = o["sha256"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?.takeIf { SHA256_REGEX.matches(it) } ?: return null
        NightlyBuild(
            sha = sha,
            runNumber = runNumber,
            commitMessage = o["commitMessage"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            size = size,
            sha256 = sha256,
            downloadUrl = downloadUrl,
        )
    }.getOrNull()

    /**
     * A nightly is an update when its commit differs from the installed one AND it is a later CI
     * run. The run-number half is the app's own guard against a mirror announcing an OLDER build
     * (the bug behind the 36 -> 34 Room crash): run numbers are monotonic per workflow, so a lower
     * one can never be an upgrade. A build with no run number baked in (built outside CI,
     * [installedRunNumber] 0) keeps the SHA-only rule; an unknown installed SHA (built without git)
     * always counts as outdated, as before.
     */
    fun isUpdateAvailable(installedSha: String, installedRunNumber: Int, latest: NightlyBuild): Boolean {
        if (latest.sha.equals(installedSha, ignoreCase = true)) return false
        return installedRunNumber <= 0 || latest.runNumber > installedRunNumber
    }

    /**
     * [isUpdateAvailable] as a gate for the download, which re-reads `/api` after the check: the
     * mirror may have moved on to a NEWER build (still an upgrade, accepted), but a build that is
     * not an upgrade over the installed one is never downloaded, whatever the earlier check saw.
     * Throws a plain IllegalStateException (an "unknown" download failure, not a corrupt artifact).
     */
    fun requireUpdate(installedSha: String, installedRunNumber: Int, build: NightlyBuild): NightlyBuild {
        check(isUpdateAvailable(installedSha, installedRunNumber, build)) {
            "Nightly channel no longer offers an update over the installed build (current ${versionLabel(build)})"
        }
        return build
    }

    fun versionLabel(build: NightlyBuild): String = "nightly #${build.runNumber} (${build.sha.take(7)})"

    fun currentVersionLabel(versionName: String, installedSha: String): String =
        if (installedSha.isBlank()) versionName else "$versionName (${installedSha.take(7)})"

    data class ChangelogEntry(val sha: String, val runNumber: Int, val commitMessage: String)

    /** Parses `/changelog`; null when the document carries no `entries` array. Newest first as served. */
    fun parseChangelog(json: String): List<ChangelogEntry>? = runCatching {
        val entries = Json.parseToJsonElement(json).jsonObject["entries"]?.jsonArray ?: return null
        entries.mapNotNull { element ->
            val o = element.jsonObject
            val sha = o["sha"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?.takeIf { SHA_REGEX.matches(it) } ?: return@mapNotNull null
            val runNumber = o["runNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val message = o["commitMessage"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChangelogEntry(sha, runNumber, message)
        }
    }.getOrNull()

    /**
     * Stacked release notes for the update dialog: each build's commit message as its own heading
     * + body, newest first, capped at [MAX_CHANGELOG_ENTRIES]. Only builds NEWER than the installed
     * one ([installedRunNumber]; 0 = unknown, show the list as served): when the mirror does not
     * hold the installed build it answers with its whole history, and an older build's message must
     * never read as "what's new". Null when there is nothing to show (the caller falls back to the
     * current build's own message).
     */
    fun changelogMarkdown(entries: List<ChangelogEntry>, installedRunNumber: Int): String? =
        entries.filter { installedRunNumber <= 0 || it.runNumber > installedRunNumber }
            .sortedByDescending { it.runNumber }
            .take(MAX_CHANGELOG_ENTRIES)
            .mapNotNull { commitMessageMarkdown(it.commitMessage) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n")

    /**
     * Formats a commit message as release notes: the subject line becomes a heading and the body
     * follows as Markdown (hard-wrapped lines reflow). Null for a blank message.
     */
    fun commitMessageMarkdown(message: String): String? {
        val lines = message.trim().lines()
        val title = lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val body = lines.drop(1).joinToString("\n").trim()
        return if (body.isEmpty()) "### $title" else "### $title\n\n$body"
    }

    /**
     * The downloaded bytes must be exactly what `/api` described: same length and same SHA-256.
     * Anything else - a truncated body, a cache or proxy handing back a different file, a mirror
     * bug - is a corrupt artifact and is never offered for install.
     */
    fun verifyDownload(downloadedBytes: Long, downloadedSha256: String, expected: NightlyBuild) {
        if (downloadedBytes != expected.size) {
            throw CorruptUpdateArtifactException(
                "Nightly size mismatch: got $downloadedBytes bytes, expected ${expected.size}",
            )
        }
        if (!downloadedSha256.equals(expected.sha256, ignoreCase = true)) {
            throw CorruptUpdateArtifactException(
                "Nightly SHA-256 mismatch: got $downloadedSha256, expected ${expected.sha256}",
            )
        }
    }

    /** Lowercase hex of a digest, the form `/api` uses. */
    fun hex(digest: ByteArray): String = digest.joinToString("") { "%02x".format(it) }
}
