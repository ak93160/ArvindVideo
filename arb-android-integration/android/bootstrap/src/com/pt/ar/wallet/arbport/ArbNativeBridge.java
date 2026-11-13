package com.pt.ar.wallet.arbport;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

/** Small, origin-restricted bridge. It intentionally has no payment/OTP/PIN API. */
public final class ArbNativeBridge {
    private final Context context;
    private final WebView webView;
    private final android.content.SharedPreferences prefs;

    ArbNativeBridge(Context context, WebView webView) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.prefs = this.context.getSharedPreferences("arb_extension_port", Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String getStorage(String queryJson) {
        try {
            Object query = new JSONTokener(queryJson == null ? "null" : queryJson).nextValue();
            JSONObject out = new JSONObject();
            if (query == null || query == JSONObject.NULL) {
                for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) out.put(e.getKey(), decodeValue(String.valueOf(e.getValue())));
            } else if (query instanceof String) {
                String key = (String) query;
                if (prefs.contains(key)) out.put(key, decodeValue(prefs.getString(key, "null")));
            } else if (query instanceof JSONArray) {
                JSONArray keys = (JSONArray) query;
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.optString(i);
                    if (prefs.contains(key)) out.put(key, decodeValue(prefs.getString(key, "null")));
                }
            } else if (query instanceof JSONObject) {
                JSONObject keys = (JSONObject) query;
                java.util.Iterator<String> it = keys.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    if (prefs.contains(key)) out.put(key, decodeValue(prefs.getString(key, "null")));
                    else out.put(key, keys.opt(key));
                }
            }
            return out.toString();
        } catch (Exception ignored) { return "{}"; }
    }

    @JavascriptInterface
    public void setStorage(String valuesJson) {
        try {
            JSONObject values = new JSONObject(valuesJson == null ? "{}" : valuesJson);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            java.util.Iterator<String> it = values.keys();
            while (it.hasNext()) {
                String key = it.next();
                editor.putString(key, values.opt(key) == null ? "null" : values.opt(key).toString());
            }
            editor.apply();
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void removeStorage(String keysJson) {
        try {
            JSONArray keys = new JSONArray(keysJson == null ? "[]" : keysJson);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < keys.length(); i++) editor.remove(keys.optString(i));
            editor.apply();
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public String currentUrl() {
        try { return webView.getUrl() == null ? "" : webView.getUrl(); }
        catch (Exception ignored) { return ""; }
    }

    @JavascriptInterface
    public void navigate(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && isAllowedHost(uri.getHost())) {
                webView.post(new Runnable() {
                    @Override public void run() {
                        webView.loadUrl(uri.toString());
                    }
                });
            }
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void notify(String id, String title, String body) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            final String channel = "arb_extension_alerts";
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "ARB Bot alerts", NotificationManager.IMPORTANCE_DEFAULT));
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            android.app.Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) builder = new android.app.Notification.Builder(context, channel);
            else builder = new android.app.Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(limit(title, 80))
                    .setContentText(limit(body, 180))
                    .setAutoCancel(true);
            manager.notify(id == null ? "arb" : id, stableId(id), builder.build());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public String assetDataUrl(String assetPath) {
        InputStream input = null;
        try {
            String safe = assetPath == null ? "" : assetPath.replace("\\", "");
            if (safe.startsWith("/") || safe.contains("..") || !(safe.equals("alert.wav") || safe.startsWith("icons/"))) return "";
            input = context.getAssets().open("arb-bot/" + safe);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) > 0) out.write(buffer, 0, n);
            String mime = safe.endsWith(".wav") ? "audio/wav" : "image/png";
            return "data:" + mime + ";base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { return ""; }
        finally { try { if (input != null) input.close(); } catch (Exception ignored) { } }
    }

    private Object decodeValue(String value) {
        try { return new JSONTokener(value).nextValue(); }
        catch (Exception ignored) { return value; }
    }
    private int stableId(String raw) { return raw == null ? 4901 : Math.abs(raw.hashCode() % 100000) + 1; }
    private String limit(String value, int max) {
        if (value == null) return "ARB Bot";
        return value.length() <= max ? value : value.substring(0, max);
    }
    private boolean isAllowedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        String[] roots = {"arbpay.me", "arbpay.ai", "arbpay.vip", "arbpay.top", "arbpay.cc", "arbpay.co",
            "payvuno.com", "payduno.com", "payzuva.com", "paykexo.com", "paykuno.com", "paywivo.com", "payjora.com"};
        for (String root : roots) if (h.equals(root) || h.endsWith("." + root)) return true;
        return false;
    }
}
