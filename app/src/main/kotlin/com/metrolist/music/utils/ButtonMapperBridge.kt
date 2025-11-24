package com.metrolist.music.utils

import android.view.KeyEvent
import com.metrolist.music.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ButtonMapperBridge {
    private val activeActivity = AtomicReference<MainActivity?>()

    fun register(activity: MainActivity) {
        activeActivity.set(activity)
    }

    fun unregister(activity: MainActivity) {
        activeActivity.compareAndSet(activity, null)
    }

    fun dispatchKey(event: KeyEvent): Boolean {
        val activity = activeActivity.get() ?: return false
        val latch = CountDownLatch(1)
        var handled = false
        activity.runOnUiThread {
            handled = activity.handleAccessibilityKey(event)
            latch.countDown()
        }
        return try {
            latch.await(50, TimeUnit.MILLISECONDS)
            handled
        } catch (_: InterruptedException) {
            false
        }
    }
}
