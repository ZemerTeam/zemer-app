package com.jtech.zemer.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * HOW the chain is walked for one song, pure so it is unit-tested. The answer is exactly what a sequential
 * walk in the user's order gives ([SyncedFirstPicker] is offered the results in that order); only the network
 * schedule differs:
 *  1. the primary trusted provider alone: a synced answer ends the walk with no other request made (the common
 *     Zemer case costs nothing extra);
 *  2. the remaining trusted providers CONCURRENTLY, offered in priority order (they were a serial 4 s tail
 *     after every plain answer);
 *  3. the low-trust providers only when NO trusted provider answered, and concurrently too. They are served
 *     only in that case (the pick rule), so deferring them changes nothing but the seconds they used to burn
 *     at the head of a user order that listed them first.
 */
object LyricsChainWalk {
    /** [fetch] runs one provider and returns its body, or null for no answer (errors are the caller's to report). */
    suspend fun run(providers: List<LyricsProvider>, fetch: suspend (LyricsProvider) -> LabeledLyrics?): LyricsHelper.Fetched {
        val picker = SyncedFirstPicker()
        val (trusted, lowTrust) = providers.partition { !it.lowTrust }
        val primary = trusted.firstOrNull()
        if (primary != null) fetch(primary)?.let { picker.offer(primary, it) }?.let { return it }
        offerAll(picker, trusted.drop(1), fetch)?.let { return it }
        if (picker.hasTrustedAnswer) return picker.result()
        offerAll(picker, lowTrust, fetch)?.let { return it }
        return picker.result()
    }

    private suspend fun offerAll(picker: SyncedFirstPicker, providers: List<LyricsProvider>, fetch: suspend (LyricsProvider) -> LabeledLyrics?): LyricsHelper.Fetched? {
        if (providers.isEmpty()) return null
        val results = coroutineScope { providers.map { p -> async { p to fetch(p) } }.awaitAll() }
        for ((provider, labeled) in results) labeled?.let { picker.offer(provider, it) }?.let { return it }
        return null
    }
}
