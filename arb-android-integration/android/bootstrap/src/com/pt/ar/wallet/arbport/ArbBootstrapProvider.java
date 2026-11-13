package com.pt.ar.wallet.arbport;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Attaches extension runtime only to ARBPay WebViews inside this app. */
public final class ArbBootstrapProvider extends ContentProvider {
    private static final String TAG = "ArbAndroidPort";
    private static final String JS_NAME = "ArbNativeBridge";
    private static final String[] HOSTS = {"arbpay.me", "arbpay.ai", "arbpay.vip", "arbpay.top", "arbpay.cc"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<WebView, String> injected = Collections.synchronizedMap(new WeakHashMap<WebView, String>());
    private WeakReference<Activity> currentActivity = new WeakReference<Activity>(null);
    private boolean scanning;
    private boolean notificationPermissionAsked;

    @Override public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        Context app = ctx.getApplicationContext();
        if (app instanceof Application) {
            ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, android.os.Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityResumed(Activity a) {
                    currentActivity = new WeakReference<Activity>(a);
                    requestNotificationPermission(a);
                    startScanning();
                }
                @Override public void onActivityPaused(Activity a) {
                    if (currentActivity.get() == a) { currentActivity.clear(); stopScanning(); }
                }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { injected.clear(); }
            });
        }
        return true;
    }

    private void requestNotificationPermission(Activity activity) {
        if (!notificationPermissionAsked && Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionAsked = true;
            try { activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 4902); }
            catch (Exception ignored) { }
        }
    }
    private void startScanning() { if (!scanning) { scanning = true; handler.post(scanTask); } }
    private void stopScanning() { scanning = false; handler.removeCallbacks(scanTask); }

    private final Runnable scanTask = new Runnable() {
        @Override public void run() {
            if (!scanning) return;
            Activity activity = currentActivity.get();
            if (activity != null && !activity.isFinishing()) {
                try { scanView(activity.getWindow().getDecorView()); }
                catch (Exception e) { Log.w(TAG, "WebView scan failed", e); }
            }
            handler.postDelayed(this, 1100L);
        }
    };

    private void scanView(View view) {
        if (view instanceof WebView) { injectIfAllowed((WebView) view); return; }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scanView(group.getChildAt(i));
        }
    }

    private void injectIfAllowed(WebView webView) {
        String url = webView.getUrl();
        if (!isAllowed(url)) return;
        String prior = injected.get(webView);
        if (url.equals(prior) || webView.getProgress() < 65) return;
        injected.put(webView, url);
        try {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(new ArbNativeBridge(getContext(), webView), JS_NAME);
            injectAsset(webView, "extension-shim.js", false);
            injectAsset(webView, "page-hook.js", true);
            injectAsset(webView, "background.js", true);
            webView.evaluateJavascript("try{chrome.runtime.__fireStartup&&chrome.runtime.__fireStartup();}catch(e){}", null);
            webView.evaluateJavascript("try{chrome.runtime.__fireTabUpdated&&chrome.runtime.__fireTabUpdated();}catch(e){}", null);
            injectAsset(webView, "content.js", true);
            Log.i(TAG, "ARB extension scripts injected into " + Uri.parse(url).getHost());
        } catch (Exception e) {
            injected.remove(webView);
            Log.e(TAG, "Extension injection failed", e);
        }
    }

    private boolean isAllowed(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            for (String root : HOSTS) if (host.equals(root) || host.endsWith("." + root)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private void injectAsset(WebView webView, String name, boolean wrap) throws Exception {
        InputStream input = getContext().getAssets().open("arb-bot/" + name);
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) source.append(line).append('\n');
        reader.close();
        String script = source.toString();
        if (wrap) script = "(function(){try{\n" + script + "\n}catch(e){console.error('[ARB Android]',e);}}).call(window);";
        webView.evaluateJavascript(script, null);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
