package com.jtech.zemer.ui.screens.settings

import com.jtech.zemer.utils.StreamClientTable
import com.zemer.cipher.StreamClientParser

/**
 * Pure model behind the Stream Sources screen: derives the toggle rows and their grouping from
 * the CURRENT client table, so the screen always matches the chain the resolver actually runs
 * (config order IS the displayed order — the old hand-maintained "must match the array" rule is
 * now structural). JVM-tested in StreamSourceUiModelTest.
 */
object StreamSourceUiModel {

    const val GROUP_WEB = "web"
    const val GROUP_NATIVE = "native"
    const val GROUP_CREATOR = "creator"

    /** The known group ids in their display order; unknown groups append after, first-seen. */
    private val KNOWN_GROUP_ORDER = listOf(GROUP_WEB, GROUP_NATIVE, GROUP_CREATOR)

    data class Family(
        val id: String,
        /** The config's display title, when its families[] row exists (English, server-driven). */
        val configTitle: String?,
        val group: String,
    )

    /**
     * Families in CHAIN order (main's family first), deduped — the VISIONOS pair renders one
     * toggle. A family without a families[] row falls back to the "web" group and its raw id as
     * title (the hybrid string map in the composable overrides both for known families).
     */
    fun families(
        table: StreamClientTable.Table,
        meta: Map<String, StreamClientParser.FamilyMeta>,
    ): List<Family> = familiesOf(listOf(table.main) + table.fallbacks, meta)

    /**
     * The SABR toggle rows: the families of the table's SABR roster (its sabr-capable entries), in
     * table order, deduped — so the SABR list also follows the table, never a compiled trio.
     */
    fun sabrFamilies(
        table: StreamClientTable.Table,
        meta: Map<String, StreamClientParser.FamilyMeta>,
    ): List<Family> = familiesOf(table.sabrRoster, meta)

    private fun familiesOf(
        clients: List<com.jtech.zemer.utils.StreamClient>,
        meta: Map<String, StreamClientParser.FamilyMeta>,
    ): List<Family> =
        clients
            .map { it.family }
            .distinct()
            .map { id ->
                val row = meta[id]
                Family(id = id, configTitle = row?.title, group = row?.group ?: GROUP_WEB)
            }

    /** Groups in display order (known groups first, unknown appended first-seen), empty dropped. */
    fun grouped(families: List<Family>): List<Pair<String, List<Family>>> {
        val byGroup = families.groupBy { it.group }
        val unknownGroups = families.map { it.group }.distinct().filter { it !in KNOWN_GROUP_ORDER }
        return (KNOWN_GROUP_ORDER + unknownGroups).mapNotNull { group ->
            byGroup[group]?.let { group to it }
        }
    }

    /** The chip-row entries: enabled families in chain order. */
    fun enabledOrder(families: List<Family>, disabled: Set<String>): List<Family> =
        families.filter { it.id !in disabled }
}
