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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
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
    private static final String[] HOSTS = {"arbpay.me", "arbpay.ai", "arbpay.vip", "arbpay.top", "arbpay.cc", "arbpay.co",
            "payvuno.com", "payduno.com", "payzuva.com", "paykexo.com", "paykuno.com", "paywivo.com", "payjora.com"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<WebView, String> injected = Collections.synchronizedMap(new WeakHashMap<WebView, String>());
    private WeakReference<Activity> currentActivity = new WeakReference<Activity>(null);
    private boolean scanning;
    private boolean notificationPermissionAsked;
    private int scannedWebViews;
    private int allowedWebViews;
    private static final String PREFS = "arb_extension_port";
    private static final String PREF_ENABLED = "extension_enabled";
    private static final String TOGGLE_TAG = "ARB_EXTENSION_TOGGLE";
    private static final String SETTINGS_TAG = "ARB_EXTENSION_SETTINGS";
    private ArbNativeBuyController nativeController;

    @Override public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        nativeController = new ArbNativeBuyController(ctx);
        Context app = ctx.getApplicationContext();
        if (app instanceof Application) {
            ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, android.os.Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityResumed(Activity a) {
                    currentActivity = new WeakReference<Activity>(a);
                    nativeController.onActivityResumed(a);
                    requestNotificationPermission(a);
                    installToggle(a);
                    startScanning();
                }
                @Override public void onActivityPaused(Activity a) {
                    nativeController.onActivityPaused(a);
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
                try {
                    scannedWebViews = 0;
                    allowedWebViews = 0;
                    View decor = activity.getWindow().getDecorView();
                    installToggle(activity);
                    scanView(decor);
                    nativeController.scan(activity);
                    refreshToggle(activity);
                } catch (Exception e) { Log.w(TAG, "WebView scan failed", e); }
            }
            handler.postDelayed(this, 1100L);
        }
    };

    private void scanView(View view) {
        if (view instanceof WebView) {
            WebView webView = (WebView) view;
            scannedWebViews++;
            if (isAllowed(webView.getUrl())) allowedWebViews++;
            injectIfAllowed(webView);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scanView(group.getChildAt(i));
        }
    }

    private void injectIfAllowed(WebView webView) {
        if (!isExtensionEnabled()) return;
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

    private boolean isExtensionEnabled() {
        Context ctx = getContext();
        return ctx != null && ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
    }

    private void installToggle(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        View existing = decor.findViewWithTag(TOGGLE_TAG);
        if (existing instanceof TextView) {
            if (decor.findViewWithTag(SETTINGS_TAG) == null) addSettingsButton(activity, (ViewGroup) decor);
            refreshToggle(activity);
            return;
        }

        final TextView toggle = new TextView(activity);
        toggle.setTag(TOGGLE_TAG);
        toggle.setTextSize(12f);
        toggle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toggle.setTextColor(Color.WHITE);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        toggle.setElevation(dp(activity, 7));
        toggle.setMaxWidth(dp(activity, 280));
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                toggleExtension(activity);
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 42), Gravity.TOP | Gravity.END);
        params.topMargin = dp(activity, 74);
        params.rightMargin = dp(activity, 60);
        ((ViewGroup) decor).addView(toggle, params);
        addSettingsButton(activity, (ViewGroup) decor);
        refreshToggle(activity);
    }

    private void addSettingsButton(final Activity activity, ViewGroup decor) {
        if (decor.findViewWithTag(SETTINGS_TAG) != null) return;
        final TextView settings = new TextView(activity);
        settings.setTag(SETTINGS_TAG);
        settings.setText("⚙");
        settings.setTextSize(19f);
        settings.setTextColor(Color.WHITE);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(makePill(activity, Color.rgb(54, 62, 76)));
        settings.setElevation(dp(activity, 7));
        settings.setClickable(true);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                nativeController.openSettings(activity);
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 42), Gravity.TOP | Gravity.END);
        params.topMargin = dp(activity, 74);
        params.rightMargin = dp(activity, 10);
        decor.addView(settings, params);
    }

    private GradientDrawable makePill(Activity activity, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(activity, 20));
        bg.setStroke(dp(activity, 1), Color.argb(90, 255, 255, 255));
        return bg;
    }

    private void refreshToggle(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        View found = decor.findViewWithTag(TOGGLE_TAG);
        if (!(found instanceof TextView)) return;
        boolean enabled = isExtensionEnabled();
        TextView toggle = (TextView) found;
        String label;
        int color;
        if (!enabled) {
            label = "EXTENSION OFF";
            color = Color.rgb(92, 99, 112);
        } else if (nativeController.isBuyActivity(activity)) {
            label = nativeController.getAdapterCount() > 0
                    ? "EXTENSION ON  •  NATIVE BUY LIST"
                    : "EXTENSION ON  •  FINDING BUY LIST";
            color = nativeController.getAdapterCount() > 0
                    ? Color.rgb(29, 137, 87) : Color.rgb(190, 105, 28);
        } else if (scannedWebViews == 0) {
            label = "EXTENSION ON  •  OPEN BUY";
            color = Color.rgb(190, 105, 28);
        } else if (allowedWebViews == 0) {
            label = "EXTENSION ON  •  OPEN ARB PAGE";
            color = Color.rgb(190, 105, 28);
        } else {
            label = "EXTENSION ON  •  PAGE FOUND";
            color = Color.rgb(29, 137, 87);
        }
        toggle.setText(label);
        toggle.setBackground(makePill(activity, color));
        toggle.setContentDescription(enabled ? "Turn ARB extension off" : "Turn ARB extension on");
    }

    private void toggleExtension(Activity activity) {
        Context ctx = getContext();
        if (ctx == null || activity == null) return;
        boolean enabled = !isExtensionEnabled();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, enabled).apply();
        nativeController.onExtensionToggled(activity, enabled);
        injected.clear();
        reloadAllowedWebViews(activity.getWindow().getDecorView());
        refreshToggle(activity);
        String message;
        if (!enabled) {
            message = "ARB Extension OFF.";
        } else if (nativeController.isBuyActivity(activity)) {
            message = "ARB Extension ON. Open the gear button to configure Native Buy matching.";
        } else {
            message = "ARB Extension ON. Open the Buy screen to use Native Buy matching.";
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private void reloadAllowedWebViews(View view) {
        if (view instanceof WebView) {
            final WebView webView = (WebView) view;
            if (isAllowed(webView.getUrl())) {
                webView.postDelayed(new Runnable() {
                    @Override public void run() {
                        try { webView.reload(); }
                        catch (Exception e) { Log.w(TAG, "WebView reload after extension toggle failed", e); }
                    }
                }, 180L);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) reloadAllowedWebViews(group.getChildAt(i));
        }
    }

    private int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
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
