package com.jtech.zemer.playback.sabr

/**
 * Pure detection of a SABR stream-protection (attestation) cap, shared by [SabrSession] and
 * [SabrVideoSession]. A client whose poToken cannot satisfy attestation (MWEB / IOS-class) is served a
 * small free window, then the server delivers ONLY `STREAM_PROTECTION_STATUS >= 2` with no media
 * (proven live: `tests/probe-mweb-sabr.mjs`). Counting consecutive no-progress responses under active
 * protection lets a session bail fast with a clear reason instead of grinding to the dry cap, so the
 * roster/stall fallback moves on to a client that can attest. JVM-unit-tested ([SabrProtectionTest]).
 */
internal object SabrProtection {
    /** `STREAM_PROTECTION_STATUS`: 1 = OK, 2 = attestation pending, 3 = attestation required. */
    const val ATTESTATION_PENDING = 2L
    /** Consecutive no-media responses under active protection before it's an attestation cap. */
    const val STALL_LIMIT = 3

    /** Running count of consecutive no-progress responses under active attestation (0 = not capped). */
    fun nextStalls(protStatus: Long, madeProgress: Boolean, prev: Int): Int =
        if (protStatus >= ATTESTATION_PENDING && !madeProgress) prev + 1 else 0

    fun capped(stalls: Int): Boolean = stalls >= STALL_LIMIT
}
