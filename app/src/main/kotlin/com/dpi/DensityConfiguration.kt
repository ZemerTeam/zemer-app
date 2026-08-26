package com.dpi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import timber.log.Timber

/**
 * Configuration class for adjusting screen density dynamically.
 * Applies density scaling to the entire application and maintains it across activity lifecycle events.
 *
 * The override rides the deprecated `Resources.updateConfiguration`, which the framework silently
 * WIPES on every system configuration delivery — including the rotation caused by the fullscreen
 * video player's forced landscape (issue #521, where MainActivity handles `configChanges` so no
 * lifecycle event fires to heal it). Re-application is therefore:
 * - IDEMPOTENT ([DensityMath.targetDpi]): safe from any hook, any number of times, and correct when
 *   the device's real density changes (the last-applied value is the only state; the incoming dpi is
 *   always treated as the current native base — never a value captured once at startup);
 * - always computed on a COPY of the configuration: mutating the live `resources.configuration` in
 *   place makes `updateConfiguration` diff the incoming object against itself, see no change, and
 *   silently skip the metrics update (the original "broken until app restart" half of #521);
 * - EVENT-DRIVEN: activity lifecycle hooks cover recreated activities, an application-level
 *   [ComponentCallbacks] covers the app resources, and `MainActivity.onConfigurationChanged`
 *   (the one activity that opts into `configChanges`) calls [applyDensityToActivity] via
 *   [DensityScaler.reapply] BEFORE dispatching to its views, so Compose re-reads the scaled metrics.
 */
internal class DensityConfiguration(
    private val densityScale: Float
) : ActivityLifecycleManager() {

    private var lastAppliedDpi: Int? = null

    /**
     * Applies the density scaling to the application context and registers the re-application
     * hooks. This method should be called once during initialization.
     */
    @SuppressLint("LogNotTimber")
    fun applyDensityScaling(context: Context) {
        if (densityScale == 1.0f) return

        try {
            onCreate()
            updateDensityDpi(context.resources)
            // The framework resets the app Resources' configuration on system config changes; this
            // callback re-applies right after (the per-activity resources ride the activity hooks).
            context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    updateDensityDpi(context.applicationContext.resources)
                }

                override fun onLowMemory() {}
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply configuration", e)
        }
    }

    /**
     * Recomputes and applies the scaled density to [resources] — a no-op when the scale is already
     * in place (see [DensityMath.targetDpi]). Always works on a COPY of the configuration.
     */
    private fun updateDensityDpi(resources: Resources) {
        val config = Configuration(resources.configuration)
        val target = DensityMath.targetDpi(config.densityDpi, lastAppliedDpi, densityScale) ?: return
        config.densityDpi = target
        lastAppliedDpi = target
        Timber.tag(TAG).i("Updated densityDpi to: $target")
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * Reapply density scaling when an activity is created.
     */
    override fun onActivityCreated(activity: Activity) {
        applyDensityToActivity(activity)
    }

    /**
     * Reapply density scaling when an activity is resumed.
     */
    override fun onActivityResumed(activity: Activity) {
        applyDensityToActivity(activity)
    }

    /**
     * Reapply density scaling when an activity is started.
     */
    override fun onActivityStarted(activity: Activity) {
        applyDensityToActivity(activity)
    }

    /**
     * Applies the density configuration to a specific activity's resources.
     */
    fun applyDensityToActivity(activity: Activity) {
        try {
            updateDensityDpi(activity.resources)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to update density for activity")
        }
    }

    companion object {
        private val TAG = DensityConfiguration::class.java.simpleName
    }
}
