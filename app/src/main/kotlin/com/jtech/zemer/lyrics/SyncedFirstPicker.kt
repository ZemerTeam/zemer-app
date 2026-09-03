package com.jtech.zemer.lyrics

import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND

/**
 * The chain's pick rule, pure so it is unit-tested. Providers are offered in trust order and the walk stops
 * at the first SYNCED body from a TRUSTED provider (a scrolling lyric beats a static one, and every trusted
 * provider gates its sync to the same recording). A trusted plain body is held as the fallback. A LOW-TRUST
 * provider (YouTube's auto-generated transcript, its lyrics tab) never outranks anything a trusted provider
 * gave: it is served only when no trusted provider answered at all, timestamps or not.
 * Regression: an ASR caption track used to win over a curated Zemer shironet body just for carrying timestamps.
 */
class SyncedFirstPicker {
    private var trustedPlain: LyricsHelper.Fetched? = null
    private var lowTrust: LyricsHelper.Fetched? = null

    /** Offer [labeled] from [provider]; returns the final answer when the walk can stop, else null. */
    fun offer(provider: LyricsProvider, labeled: LabeledLyrics): LyricsHelper.Fetched? {
        if (labeled.lyrics.isBlank() || labeled.lyrics == LYRICS_NOT_FOUND) return null
        val fetched = LyricsHelper.Fetched(labeled.lyrics, labeled.label)
        if (provider.lowTrust) {
            if (lowTrust == null) lowTrust = fetched
            return null
        }
        if (LyricsUtils.isSynced(labeled.lyrics)) return fetched
        if (trustedPlain == null) trustedPlain = fetched
        return null
    }

    /** The answer once every provider was walked: the first trusted plain body, else the first low-trust body, else not found. */
    fun result(): LyricsHelper.Fetched = trustedPlain ?: lowTrust ?: LyricsHelper.Fetched(LYRICS_NOT_FOUND, null)
}
