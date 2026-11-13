package com.pt.ar.wallet.arbbot

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView

/**
 * WebView injection helper. Call from the existing AgentWeb/WebViewClient
 * lifecycle after a top-level page load. Do not replace the app's current
 * WebViewClient, WebChromeClient, navigation, or SSL handling.
 */
class ArbBotWebViewInstaller(
    private val context: Context,
    private val bridge: ArbBotBridge
) {
    private val allowedRoots = setOf(
        "arbpay.me",
        "arbpay.ai",
        "arbpay.vip",
        "arbpay.top",
        "arbpay.cc",
        "payvuno.com",
        "payduno.com",
        "payzuva.com",
        "paykexo.com",
        "paykuno.com",
        "paywivo.com",
        "payjora.com"
    )

    fun isAllowed(url: String?): Boolean {
        val uri = try { Uri.parse(url ?: return false) } catch (_: Exception) { return false }
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return allowedRoots.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Invoke only for the main-frame URL. The asset files referenced below
     * must be copied into app/src/main/assets/arb-bot/ by the host project.
     */
    fun onMainPageFinished(webView: WebView, url: String?) {
        if (!isAllowed(url)) return
        try {
            webView.settings.javaScriptEnabled = true
            webView.addJavascriptInterface(bridge, "ArbNativeBridge")
            inject(webView, "bridge.js")
            inject(webView, "content.js")
        } catch (e: Exception) {
            Log.e("ArbBotInstaller", "ARB port injection failed", e)
        }
    }

    private fun inject(webView: WebView, assetName: String) {
        val script = context.assets.open("arb-bot/$assetName")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        webView.evaluateJavascript(script, null)
    }
}
