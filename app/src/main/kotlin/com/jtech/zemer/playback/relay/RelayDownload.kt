package com.jtech.zemer.playback.relay

/** Pure, unit-testable RELAY download decisions extracted from MediaStoreDownloadManager. */
object RelayDownload {
    const val DEFAULT_AUDIO_EXTENSION = "opus"

    /**
     * A MediaStore-friendly audio extension for a file whose first [length] bytes are in [head]. `.webm` is
     * REJECTED by MediaStore.Audio (Android maps it to video/webm), so WebM/Ogg (Opus) -> "opus" and MP4 ->
     * "m4a"; in-app playback sniffs the real container regardless of the label.
     */
    fun audioExtensionFromMagic(head: ByteArray, length: Int): String = when {
        // EBML header (1A 45 DF A3) = WebM/Matroska, i.e. the Opus audio the relay serves.
        length >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> "opus"
        // "ftyp" at offset 4 = MP4/M4A.
        length >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> "m4a"
        // "OggS" = Ogg (also Opus); label the same as WebM opus.
        length >= 4 && head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
            head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() -> "opus"
        else -> DEFAULT_AUDIO_EXTENSION
    }

    /** What a download should do with a non-2xx HTTP status. */
    enum class HttpErrorAction {
        /** Relay 404: the track is genuinely unavailable -> fail fast (no retry), contracted message. */
        UNAVAILABLE,

        /** Relay 502/503: a transient relay/upstream error -> retry, contracted "try again" message. */
        TRANSIENT,

        /** Everything else (incl. all non-relay errors) -> the existing generic retry-then-fail path. */
        GENERIC,
    }

    /**
     * Classify a non-2xx [code] for a download. Only relay URLs ([isRelay]) get the typed handling that
     * mirrors the playback error contract; a DIRECT (googlevideo) error stays GENERIC exactly as before.
     */
    fun classifyHttpError(isRelay: Boolean, code: Int): HttpErrorAction = when {
        isRelay && code == 404 -> HttpErrorAction.UNAVAILABLE
        isRelay && (code == 502 || code == 503) -> HttpErrorAction.TRANSIENT
        else -> HttpErrorAction.GENERIC
    }
}
