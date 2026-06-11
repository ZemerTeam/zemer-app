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
) {
    NATIVE(R.string.installer_native_title),
    ROOT(R.string.installer_root_title),
    SHIZUKU(R.string.installer_shizuku_title);

    companion object {
        /** Resolve a persisted ordinal, falling back to [NATIVE] for unknown values. */
        fun fromOrdinal(ordinal: Int): InstallerType = entries.getOrElse(ordinal) { NATIVE }
    }
}
