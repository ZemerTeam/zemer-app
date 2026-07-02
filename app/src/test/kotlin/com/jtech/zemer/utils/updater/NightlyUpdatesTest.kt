package com.jtech.zemer.utils.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure coverage of the nightly update channel: GitHub runs parsing, the SHA-based "is an update
 * available" rule (nightlies share the stable versionName, so versions can never be compared),
 * and APK extraction from the nightly.link artifact zip.
 */
class NightlyUpdatesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Trimmed from a real https://api.github.com/.../release-build.yml/runs response.
    private val runsJson = """
        {
          "total_count": 103,
          "workflow_runs": [
            {
              "id": 28590629343,
              "name": "Android Release Build",
              "head_branch": "main",
              "head_sha": "5226868eaf3e6ae9e0e022c5b80c4d1b020e28e4",
              "display_title": "fix(search): route Zemer-search albums through the server /album endp…",
              "run_number": 549,
              "status": "completed",
              "conclusion": "success",
              "head_commit": {
                "id": "5226868eaf3e6ae9e0e022c5b80c4d1b020e28e4",
                "message": "fix(search): route Zemer-search albums through the server /album endpoint (#171)"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the latest run from a real-shaped response`() {
        val run = NightlyUpdates.parseLatestRun(runsJson)!!

        assertEquals("5226868eaf3e6ae9e0e022c5b80c4d1b020e28e4", run.headSha)
        assertEquals(549, run.runNumber)
        // The full head_commit message is shown to the user, not GitHub's truncated display_title.
        assertEquals(
            "fix(search): route Zemer-search albums through the server /album endpoint (#171)",
            run.commitTitle,
        )
    }

    @Test
    fun `falls back to display_title when head_commit is absent`() {
        val run = NightlyUpdates.parseLatestRun(
            """{"workflow_runs":[{"head_sha":"abc123","run_number":42,"display_title":"truncated title…"}]}"""
        )!!

        assertEquals("truncated title…", run.commitTitle)
    }

    @Test
    fun `unusable responses parse to null instead of throwing`() {
        assertNull(NightlyUpdates.parseLatestRun("""{"total_count":0,"workflow_runs":[]}"""))
        assertNull(NightlyUpdates.parseLatestRun("""{"message":"API rate limit exceeded"}"""))
        assertNull(NightlyUpdates.parseLatestRun("""{"workflow_runs":[{"run_number":549}]}""")) // no head_sha
        assertNull(NightlyUpdates.parseLatestRun("""{"workflow_runs":[{"head_sha":"abc"}]}""")) // no run_number
        assertNull(NightlyUpdates.parseLatestRun("not json at all"))
    }

    @Test
    fun `update is available only when the latest SHA differs from the installed one`() {
        val sha = "5226868eaf3e6ae9e0e022c5b80c4d1b020e28e4"

        assertFalse(NightlyUpdates.isUpdateAvailable(sha, sha))
        assertFalse(NightlyUpdates.isUpdateAvailable(sha.uppercase(), sha)) // case never matters
        assertTrue(NightlyUpdates.isUpdateAvailable("0000000aaf3e6ae9e0e022c5b80c4d1b020e28e4", sha))
        // A build without a baked-in SHA (built outside git) always counts as outdated.
        assertTrue(NightlyUpdates.isUpdateAvailable("", sha))
    }

    @Test
    fun `version labels use the run number and short SHAs`() {
        val run = NightlyUpdates.parseLatestRun(runsJson)!!

        assertEquals("nightly #549 (5226868)", NightlyUpdates.versionLabel(run))
        assertEquals("34 (5226868)", NightlyUpdates.currentVersionLabel("34", run.headSha))
        assertEquals("34", NightlyUpdates.currentVersionLabel("34", "")) // no SHA -> plain version
    }

    @Test
    fun `extracts the APK entry from the artifact zip`() {
        val apkBytes = ByteArray(4096) { (it % 251).toByte() }
        val zip = tempFolder.newFile("release-apk.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("output-metadata.json"))
            out.write("{}".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("app-release.apk"))
            out.write(apkBytes)
            out.closeEntry()
        }
        val destination = tempFolder.newFile("zemer-update.apk")

        NightlyUpdates.extractApk(zip, destination)

        assertTrue(apkBytes.contentEquals(destination.readBytes()))
    }

    @Test
    fun `a zip without an APK fails loudly instead of producing a dead file`() {
        val zip = tempFolder.newFile("broken.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("readme.txt"))
            out.write("no apk here".toByteArray())
            out.closeEntry()
        }
        val destination = tempFolder.newFile("zemer-update.apk")

        val result = runCatching { NightlyUpdates.extractApk(zip, destination) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `parses versionCode and versionName from a build gradle`() {
        val gradle = """
            defaultConfig {
                applicationId = "com.jtech.zemer"
                versionCode = 35
                versionName = "35"
                buildConfigField("String", "ARCHITECTURE", "\"universal\"")
            }
        """.trimIndent()

        val version = NightlyUpdates.parseBuildVersion(gradle)!!
        assertEquals(35, version.versionCode)
        assertEquals("35", version.versionName)
    }

    @Test
    fun `parseBuildVersion returns null when a field is missing`() {
        assertNull(NightlyUpdates.parseBuildVersion("""versionName = "35""""))     // no versionCode
        assertNull(NightlyUpdates.parseBuildVersion("versionCode = 35"))           // no versionName
        assertNull(NightlyUpdates.parseBuildVersion("nothing here"))
    }

    @Test
    fun `isNameGreater compares dotted-numeric versions`() {
        assertTrue(NightlyUpdates.isNameGreater("35", "34"))
        assertTrue(NightlyUpdates.isNameGreater("34.1", "34"))
        assertFalse(NightlyUpdates.isNameGreater("34", "34"))
        assertFalse(NightlyUpdates.isNameGreater("34", "35"))
    }

    @Test
    fun `release is coming soon only when both code and name are higher`() {
        // Installed build is code 34 / name "34".
        assertTrue(
            NightlyUpdates.isReleaseComingSoon(34, "34", NightlyUpdates.BuildVersion(35, "35")),
        )
        // Same version (a normal same-release nightly) => offer it, not "coming soon".
        assertFalse(
            NightlyUpdates.isReleaseComingSoon(34, "34", NightlyUpdates.BuildVersion(34, "34")),
        )
        // Only one of the two higher => not a release bump; still offer it.
        assertFalse(
            NightlyUpdates.isReleaseComingSoon(34, "34", NightlyUpdates.BuildVersion(35, "34")),
        )
        assertFalse(
            NightlyUpdates.isReleaseComingSoon(34, "34", NightlyUpdates.BuildVersion(34, "35")),
        )
    }

    @Test
    fun `buildGradleUrl points at the raw file for the commit`() {
        assertEquals(
            "https://raw.githubusercontent.com/ZemerTeam/zemer-app/abc123/app/build.gradle.kts",
            NightlyUpdates.buildGradleUrl("abc123"),
        )
    }
}
