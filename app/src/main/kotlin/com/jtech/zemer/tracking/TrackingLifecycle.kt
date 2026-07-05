package com.jtech.zemer.tracking

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Session semantics for the `open` event (spec §3.1) with zero extra dependencies: an `open` fires
 * when the FIRST activity starts (cold start) and again on return-to-foreground after more than
 * [SESSION_GAP_MS] in background — never on screen changes (activity handoffs keep the count > 0).
 * The background transition also triggers a queue flush (spec §2). A service-only process start
 * (e.g. media resumption) deliberately fires nothing: no UI was opened.
 */
class TrackingLifecycle : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private var lastBackgroundedAt = 0L

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) {
            val gap = System.currentTimeMillis() - lastBackgroundedAt
            if (lastBackgroundedAt == 0L || gap > SESSION_GAP_MS) {
                Tracker.open()
            }
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0) {
            lastBackgroundedAt = System.currentTimeMillis()
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
