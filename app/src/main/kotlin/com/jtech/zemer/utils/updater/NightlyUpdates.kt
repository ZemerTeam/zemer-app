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
 * document describes the current build (commit, CI run number, version, size, SHA-256) and carries
 * a SHA-pinned download URL, so the build the app announces and the build it installs are the same
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
        /** Null when the mirror could not carry the version; the coming-soon rule is then skipped. */
        val version: BuildVersion?,
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
        val versionCode = o["versionCode"]?.jsonPrimitive?.intOrNull
        val versionName = o["versionName"]?.jsonPrimitive?.contentOrNull
        NightlyBuild(
            sha = sha,
            runNumber = runNumber,
            commitMessage = o["commitMessage"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            version = if (versionCode != null && versionName != null) BuildVersion(versionCode, versionName) else null,
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

    data class BuildVersion(val versionCode: Int, val versionName: String)

    /** True when [candidate] is a strictly higher dotted-numeric version than [base] (e.g. 35 > 34). */
    fun isNameGreater(candidate: String, base: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = base.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * A new nightly whose versionCode AND versionName are both above the installed build is a
     * release being prepared - nightly users should wait for the stable release rather than jump
     * to what is effectively the release candidate.
     */
    fun isReleaseComingSoon(installedCode: Int, installedName: String, nightly: BuildVersion): Boolean =
        nightly.versionCode > installedCode && isNameGreater(nightly.versionName, installedName)
}
