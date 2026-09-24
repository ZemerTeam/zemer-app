package com.jtech.zemer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.jtech.zemer.playback.MusicService
import com.jtech.zemer.utils.reportException

/**
 * Invisible trampoline for the "Resume playback" launcher shortcut (#508). A launcher shortcut can
 * only launch an Activity, so this no-display Activity forwards a resume command to [MusicService]
 * and finishes immediately - playback resumes in the background without opening the app UI. Started
 * from a user tap (foreground), so the plain service start is permitted; [MusicService] promotes
 * itself to the foreground once playback begins (the same path the widget's play control uses).
 */
class ResumePlaybackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MusicService::class.java).setAction(MusicService.ACTION_RESUME_PLAYBACK)
        runCatching { startService(intent) }
            .recoverCatching { startForegroundService(intent) }
            .onFailure { reportException(it) }
        finish()
    }
}
