package com.dpi

import android.annotation.SuppressLint
import android.app.Activity
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
 * - STATELESS and idempotent ([DensityMath.targetDpi]): the target is always the CURRENT native
 *   density (from `Resources.getSystem()`, which the framework owns and keeps updated) times the
 *   scale, applied only when the Resources isn't already there — safe from any hook, any number of
 *   times, per Resources object, and correct across genuine system density changes;
 * - always computed on a COPY of the configuration: mutating the live `resources.configuration` in
 *   place makes `updateConfiguration` diff the incoming object against itself, see no change, and
 *   silently skip the metrics update (the original "broken until app restart" half of #521);
 * - EVENT-DRIVEN, no polling: activity lifecycle hooks cover recreated activities;
 *   [onSystemConfigurationChanged] — forwarded from the [DensityScaler] ContentProvider, which the
 *   framework dispatches to right after resetting the app Resources — re-applies to the app
 *   resources AND every active activity, so any `configChanges`-handling activity is covered
 *   generically; `MainActivity.onConfigurationChanged` additionally calls
 *   [applyDensityToActivity] (via [DensityScaler.reapply]) BEFORE dispatching to its views, so the
 *   view tree deterministically re-reads the scaled metrics during that dispatch.
 *
 * Known limitation (accepted): the native base is the DEFAULT display's density, so an activity
 * shown on a secondary display (DeX/external) scales relative to the primary panel. Stateless-ness
 * keeps even that case stable — a wrong-but-constant target, never a compounding one.
 */
internal class DensityConfiguration(
    private val densityScale: Float
) : ActivityLifecycleManager() {

    private var appContext: Context? = null

    /**
     * Applies the density scaling to the application context and registers the activity lifecycle
     * hooks. This method should be called once during initialization.
     */
    @SuppressLint("LogNotTimber")
    fun applyDensityScaling(context: Context) {
        if (densityScale == 1.0f) return

        try {
            appContext = context.applicationContext
            onCreate()
            updateDensityDpi(context.resources)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply configuration", e)
        }
    }

    /**
     * A system configuration change landed (forwarded from the [DensityScaler] provider, which the
     * framework calls right after resetting the app Resources): re-apply to the app resources and
     * to every active activity's resources. Idempotent, so sweeping activities the framework did
     * not touch (or that MainActivity's own hook already healed) is a strict no-op.
     */
    fun onSystemConfigurationChanged() {
        appContext?.let { runCatching { updateDensityDpi(it.resources) } }
        forEachActiveActivity { applyDensityToActivity(it) }
    }

    /**
     * Recomputes and applies the scaled density to [resources] — a no-op when [resources] already
     * sits at the target (see [DensityMath.targetDpi]). Always works on a COPY of the configuration.
     */
    private fun updateDensityDpi(resources: Resources) {
        val nativeDpi = Resources.getSystem().configuration.densityDpi
        val config = Configuration(resources.configuration)
        val target = DensityMath.targetDpi(config.densityDpi, nativeDpi, densityScale) ?: return
        config.densityDpi = target
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
