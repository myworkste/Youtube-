package com.auraplay.app.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.auraplay.app.data.AppPreferences
import com.auraplay.app.service.PlaybackStateHolder
import java.io.ByteArrayInputStream

class AuraWebViewClient(
    private val context: Context,
    private val preferences: AppPreferences,
    private val onPageFinishedListener: (url: String, title: String?) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (preferences.isAdBlockEnabled && AdBlockEngine.shouldBlockUrl(url)) {
            PlaybackStateHolder.incrementAdBlockedCount()
            // Return empty response to block network request
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (view != null && preferences.isAdBlockEnabled) {
            // Early injection of ad-blocking script
            view.evaluateJavascript(AdBlockEngine.getAdStripperJs(), null)
        }
        if (view != null) {
            view.evaluateJavascript(WebMediaBridge.getMediaObserverJs(), null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            preferences.lastVisitedUrl = url
            onPageFinishedListener(url, view?.title)
        }
        if (view != null) {
            if (preferences.isAdBlockEnabled) {
                view.evaluateJavascript(AdBlockEngine.getAdStripperJs(), null)
            }
            view.evaluateJavascript(WebMediaBridge.getMediaObserverJs(), null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false

        // Handle standard web schemes internally
        if (scheme == "http" || scheme == "https") {
            return false
        }

        // Handle custom intent or market links safely
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            return true
        }
    }
}
