package com.jtech.zemer.lyrics.musixmatch

/**
 * The last Musixmatch lookup outcome as STORED in `MusixmatchLastStatusKey`: a stable code, never display text,
 * so the Content settings row can localise it. `code` is what is persisted; [parse] reads it back (unknown or
 * pre-code values parse to null and show nothing).
 */
sealed class MusixmatchStatus(val code: String) {
    object Hit : MusixmatchStatus("hit")
    object HitSynced : MusixmatchStatus("hit_synced")
    object Unauthorized : MusixmatchStatus("unauthorized")
    object Network : MusixmatchStatus("network")
    object NoMatch : MusixmatchStatus("no_match")
    object NoLyrics : MusixmatchStatus("no_lyrics")
    object NoToken : MusixmatchStatus("no_token")
    /** A gate rejected the reply: [reason] is one of [REASON_INSTRUMENTAL]/[REASON_RESTRICTED]/[REASON_ARTIST]/[REASON_TITLE]/[REASON_LENGTH], [detail] the offending value. */
    data class Rejected(val reason: String, val detail: String) : MusixmatchStatus("rejected:$reason:$detail")

    companion object {
        const val REASON_INSTRUMENTAL = "instrumental"
        const val REASON_RESTRICTED = "restricted"
        const val REASON_ARTIST = "artist"
        const val REASON_TITLE = "title"
        const val REASON_LENGTH = "length"
        /**
         * LAZY, never an eager companion field: a nested object initialised first (a lookup recording its status
         * before settings ever opened) runs the parent class init from inside its own constructor, and an eager
         * list would capture that object as null - the first dialog open after install then NPEs in [parse].
         */
        private val singletons by lazy { listOf(Hit, HitSynced, Unauthorized, Network, NoMatch, NoLyrics, NoToken) }

        fun parse(code: String?): MusixmatchStatus? {
            if (code.isNullOrBlank()) return null
            singletons.firstOrNull { it.code == code }?.let { return it }
            val parts = code.split(":", limit = 3)
            return if (parts.size == 3 && parts[0] == "rejected") Rejected(parts[1], parts[2]) else null
        }
    }
}
