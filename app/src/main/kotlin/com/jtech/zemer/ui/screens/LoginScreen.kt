package com.jtech.zemer.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var hasCompletedLogin by remember { mutableStateOf(false) }

    var webView: WebView? = null

    // NOTE: Removed setupWebViewForFreshLogin() to avoid interfering with Google login flow
    // Cookie clearing should only happen during logout, not before login

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()

                        // Block specific URLs
                        val blockedUrls = listOf(
                            "support.google.com",
                            "policies.google.com"
                        )

                        if (url != null && blockedUrls.any { url.contains(it) }) {
                            android.util.Log.d("LoginScreen", "Blocked URL: $url")
                            // Show "Blocked" message
                            coroutineScope.launch {
                                Toast.makeText(context, context.getString(R.string.blocked), Toast.LENGTH_SHORT).show()
                            }
                            return true // Block the URL
                        }

                        return false // Allow the URL
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                        loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                        if (url?.startsWith("https://music.youtube.com") == true) {
                            innerTubeCookie = CookieManager.getInstance().getCookie(url)

                            android.util.Log.d("LoginScreen", "Login URL detected, extracting data...")
                            android.util.Log.d("LoginScreen", "Cookie extracted: ${if(innerTubeCookie.isNotEmpty()) "Yes (${innerTubeCookie.length} chars)" else "No"}")
                            android.util.Log.d("LoginScreen", "DataSyncId: ${if(dataSyncId.isNotEmpty()) dataSyncId else "No"}")
                            android.util.Log.d("LoginScreen", "VisitorData: ${if(visitorData.isNotEmpty()) visitorData else "No"}")

                            if (!hasCompletedLogin) {
                                hasCompletedLogin = true
                                coroutineScope.launch {
                                    // Restored original timing
                                    kotlinx.coroutines.delay(500)

                                    try {
                                        // CRITICAL: Initialize YouTube object with new authentication data
                                        YouTube.cookie = innerTubeCookie
                                        YouTube.dataSyncId = dataSyncId
                                        YouTube.visitorData = visitorData

                                        android.util.Log.d("LoginScreen", "YouTube object initialized, validating...")

                                        // Validate authentication by testing API call
                                        YouTube.accountInfo().onSuccess {
                                            accountName = it.name
                                            accountEmail = it.email.orEmpty()
                                            accountChannelHandle = it.channelHandle.orEmpty()

                                            android.util.Log.d("LoginScreen", "Successfully logged in as ${it.name}, restarting app...")

                                            // Clean up WebView before restart
                                            cleanupWebView(webView)

                                            // Small delay to ensure preferences are saved
                                            kotlinx.coroutines.delay(300)

                                            // Restart app to apply login state throughout all components
                                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            context.startActivity(intent)
                                            Runtime.getRuntime().exit(0)
                                        }.onFailure { exception ->
                                            // Clear invalid credentials and show error
                                            android.util.Log.e("LoginScreen", "Authentication validation failed: ${exception.message}")
                                            reportException(exception)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("LoginScreen", "Exception during authentication: ${e.message}")
                                        reportException(e)
                                    }
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        android.util.Log.d("LoginScreen", "JavaScript onRetrieveVisitorData called: ${if(newVisitorData != null) "Yes" else "No"}")
                        if (newVisitorData != null) {
                            visitorData = newVisitorData
                            android.util.Log.d("LoginScreen", "VisitorData set: ${newVisitorData.take(20)}...")
                        }
                    }

                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        android.util.Log.d("LoginScreen", "JavaScript onRetrieveDataSyncId called: ${if(newDataSyncId != null) "Yes" else "No"}")
                        if (newDataSyncId != null) {
                            val cleanDataSyncId = newDataSyncId.substringBefore("||")
                            dataSyncId = cleanDataSyncId
                            android.util.Log.d("LoginScreen", "DataSyncId set: ${cleanDataSyncId}")
                        }
                    }
                }, "Android")
                webView = this
                // Original login URL
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

private fun cleanupWebView(webView: WebView?) {
    try {
        // Stop any loading
        webView?.stopLoading()

        // CRITICAL FIX: Don't clear all cookies - only clear WebView-specific data
        // DO NOT call CookieManager.getInstance().removeAllCookies(null) as it breaks music playback

        // Clear history, cache, and form data only for this WebView
        webView?.apply {
            clearHistory()
            clearCache(true)
            clearFormData()
        }

        // Destroy the WebView
        webView?.destroy()
    } catch (e: Exception) {
        // Log any errors but don't crash
        android.util.Log.w("LoginScreen", "Error cleaning up WebView: ${e.message}")
    }
}
