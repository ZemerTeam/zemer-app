package com.jtech.zemer.playback

/**
 * Issue #109 (reboot follow-up): connections from these callers must NOT trigger the persisted-queue
 * restore. After boot, SystemUI's media-resumption scanner binds the exported browse service with no
 * user in the loop; eagerly restoring for it loads the player and makes media3 resurrect the media
 * notification (+ launcher badge) on a phone nobody touched. An OEM skin that renames its SystemUI
 * package can be appended here — a data-only change.
 */
internal val QUEUE_RESTORE_SKIPPED_CALLERS = setOf(
    "com.android.systemui",
)

/**
 * Whether a service connection counts as USER intent for restoring the persisted queue. Every real
 * controller — the app's own UI, Android Auto, Bluetooth/headset, third-party controllers — passes
 * and restores exactly as before; the boot scanner (and an anonymous null caller) is the only thing
 * that gets an empty player. Pure + top-level so the gate is unit-tested without an Android runtime.
 */
internal fun shouldRestorePersistedQueue(callerPackage: String?): Boolean =
    callerPackage != null && callerPackage !in QUEUE_RESTORE_SKIPPED_CALLERS
