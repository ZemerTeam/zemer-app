package com.jtech.zemer.tracking

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/**
 * Session semantics for the `open` event (spec §3.1) with zero extra dependencies: an `open` fires
 * when the FIRST activity starts (cold start) and again on return-to-foreground after more than
 * [SESSION_GAP_MS] in background — never on screen changes (activity handoffs keep the count > 0).
 * A real background transition also triggers a queue flush (spec §2).
 *
 * Two deliberate subtleties:
 * - A configuration change (rotation, theme/locale switch) transits the started-count through 0
 *   while the app never leaves the foreground — [Activity.isChangingConfigurations] gates it out,
 *   so no flush fires and no session boundary is recorded per rotation.
 * - The gap is measured on [SystemClock.elapsedRealtime] (monotonic, counts through deep sleep),
 *   never wall-clock: an NTP/timezone clock step while backgrounded must not suppress or fabricate
 *   `open` events.
 *
 * A service-only process start (e.g. media resumption) deliberately fires nothing: no UI opened.
 */
class TrackingLifecycle : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private var lastBackgroundedAtElapsed = 0L
    private var inBackground = true // process start counts as returning from background

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0 && inBackground) {
            val gap = SystemClock.elapsedRealtime() - lastBackgroundedAtElapsed
            if (lastBackgroundedAtElapsed == 0L || gap > SESSION_GAP_MS) {
                Tracker.open()
            }
            inBackground = false
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            inBackground = true
            lastBackgroundedAtElapsed = SystemClock.elapsedRealtime()
            Tracker.onAppBackgrounded()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val SESSION_GAP_MS = 30 * 60 * 1000L
    }
}
