package com.pt.ar.wallet.arbbot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Restricted Android bridge for the ARB WebView port.
 *
 * Expose this object only to an allowlisted ARBPay origin. Never add it to
 * arbitrary WebViews. Browser APIs not implemented here must be ported
 * explicitly rather than silently treated as successful.
 */
class ArbBotBridge(
    context: Context,
    private val openSettings: (() -> Unit)? = null,
    private val stopBot: (() -> Unit)? = null
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("arb_extension_port", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getStorage(queryJson: String?): String {
        return try {
            val query = queryJson?.let { org.json.JSONTokener(it).nextValue() }
            val output = JSONObject()
            when (query) {
                null, JSONObject.NULL -> {
                    prefs.all.forEach { (key, value) -> output.put(key, toJsonValue(value)) }
                }
                is String -> if (prefs.contains(query)) {
                    output.put(query, decode(prefs.getString(query, "null")))
                }
                is JSONArray -> {
                    for (i in 0 until query.length()) {
                        val key = query.optString(i)
                        if (prefs.contains(key)) output.put(key, decode(prefs.getString(key, "null")))
                    }
                }
                is JSONObject -> {
                    val it = query.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        if (prefs.contains(key)) output.put(key, decode(prefs.getString(key, "null")))
                        else output.put(key, query.opt(key))
                    }
                }
            }
            output.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun setStorage(valuesJson: String?) {
        try {
            val values = JSONObject(valuesJson ?: "{}")
            val edit = prefs.edit()
            val it = values.keys()
            while (it.hasNext()) {
                val key = it.next()
                edit.putString(key, values.opt(key)?.toString() ?: "null")
            }
            edit.apply()
        } catch (_: Exception) {
            // The JS caller receives no false "success" signal from this method.
        }
    }

    @JavascriptInterface
    fun removeStorage(keysJson: String?) {
        try {
            val keys = JSONArray(keysJson ?: "[]")
            val edit = prefs.edit()
            for (i in 0 until keys.length()) edit.remove(keys.optString(i))
            edit.apply()
        } catch (_: Exception) {
            // Invalid input is ignored; do not clear all preferences on parse errors.
        }
    }

    @JavascriptInterface
    fun sendMessage(messageJson: String?): String {
        return try {
            val message = JSONObject(messageJson ?: "{}")
            when (message.optString("type")) {
                "OPEN_EXTENSION_SETTINGS" -> main.post { openSettings?.invoke() }
                "FORCE_STOP" -> main.post { stopBot?.invoke() }
                "REVALIDATE_LICENSE" -> {
                    // Intentionally not emulated: the original license.js was
                    // missing from the uploaded XPI and must be obtained legitimately.
                    return JSONObject()
                        .put("ok", false)
                        .put("error", "Original extension license component unavailable")
                        .toString()
                }
                else -> return JSONObject()
                    .put("ok", false)
                    .put("error", "Android handler not implemented for this message")
                    .put("type", message.optString("type"))
                    .toString()
            }
            JSONObject().put("ok", true).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "Bridge error").toString()
        }
    }

    @JavascriptInterface
    fun notify(title: String?, body: String?) {
        val channelId = "arb_extension_alerts"
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "ARB alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title?.take(80) ?: "ARB Bot")
            .setContentText(body?.take(180) ?: "An order needs your attention")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: "An order needs your attention"))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun decode(raw: String?): Any? {
        if (raw == null) return null
        return try { org.json.JSONTokener(raw).nextValue() } catch (_: Exception) { raw }
    }

    private fun toJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Set<*> -> JSONArray(value)
        is Number, is Boolean, is String, is JSONObject, is JSONArray -> value
        else -> value.toString()
    }
}
