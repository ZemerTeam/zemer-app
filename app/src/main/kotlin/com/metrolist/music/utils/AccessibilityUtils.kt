package com.metrolist.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import com.metrolist.music.accessibility.ButtonMapperAccessibilityService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object AccessibilityUtils {
    private fun serviceId(context: Context): String {
        return ComponentName(context, ButtonMapperAccessibilityService::class.java).flattenToString()
    }

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(serviceId(context), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
fun rememberAccessibilityEnabledState(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    DisposableEffect(context, lifecycleOwner) {
        state.value = AccessibilityUtils.isServiceEnabled(context)

        val handler = Handler(Looper.getMainLooper())
        val accessibilityObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                state.value = AccessibilityUtils.isServiceEnabled(context)
            }
        }
        val resolver = context.contentResolver
        val enabledUri = Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED)
        val servicesUri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        resolver.registerContentObserver(enabledUri, false, accessibilityObserver)
        resolver.registerContentObserver(servicesUri, false, accessibilityObserver)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = AccessibilityUtils.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            resolver.unregisterContentObserver(accessibilityObserver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return state
}
