package com.jtech.zemer.statuses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM coverage of the server-driven status-sources config (SERVER-ONLY, no baked-in fallback): the
 * typed-descriptor parse and the fail-soft distinction that keeps the feature safe - "could not obtain a
 * valid config" (-> null, caller keeps its last-good / stays hidden) vs. "valid config" (-> honored as-is,
 * even when empty = intentional dark). Uses the real `org.json` (test dep).
 * Contract: `handoff-docs/zemer-status-sources-config-request.md`.
 */
class StatusSourcesConfigTest {

    @org.junit.Before
    fun resetCache() {
        // The cache is a process-wide singleton with a no-rollback guard; without a reset the
        // version-dependent tests below would interfere across (arbitrary) JUnit method order.
        StatusSourcesCache.resetForTest()
    }

    private fun descriptor(
        id: String = "jewish-status",
        type: String = "supabase-category",
        enabled: Boolean = true,
        filterKey: String = "categoryIds",
        filter: String = """["cat-1","cat-2"]""",
    ) = """{"id":"$id","type":"$type","baseUrl":"https://x","apiKey":"k","enabled":$enabled,"$filterKey":$filter}"""

    private fun doc(vararg providers: String, version: Int = 3) =
        """{"version":$version,"updatedAt":"2026-08-04T00:00:00Z","providers":[${providers.joinToString(",")}]}"""

    // --- Happy path ---

    @Test
    fun `parses a valid two-provider config with version and per-type filters`() {
        val json = doc(
            descriptor(id = "jewish-status", type = "supabase-category", filterKey = "categoryIds", filter = """["a","b"]"""),
            descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music","singer"]"""),
            version = 7,
        )
        val config = parseStatusSourcesConfig(json)!!
        assertEquals(7L, config.version)
        assertEquals(2, config.providers.size)
        assertEquals(listOf("a", "b"), config.providersOfType(StatusProviderType.SUPABASE_CATEGORY).single().categoryIds)
        assertEquals(listOf("music", "singer"), config.providersOfType(StatusProviderType.KEYWORD_FEED).single().musicKeywords)
    }

    // --- Non-fatal skips ---

    @Test
    fun `an unknown type is skipped but the known providers still load`() {
        val json = doc(
            descriptor(id = "future", type = "telegram-stories", filterKey = "categoryIds", filter = """["z"]"""),
            descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music"]"""),
        )
        val config = parseStatusSourcesConfig(json)!!
        assertEquals(listOf("yid-status"), config.providers.map { it.id })
    }

    @Test
    fun `a disabled provider is skipped, an enabled one is kept`() {
        val json = doc(
            descriptor(id = "jewish-status", enabled = false),
            descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music"]"""),
        )
        val config = parseStatusSourcesConfig(json)!!
        assertEquals(listOf("yid-status"), config.providers.map { it.id })
    }

    @Test
    fun `an enabled provider with an empty filter list is skipped`() {
        val json = doc(
            descriptor(id = "jewish-status", filter = "[]"),
            descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music"]"""),
        )
        val config = parseStatusSourcesConfig(json)!!
        assertEquals(listOf("yid-status"), config.providers.map { it.id })
    }

    // --- The fail-soft fork: a VALID doc is honored (even empty); only an UNOBTAINABLE one keeps last-good ---

    @Test
    fun `a valid doc with an empty usable set is honored, not treated as failure`() {
        // All disabled, all unknown-type, and an empty array are all VALID configs -> honored (row hidden),
        // NOT null. Null is reserved for "could not obtain a valid config" (see below).
        val allDisabled = doc(
            descriptor(id = "jewish-status", enabled = false),
            descriptor(id = "yid-status", type = "keyword-feed", enabled = false, filterKey = "musicKeywords", filter = """["music"]"""),
        )
        val allUnknownType = doc(descriptor(id = "future", type = "telegram-stories", filter = """["z"]"""))
        for (json in listOf(allDisabled, allUnknownType, """{"version":3,"providers":[]}""")) {
            val config = parseStatusSourcesConfig(json)
            assertNotNull("valid doc should be honored, not null: $json", config)
            assertTrue("usable set should be empty: $json", config!!.providers.isEmpty())
        }
    }

    @Test
    fun `an unobtainable config returns null so the caller keeps its last-good`() {
        // Structurally broken / absent -> null. The repository keeps the last-good config (or stays hidden).
        assertNull(parseStatusSourcesConfig("not json at all"))
        assertNull(parseStatusSourcesConfig("""{"version":3}""")) // no providers array
        assertNull(parseStatusSourcesConfig(""))
        assertNull(parseStatusSourcesConfig(null))
    }

    // --- Enum + cache ---

    @Test
    fun `type slug resolves known values and rejects unknown`() {
        assertEquals(StatusProviderType.SUPABASE_CATEGORY, StatusProviderType.fromSlug("supabase-category"))
        assertEquals(StatusProviderType.KEYWORD_FEED, StatusProviderType.fromSlug("keyword-feed"))
        assertNull(StatusProviderType.fromSlug("telegram-stories"))
        assertNull(StatusProviderType.fromSlug(null))
    }

    @Test
    fun `baseUrl is normalized - a trailing slash never reaches a handler`() {
        // Handlers concatenate "$baseUrl/path"; an un-normalized trailing slash would build //rpc URLs
        // and silently 404 the whole provider family (the config is hand-authored).
        val json = doc(
            """{"id":"jewish-status","type":"supabase-category","baseUrl":"https://x.supabase.co/rest/v1/","apiKey":"k","categoryIds":["a"]}""",
        )
        assertEquals("https://x.supabase.co/rest/v1", parseStatusSourcesConfig(json)!!.providers.single().baseUrl)
    }

    @Test
    fun `installing a config makes it current and reports its version`() {
        val installed = parseStatusSourcesConfig(
            doc(descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music"]"""), version = 42),
        )!!
        StatusSourcesCache.update(installed)
        assertEquals(42L, StatusSourcesCache.syncedVersion)
        assertEquals(listOf("yid-status"), StatusSourcesCache.current().providers.map { it.id })
    }

    @Test
    fun `update never rolls back to an older config`() {
        // Guards the startup-restore race: a persisted older snapshot must not clobber a config a
        // concurrent sync already installed. Same version re-installs (harmless, same content).
        val newer = parseStatusSourcesConfig(
            doc(descriptor(id = "yid-status", type = "keyword-feed", filterKey = "musicKeywords", filter = """["music"]"""), version = 100),
        )!!
        val older = parseStatusSourcesConfig(
            doc(descriptor(id = "jewish-status"), version = 99),
        )!!
        StatusSourcesCache.update(newer)
        StatusSourcesCache.update(older) // ignored
        assertEquals(100L, StatusSourcesCache.syncedVersion)
        assertEquals(listOf("yid-status"), StatusSourcesCache.current().providers.map { it.id })
    }
}
