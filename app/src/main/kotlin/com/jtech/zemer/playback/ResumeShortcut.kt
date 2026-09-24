package com.jtech.zemer.playback

/**
 * The Resume playback launcher shortcut (#508), pure so the rule is unit-tested. The persisted queue
 * restores ASYNCHRONOUSLY on a cold start, so a resume command that arrives while that restore is in
 * flight is PARKED until the restore lands - never raced against a timer: a slow restore (a big
 * queue on a slow phone) read as a false "nothing to resume" while the queue landed paused a moment
 * later. With nothing in flight the player's queue is the truth: empty means there is nothing to
 * resume (Persistent Queue off, or never played), otherwise play.
 */
object ResumeShortcut {
    enum class Action { WAIT_FOR_RESTORE, NOTHING_TO_RESUME, PLAY }

    fun decide(restoreInFlight: Boolean, mediaItemCount: Int): Action = when {
        restoreInFlight -> Action.WAIT_FOR_RESTORE
        mediaItemCount <= 0 -> Action.NOTHING_TO_RESUME
        else -> Action.PLAY
    }
}
