package com.dpi

import android.content.Context
import timber.log.Timber

/**
 * DensityScaler - Main entry point for screen density scaling.
 *
 * Modified for Metrolist to use manual SharedPreferences control instead of automatic scaling.
 * Reads scale factor from user preferences with default of 1.0f (100% native).
 *
 * Supported scale factors:
 * - 1.0f (100%) - Native density (default)
 * - 0.75f (75%) - Compact
 * - 0.65f (65%) - Very Compact
 * - 0.55f (55%) - Ultra Compact
 */
class DensityScaler : BaseLifecycleContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val scaleFactor = getScaleFactorFromPreferences(context)
        val configuration = DensityConfiguration(scaleFactor)
        active = configuration.takeIf { scaleFactor != 1.0f }
        configuration.applyDensityScaling(context)
        return true
    }

    companion object {
        private const val PREFS_NAME = "metrolist_settings"
        private const val KEY_DENSITY_SCALE = "density_scale_factor"
        private const val DEFAULT_SCALE_FACTOR = 1.0f

        @Volatile
        private var active: DensityConfiguration? = null

        /**
         * Reapplies the density scale to [activity]'s resources — for an activity that handles its
         * own `configChanges` (MainActivity), whose resources the framework just reset WITHOUT any
         * lifecycle event firing (issue #521: the fullscreen video player's forced landscape).
         * Idempotent and a strict no-op at native scale; call it from `onConfigurationChanged`
         * BEFORE dispatching to super, so the view tree re-reads the already-rescaled metrics.
         */
        fun reapply(activity: android.app.Activity) {
            active?.applyDensityToActivity(activity)
        }

        /**
         * Reads the density scale factor from SharedPreferences.
         * Uses SharedPreferences instead of DataStore for synchronous access during ContentProvider initialization.
         */
        private fun getScaleFactorFromPreferences(context: Context): Float {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getFloat(KEY_DENSITY_SCALE, DEFAULT_SCALE_FACTOR)
            } catch (e: Exception) {
                Timber.tag("DensityScaler").w(e, "Failed to read scale factor from preferences")
                DEFAULT_SCALE_FACTOR
            }
        }
    }
}
