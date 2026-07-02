package com.jtech.zemer.utils.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * The opt-in nightly update channel: every push to `main` produces a signed release APK via the
 * release-build workflow, and nightly.link mirrors the latest artifact. Nightlies all share the
 * stable versionName, so "is a newer nightly available" is a commit-SHA comparison against
 * BuildConfig.COMMIT_HASH — never a version comparison. Pure logic (parsing, comparison, labels,
 * zip extraction) lives here so it is unit-testable; the network/UI flow stays in UpdateChecker.
 */
object NightlyUpdates {
    /** Latest successful release-build run on main; its head SHA identifies the latest nightly. */
    const val RUNS_URL =
        "https://api.github.com/repos/ZemerTeam/zemer-app/actions/workflows/release-build.yml/runs?branch=main&status=success&per_page=1"

    /** nightly.link mirror of the latest release-apk artifact — a zip wrapping the APK. */
    const val DOWNLOAD_URL =
        "https://nightly.link/ZemerTeam/zemer-app/workflows/release-build/main/release-apk.zip"

    data class NightlyRun(
        val headSha: String,
        val runNumber: Int,
        val commitTitle: String?,
    )

    /** Parses the GitHub Actions runs response; null when it holds no usable run. */
    fun parseLatestRun(json: String): NightlyRun? = runCatching {
        val run = Json.parseToJsonElement(json)
            .jsonObject["workflow_runs"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return null
        NightlyRun(
            headSha = run["head_sha"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return null,
            runNumber = run["run_number"]?.jsonPrimitive?.int ?: return null,
            // head_commit carries the full commit message; display_title is a truncated fallback.
            commitTitle = run["head_commit"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: run["display_title"]?.jsonPrimitive?.content,
        )
    }.getOrNull()

    /** A build with an unknown (blank) SHA — e.g. built without git — always counts as outdated. */
    fun isUpdateAvailable(installedSha: String, latestSha: String): Boolean =
        !latestSha.equals(installedSha, ignoreCase = true)

    fun versionLabel(run: NightlyRun): String = "nightly #${run.runNumber} (${run.headSha.take(7)})"

    fun currentVersionLabel(versionName: String, installedSha: String): String =
        if (installedSha.isBlank()) versionName else "$versionName (${installedSha.take(7)})"

    /** Raw build.gradle.kts at [sha] — the authoritative version of the nightly at that commit. */
    fun buildGradleUrl(sha: String): String =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-app/$sha/app/build.gradle.kts"

    data class BuildVersion(val versionCode: Int, val versionName: String)

    private val VERSION_CODE_REGEX = Regex("""versionCode\s*=\s*(\d+)""")
    private val VERSION_NAME_REGEX = Regex("""versionName\s*=\s*"([^"]+)"""")

    /** Parses versionCode/versionName out of a build.gradle.kts; null if either is absent. */
    fun parseBuildVersion(buildGradle: String): BuildVersion? {
        val code = VERSION_CODE_REGEX.find(buildGradle)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = VERSION_NAME_REGEX.find(buildGradle)?.groupValues?.get(1) ?: return null
        return BuildVersion(code, name)
    }

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
     * release being prepared — nightly users should wait for the stable release rather than jump
     * to what is effectively the release candidate.
     */
    fun isReleaseComingSoon(installedCode: Int, installedName: String, nightly: BuildVersion): Boolean =
        nightly.versionCode > installedCode && isNameGreater(nightly.versionName, installedName)

    /**
     * Extracts the single APK inside a nightly.link artifact zip into [destination]. Throws when
     * the archive holds no APK so a broken artifact surfaces as a download error, not a dead file.
     */
    fun extractApk(zip: File, destination: File) {
        ZipFile(zip).use { archive ->
            val entry = archive.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith(".apk") }
                ?: error("No APK found in the nightly archive")
            archive.getInputStream(entry).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
