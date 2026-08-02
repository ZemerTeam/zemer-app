package com.jtech.zemer.statuses

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM coverage of the Status library's pure filter/sort/group logic. */
class StatusDownloadsViewTest {

    private fun d(id: String, kind: String, creator: String, posted: String, saved: Long) = StatusDownload(
        id = id, kind = kind, creatorId = creator.lowercase(), creatorName = creator, creatorAvatar = null,
        postedAt = posted, caption = null, textBody = null,
        mediaUri = "content://$id", savedAt = saved,
    )

    private val items = listOf(
        d("1", "video", "Beri", "2026-08-01T10:00:00+00:00", 300),
        d("2", "image", "Avi", "2026-08-03T10:00:00+00:00", 100),
        d("3", "text", "Beri", "2026-08-02T10:00:00+00:00", 400),
        d("4", "video", "Avi", "2026-08-01T09:00:00+00:00", 200),
    )

    @Test
    fun `filterByKind keeps only the chosen kind, ALL keeps all`() {
        assertEquals(listOf("1", "4"), items.filterByKind(StatusKindFilter.VIDEO).map { it.id })
        assertEquals(listOf("2"), items.filterByKind(StatusKindFilter.IMAGE).map { it.id })
        assertEquals(listOf("3"), items.filterByKind(StatusKindFilter.TEXT).map { it.id })
        assertEquals(4, items.filterByKind(StatusKindFilter.ALL).size)
    }

    @Test
    fun `sortedFlat by recent saved is newest-saved first`() {
        assertEquals(listOf("3", "1", "4", "2"), items.sortedFlat(StatusDownloadSort.RECENT_SAVED).map { it.id })
    }

    @Test
    fun `sortedFlat by recent posted is newest-posted first (ISO string order)`() {
        assertEquals(listOf("2", "3", "1", "4"), items.sortedFlat(StatusDownloadSort.RECENT_POSTED).map { it.id })
    }

    @Test
    fun `groupByCreator sorts creators A-Z and each section newest-saved first`() {
        val groups = items.groupByCreator()
        assertEquals(listOf("Avi", "Beri"), groups.map { it.creatorName })
        assertEquals(listOf("4", "2"), groups[0].items.map { it.id }) // Avi: saved 200 then 100
        assertEquals(listOf("3", "1"), groups[1].items.map { it.id }) // Beri: saved 400 then 300
    }
}
