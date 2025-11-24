package com.metrolist.music.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.metrolist.music.utils.ButtonInputCapture
import com.metrolist.music.utils.ButtonMapperBridge

class ButtonMapperAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: return).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isProtectedKey(event.keyCode)) {
            return false
        }
        if (ButtonInputCapture.isCapturing()) {
            ButtonInputCapture.notify(event)
            return true
        }
        if (ButtonMapperBridge.dispatchKey(event)) {
            return true
        }
        return false
    }

    override fun onInterrupt() {
        // No-op
    }

    private fun isProtectedKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_POWER
    }
}
