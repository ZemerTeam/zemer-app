package com.jtech.zemer.utils

import android.content.Context
import android.content.res.Configuration
import timber.log.Timber
import java.util.Locale

/**
 * Reports an exception for debugging/crash reporting.
 * Logs the exception with Timber and prints stack trace.
 *
 * @param throwable The exception to report
 * @param context Optional context message for debugging
 */
fun reportException(throwable: Throwable, context: String? = null) {
    if (context != null) {
        "[Exception] $context - ${throwable.javaClass.simpleName}: ${throwable.message} - thread: ${Thread.currentThread().name}"
    } else {
        "[Exception] ${throwable.javaClass.simpleName}: ${throwable.message} - thread: ${Thread.currentThread().name}"
    }
    Timber.e(throwable)
    throwable.printStackTrace()
}

@Suppress("DEPRECATION", "AppBundleLocaleChanges")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
