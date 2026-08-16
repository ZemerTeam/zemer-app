package com.jtech.zemer.ui.screens.settings

import com.jtech.zemer.utils.StreamClient
import com.jtech.zemer.utils.StreamClientTable
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.StreamClientParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSourceUiModelTest {

    private fun sc(name: String, family: String = name) = StreamClient(
        YouTubeClient(clientName = name, clientVersion = "1.0", clientId = "1", userAgent = "ua"),
        family,
    )

    private fun meta(id: String, title: String, group: String) =
        id to StreamClientParser.FamilyMeta(id, title, group)

    private val table = StreamClientTable.Table(
        main = sc("WEB_REMIX"),
        fallbacks = listOf(
            sc("VISIONOS"), sc("VISIONOS"), sc("WEB_CREATOR"),
            sc("TVHTML5_SIMPLY", family = "TVHTML5"),
        ),
    )

    @Test
    fun `families come out in chain order, deduped`() {
        val families = StreamSourceUiModel.families(table, emptyMap())
        assertEquals(listOf("WEB_REMIX", "VISIONOS", "WEB_CREATOR", "TVHTML5"), families.map { it.id })
    }

    @Test
    fun `family meta supplies title and group, absent meta falls back to web group`() {
        val families = StreamSourceUiModel.families(
            table,
            mapOf(meta("VISIONOS", "visionOS", "native"), meta("WEB_CREATOR", "Studio", "creator")),
        )
        val visionos = families.first { it.id == "VISIONOS" }
        assertEquals("visionOS", visionos.configTitle)
        assertEquals("native", visionos.group)
        val webRemix = families.first { it.id == "WEB_REMIX" }
        assertNull(webRemix.configTitle)
        assertEquals(StreamSourceUiModel.GROUP_WEB, webRemix.group)
    }

    @Test
    fun `grouping orders known groups first then unknown first-seen, empties dropped`() {
        val families = listOf(
            StreamSourceUiModel.Family("A", null, "experimental"),
            StreamSourceUiModel.Family("B", null, StreamSourceUiModel.GROUP_CREATOR),
            StreamSourceUiModel.Family("C", null, StreamSourceUiModel.GROUP_WEB),
            StreamSourceUiModel.Family("D", null, "experimental"),
        )
        val grouped = StreamSourceUiModel.grouped(families)
        assertEquals(listOf(StreamSourceUiModel.GROUP_WEB, StreamSourceUiModel.GROUP_CREATOR, "experimental"), grouped.map { it.first })
        assertEquals(listOf("A", "D"), grouped.last().second.map { it.id })
    }

    @Test
    fun `enabledOrder drops disabled families and keeps chain order`() {
        val families = StreamSourceUiModel.families(table, emptyMap())
        val enabled = StreamSourceUiModel.enabledOrder(families, setOf("VISIONOS", "TVHTML5"))
        assertEquals(listOf("WEB_REMIX", "WEB_CREATOR"), enabled.map { it.id })
    }
}
