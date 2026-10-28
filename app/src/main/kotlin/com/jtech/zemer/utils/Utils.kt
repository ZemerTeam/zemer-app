package com.jtech.zemer.utils

import android.content.Context
import android.content.res.Configuration
import timber.log.Timber
import java.util.Locale

/**
 * Reports a caught exception. Logged at ERROR through Timber, which the
 * [CrashReportingTree] turns into a crash-report non-fatal (with the
 * recent log breadcrumbs attached) in addition to local logcat output in
 * debug builds.
 *
 * @param throwable The exception to report
 * @param context Optional context message for debugging
 */
fun reportException(throwable: Throwable, context: String? = null) {
    if (context != null) {
        Timber.e(throwable, "[Exception] %s - thread: %s", context, Thread.currentThread().name)
    } else {
        Timber.e(throwable)
    }
}

@Suppress("DEPRECATION", "AppBundleLocaleChanges")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
