package com.jtech.zemer.utils.updater

import androidx.annotation.StringRes
import com.jtech.zemer.R

/**
 * How a downloaded update APK gets installed. Ordinals are persisted in
 * DataStore ([com.jtech.zemer.constants.InstallerTypeKey]) — append new
 * entries, never reorder.
 */
enum class InstallerType(
    @StringRes val title: Int,
    /** Heads-up shown while installing, warning the user the app will close/restart; null = none. */
    @StringRes val installingNote: Int?,
) {
    NATIVE(R.string.installer_native_title, installingNote = null),
    ROOT(R.string.installer_root_title, installingNote = R.string.installing_note_restart),
    SHIZUKU(R.string.installer_shizuku_title, installingNote = R.string.installing_note_reopen);

    companion object {
        /** Resolve a persisted ordinal, falling back to [NATIVE] for unknown values. */
        fun fromOrdinal(ordinal: Int): InstallerType = entries.getOrElse(ordinal) { NATIVE }
    }
}
