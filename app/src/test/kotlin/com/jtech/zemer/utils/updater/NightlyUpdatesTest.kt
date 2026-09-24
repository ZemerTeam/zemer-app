package com.jtech.zemer.utils.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Pure coverage of the nightly update channel: the mirror's `/api` + `/changelog` parsing, the
 * SHA + run-number "is an update available" rule (nightlies share the stable versionName, so
 * versions can never be compared; a lower run number can never be an upgrade), the labels, and
 * the size + SHA-256 verification of a downloaded build.
 */
class NightlyUpdatesTest {

    // Trimmed from a real https://nightly.zemer.io/api response (2026-09-24).
    private val apiJson = """
        {
          "sha": "4a3d242079f43d96ad504683128d425bd583ee3a",
          "shortSha": "4a3d242",
          "runId": 35876591976,
          "runNumber": 1247,
          "commitMessage": "fix(home): show Latest Releases when the whitelist loads late (#544)\n\nThe releases feed was filtered once at load.",
          "builtAt": "2026-09-23T14:59:11Z",
          "versionCode": 38,
          "versionName": "38",
          "size": 12276456,
          "sha256": "8f2b84306a0d36901677ffbe024416481ad8d9bafc5692b1ed71253d2766c14d",
          "downloadUrl": "https://nightly.zemer.io/download/4a3d242079f43d96ad504683128d425bd583ee3a.apk"
        }
    """.trimIndent()

    private val build get() = NightlyUpdates.parseBuild(apiJson)!!

    /** The fixture minus one property, rebuilt from the parsed object so it stays valid JSON. */
    private fun apiJsonWithout(field: String): String {
        val reduced = JsonObject(Json.parseToJsonElement(apiJson).jsonObject.filterKeys { it != field }).toString()
        assertFalse("$field still present", Json.parseToJsonElement(reduced).jsonObject.containsKey(field))
        return reduced
    }

    @Test
    fun `parses the current build from a real-shaped response`() {
        val b = build

        assertEquals("4a3d242079f43d96ad504683128d425bd583ee3a", b.sha)
        assertEquals(1247, b.runNumber)
        assertEquals(12276456L, b.size)
        assertEquals("8f2b84306a0d36901677ffbe024416481ad8d9bafc5692b1ed71253d2766c14d", b.sha256)
        assertEquals("https://nightly.zemer.io/download/4a3d242079f43d96ad504683128d425bd583ee3a.apk", b.downloadUrl)
        // The full commit message (subject + body) is the release-notes source.
        assertEquals(
            "fix(home): show Latest Releases when the whitelist loads late (#544)\n\nThe releases feed was filtered once at load.",
            b.commitMessage,
        )
    }

    @Test
    fun `a record missing any download-critical field parses to null instead of throwing`() {
        for (field in listOf("sha", "runNumber", "downloadUrl", "size", "sha256")) {
            assertNull("without $field", NightlyUpdates.parseBuild(apiJsonWithout(field)))
        }
        assertNull(NightlyUpdates.parseBuild(apiJson.replace("4a3d242079f43d96ad504683128d425bd583ee3a\",\n  \"shortSha", "4a3d242\",\n  \"shortSha"))) // short sha
        assertNull(NightlyUpdates.parseBuild(apiJson.replace("https://nightly.zemer.io/download", "http://nightly.zemer.io/download")))
        assertNull(NightlyUpdates.parseBuild(apiJson.replace("\"size\": 12276456", "\"size\": 0")))
        assertNull(NightlyUpdates.parseBuild(apiJson.replace("\"sha256\": \"8f2b8430", "\"sha256\": \"zz2b8430")))
        assertNull(NightlyUpdates.parseBuild("""{"error":"no nightly ingested yet"}"""))
        assertNull(NightlyUpdates.parseBuild("not json at all"))
    }

    @Test
    fun `the commit message is optional`() {
        val noMessage = NightlyUpdates.parseBuild(apiJsonWithout("commitMessage"))!!
        assertNull(noMessage.commitMessage)
        assertEquals(1247, noMessage.runNumber)
    }

    @Test
    fun `update is available only for a different commit from a LATER run`() {
        val b = build // #1247

        assertFalse(NightlyUpdates.isUpdateAvailable(b.sha, 1247, b))
        assertFalse(NightlyUpdates.isUpdateAvailable(b.sha.uppercase(), 1200, b)) // case never matters
        assertTrue(NightlyUpdates.isUpdateAvailable("0000000000000000000000000000000000000000", 1242, b))
        // The guard: an announced build from an OLDER run is never an update, whatever its SHA.
        assertFalse(NightlyUpdates.isUpdateAvailable("0000000000000000000000000000000000000000", 1300, b))
        assertFalse(NightlyUpdates.isUpdateAvailable("0000000000000000000000000000000000000000", 1247, b))
        // No run number baked in (built outside CI): the SHA-only rule.
        assertTrue(NightlyUpdates.isUpdateAvailable("0000000000000000000000000000000000000000", 0, b))
        // No SHA baked in (built without git) always counts as outdated.
        assertTrue(NightlyUpdates.isUpdateAvailable("", 0, b))
    }

    @Test
    fun `the download gate accepts a newer build and refuses anything else`() {
        val b = build // #1247

        // Still an upgrade (a newer build landed after the check is fine too): passes through.
        assertSame(b, NightlyUpdates.requireUpdate("0000000000000000000000000000000000000000", 1242, b))
        // The mirror moved to the installed build, or to an OLDER run, between check and download.
        val same = runCatching { NightlyUpdates.requireUpdate(b.sha, 1247, b) }
        val older = runCatching { NightlyUpdates.requireUpdate("0000000000000000000000000000000000000000", 1300, b) }
        assertTrue(same.exceptionOrNull() is IllegalStateException)
        assertTrue(older.exceptionOrNull() is IllegalStateException)
        // Not a corrupt artifact: nothing was downloaded yet.
        assertEquals(UpdateDownloadFailure.UNKNOWN, classifyUpdateDownloadFailure(older.exceptionOrNull()!!))
    }

    @Test
    fun `version labels use the run number and short SHAs`() {
        assertEquals("nightly #1247 (4a3d242)", NightlyUpdates.versionLabel(build))
        assertEquals("38 (4a3d242)", NightlyUpdates.currentVersionLabel("38", build.sha))
        assertEquals("38", NightlyUpdates.currentVersionLabel("38", "")) // no SHA -> plain version
    }

    @Test
    fun `changelog entries become stacked notes, newest first, capped`() {
        val entries = (1..12).map { n ->
            NightlyUpdates.ChangelogEntry("%040d".format(n), 1200 + n, "subject $n\n\nbody $n")
        }.shuffled()

        val markdown = NightlyUpdates.changelogMarkdown(entries, installedRunNumber = 0)!!
        val headings = markdown.lines().filter { it.startsWith("### ") }

        assertEquals(NightlyUpdates.MAX_CHANGELOG_ENTRIES, headings.size)
        assertEquals("### subject 12", headings.first())
        assertEquals("### subject 3", headings.last())
        assertTrue(markdown.contains("### subject 12\n\nbody 12\n\n### subject 11"))
    }

    @Test
    fun `changelog never lists a build at or below the installed run number`() {
        // The mirror's whole history when it does not hold the installed build (#1242):
        // an August build must not read as "what's new" to a user on a September one.
        val history = listOf(
            NightlyUpdates.ChangelogEntry("4a3d242079f43d96ad504683128d425bd583ee3a", 1247, "fix(home): a"),
            NightlyUpdates.ChangelogEntry("2320dc0775ba7b8be007131caa1f2b827369f3e0", 1007, "feat(ui): august"),
        )

        assertEquals("### fix(home): a", NightlyUpdates.changelogMarkdown(history, installedRunNumber = 1242))
        assertNull(NightlyUpdates.changelogMarkdown(history, installedRunNumber = 1247)) // nothing newer
        // No run number baked in: the list is shown as served.
        assertEquals("### fix(home): a\n\n### feat(ui): august", NightlyUpdates.changelogMarkdown(history, installedRunNumber = 0))
    }

    @Test
    fun `changelog parses the mirror document and skips unusable entries`() {
        val json = """
            {"since":"1111111111111111111111111111111111111111","current":"4a3d242079f43d96ad504683128d425bd583ee3a","complete":true,
             "entries":[
               {"sha":"4a3d242079f43d96ad504683128d425bd583ee3a","shortSha":"4a3d242","runNumber":1247,"builtAt":"x","commitMessage":"fix(home): a"},
               {"sha":"bad","runNumber":1246,"commitMessage":"dropped: bad sha"},
               {"sha":"0cc37d590f82896902a3e55e480a4e9f879dfbd2","runNumber":1242,"commitMessage":"   "},
               {"sha":"d7bc5c8afdb5204df4d2a3800dbe3dcbde831eca","runNumber":1240,"commitMessage":"feat(offline): b"}
             ]}
        """.trimIndent()

        val entries = NightlyUpdates.parseChangelog(json)!!

        assertEquals(listOf(1247, 1240), entries.map { it.runNumber })
        assertEquals("### fix(home): a\n\n### feat(offline): b", NightlyUpdates.changelogMarkdown(entries, 1230))
    }

    @Test
    fun `an empty or malformed changelog yields nothing so the caller falls back`() {
        assertNull(NightlyUpdates.changelogMarkdown(NightlyUpdates.parseChangelog("""{"entries":[]}""")!!, 0))
        assertNull(NightlyUpdates.parseChangelog("""{"error":"no nightly ingested yet"}"""))
        assertNull(NightlyUpdates.parseChangelog("<html>404</html>"))
    }

    @Test
    fun `changelog url asks for the gap since the installed commit`() {
        assertEquals(
            "https://nightly.zemer.io/changelog?since=4a3d242079f43d96ad504683128d425bd583ee3a",
            NightlyUpdates.changelogUrl("4a3d242079f43d96ad504683128d425bd583ee3a"),
        )
    }

    @Test
    fun `a download matching the announced size and sha256 verifies`() {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val digest = NightlyUpdates.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val expected = build.copy(size = bytes.size.toLong(), sha256 = digest)

        NightlyUpdates.verifyDownload(bytes.size.toLong(), digest, expected)
        NightlyUpdates.verifyDownload(bytes.size.toLong(), digest.uppercase(), expected) // case never matters
    }

    @Test
    fun `a truncated or substituted download is a corrupt artifact, never installable`() {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val digest = NightlyUpdates.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val expected = build.copy(size = bytes.size.toLong(), sha256 = digest)

        val truncated = runCatching { NightlyUpdates.verifyDownload(4000L, digest, expected) }
        assertTrue(truncated.exceptionOrNull() is CorruptUpdateArtifactException)

        val substituted = runCatching { NightlyUpdates.verifyDownload(bytes.size.toLong(), "0".repeat(64), expected) }
        assertTrue(substituted.exceptionOrNull() is CorruptUpdateArtifactException)
        assertEquals(UpdateDownloadFailure.CORRUPT_ARTIFACT, classifyUpdateDownloadFailure(substituted.exceptionOrNull()!!))
    }

    @Test
    fun `hex renders a digest lowercase and zero-padded`() {
        assertEquals("00ff10", NightlyUpdates.hex(byteArrayOf(0x00, 0xff.toByte(), 0x10)))
    }

    @Test
    fun `commit message becomes a heading plus markdown body`() {
        assertEquals(
            "### fix(x): subject (#1)\n\nBody line one\nline two.\n\n- a bullet",
            NightlyUpdates.commitMessageMarkdown("fix(x): subject (#1)\n\nBody line one\nline two.\n\n- a bullet\n"),
        )
        assertEquals("### subject only", NightlyUpdates.commitMessageMarkdown("subject only"))
        assertNull(NightlyUpdates.commitMessageMarkdown("  \n "))
    }
}
