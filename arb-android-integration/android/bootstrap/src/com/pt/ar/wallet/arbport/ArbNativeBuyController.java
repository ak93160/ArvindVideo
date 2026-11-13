package com.pt.ar.wallet.arbport;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native Buy controller. It inspects the visible BuyOptionalNewAdapter list and
 * clicks only that row's existing visible Buy button. It never calls purchase
 * APIs directly and never enters OTP/PIN or confirms/submits a payment.
 */
public final class ArbNativeBuyController {
    private static final String PREFS = "arb_extension_port";
    private static final String PREF_ENABLED = "extension_enabled";
    private static final String PREF_BOT = "native_bot_enabled";
    private static final String PREF_MODE = "native_bot_mode";
    private static final String PREF_FIXED = "native_bot_fixed";
    private static final String PREF_MIN = "native_bot_min";
    private static final String PREF_MAX = "native_bot_max";
    private static final String PREF_LATENCY = "native_bot_latency";
    private static final String PREF_REFRESH_MS = "native_bot_refresh_ms";
    private static final String PREF_PROVIDER = "native_bot_provider";
    private static final String BUY_ACTIVITY = "com.pt.ar.wallet.home.buy.activity.BuyActivity";
    private static final String ADAPTER_CLASS = "com.pt.ar.wallet.home.buy.adapter.BuyOptionalNewAdapter";
    private static final String PANEL_TAG = "ARB_NATIVE_BUY_PANEL";

    private final Context appContext;
    private final Set<String> attemptedKeys = new HashSet<String>();
    private WeakReference<Activity> currentActivity = new WeakReference<Activity>(null);
    private WeakReference<View> panelRef = new WeakReference<View>(null);
    private TextView panelStatus;
    private Button startStopButton;
    private EditText fixedInput;
    private EditText minInput;
    private EditText maxInput;
    private EditText latencyInput;
    private EditText refreshInput;
    private Spinner providerInput;
    private RadioGroup modeGroup;
    private WeakReference<Activity> buyActivityRef = new WeakReference<Activity>(null);
    private boolean providerSelectionDone;
    private boolean panelExpanded;
    private boolean botRunning;
    private boolean pendingClick;
    private String pendingKey;
    private long pendingAt;
    private long lastClickedAt;
    private long lastRefreshAt;
    private String diagnostic = "OPEN BUY SCREEN";
    private int adapterCount;
    private int visibleRows;

    public ArbNativeBuyController(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences p = prefs();
        this.botRunning = p.getBoolean(PREF_BOT, false);
        // Never resume transaction clicks after a process restart.
        if (this.botRunning) {
            this.botRunning = false;
            p.edit().putBoolean(PREF_BOT, false).apply();
        }
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isBuyActivity(Activity activity) {
        return activity != null && BUY_ACTIVITY.equals(activity.getClass().getName());
    }

    public void onActivityResumed(Activity activity) {
        currentActivity = new WeakReference<Activity>(activity);
        if (isBuyActivity(activity)) {
            buyActivityRef = new WeakReference<Activity>(activity);
            installPanel(activity);
            scanBuyActivity(activity);
        } else if (botRunning) {
            handlePaymentOrBackground(activity);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (currentActivity.get() == activity) currentActivity.clear();
    }

    public void onActivityDestroyed(Activity activity) {
        if (buyActivityRef.get() == activity) buyActivityRef.clear();
        if (currentActivity.get() == activity) currentActivity.clear();
    }

    public boolean isEnabled() {
        return prefs().getBoolean(PREF_ENABLED, false);
    }

    public String getDiagnostic() { return diagnostic; }
    public int getAdapterCount() { return adapterCount; }
    public boolean isBotRunning() { return botRunning; }
    public int getVisibleRows() { return visibleRows; }

    public long getScanIntervalMs() {
        int value = prefs().getInt(PREF_LATENCY, 200);
        return Math.max(80, Math.min(500, value));
    }

    public long getRefreshIntervalMs() {
        int value = prefs().getInt(PREF_REFRESH_MS, 1000);
        return Math.max(10, Math.min(60000, value));
    }

    public void onExtensionToggled(Activity activity, boolean enabled) {
        if (!enabled && botRunning) stopBot("EXTENSION OFF");
        if (activity != null && isBuyActivity(activity)) {
            installPanel(activity);
            updatePanelStatus();
        }
    }

    public void openSettings(Activity activity) {
        if (activity == null || !isBuyActivity(activity)) {
            Toast.makeText(activity == null ? appContext : activity,
                    "Open the Buy screen to configure ARB Bot", Toast.LENGTH_SHORT).show();
            return;
        }
        installPanel(activity);
        View panel = panelRef.get();
        if (panel != null) {
            panelExpanded = !panelExpanded;
            panel.setVisibility(panelExpanded ? View.VISIBLE : View.GONE);
            updatePanelStatus();
        }
    }

    public void scan(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!isEnabled()) {
            if (botRunning) stopBot("EXTENSION OFF");
            diagnostic = "EXTENSION OFF";
            updatePanelStatus();
            return;
        }
        if (isBuyActivity(activity)) {
            buyActivityRef = new WeakReference<Activity>(activity);
            scanBuyActivity(activity);
        } else if (botRunning) {
            handlePaymentOrBackground(activity);
        }
    }

    private void scanBuyActivity(Activity activity) {
        if (activity == null || activity.isFinishing() || !botRunning) return;
        ImageView onBuyQr = findQrImageView(activity.getWindow().getDecorView());
        if (onBuyQr != null && onBuyQr.isShown() && isQrPaymentScreen(activity, activity.getWindow().getDecorView())) {
            stopBot("QR SCREEN FOUND · BOT STOPPED");
            ArbQrSaveOverlay.show(activity, onBuyQr);
            return;
        }
        if (System.currentTimeMillis() - lastRefreshAt >= getRefreshIntervalMs()) refreshVisibleBuyFragment(activity);
        Object fragment = findOtpUpiFragmentFromActivity(activity);
        Object fragmentView = invokeObject(fragment, "getView");
        RecyclerScan result = new RecyclerScan();
        if (fragmentView instanceof View) scanView((View) fragmentView, result);
        if (result.adapterCount == 0) scanView(activity.getWindow().getDecorView(), result);
        adapterCount = result.adapterCount;
        visibleRows = result.visibleRows;
        diagnostic = adapterCount == 0 ? "OTP UPI LIST SEARCHING" : "OTP UPI LIST FOUND · " + adapterCount;
        runBot(activity, result.lists);
        updatePanelStatus();
    }

    private void handlePaymentOrBackground(Activity activity) {
        if (activity == null || activity.isFinishing() || !botRunning) return;
        ImageView qr = findQrImageView(activity.getWindow().getDecorView());
        if (qr != null && qr.isShown() && isQrPaymentScreen(activity, activity.getWindow().getDecorView())) {
            stopBot("QR SCREEN FOUND · BOT STOPPED");
            ArbQrSaveOverlay.show(activity, qr);
            return;
        }
        if (pendingClick) {
            selectConfiguredProvider(activity);
        } else {
            Activity buy = buyActivityRef.get();
            if (buy != null && !buy.isFinishing()) scanBuyActivity(buy);
        }
    }

    private Object findOtpUpiFragmentFromActivity(Activity activity) {
        try {
            Method getter = findMethod(activity.getClass(), "getSupportFragmentManager", 0);
            if (getter == null) return null;
            getter.setAccessible(true);
            return findOtpUpiFragment(getter.invoke(activity));
        } catch (Exception ignored) { return null; }
    }

    private Object findOtpUpiFragment(Object manager) {
        if (manager == null) return null;
        try {
            Object value = manager.getClass().getMethod("getFragments").invoke(manager);
            if (!(value instanceof List)) return null;
            List<?> fragments = (List<?>) value;
            for (int i = fragments.size() - 1; i >= 0; i--) {
                Object fragment = fragments.get(i);
                if (fragment == null) continue;
                if (fragment.getClass().getName().endsWith(".OtpUPIBuyFragment")) return fragment;
                Object nested = findOtpUpiFragment(invokeObject(fragment, "getChildFragmentManager"));
                if (nested != null) return nested;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean isQrPaymentScreen(Activity activity, View root) {
        String name = activity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        boolean qrName = name.contains("qr");
        boolean qrText = containsScreenText(root, "scan to pay") || containsScreenText(root, "scan qr")
                || containsScreenText(root, "qr code") || containsScreenText(root, "payment qr");
        return qrName || qrText;
    }

    private boolean containsScreenText(View view, String needle) {
        if (view == null) return false;
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.toString().toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
        }
        CharSequence cd = view.getContentDescription();
        if (cd != null && cd.toString().toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i=0;i<g.getChildCount();i++) if (containsScreenText(g.getChildAt(i), needle)) return true;
        }
        return false;
    }

    private ImageView findQrImageView(View view) {
        if (view == null) return null;
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            int w = image.getWidth(), h = image.getHeight();
            float density = image.getResources().getDisplayMetrics().density;
            if (w >= (int)(120 * density) && h >= (int)(120 * density)
                    && Math.abs(w-h) <= Math.max(w,h)/4 && image.getDrawable() != null) return image;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            ImageView best = null;
            int bestArea = 0;
            for (int i=0;i<g.getChildCount();i++) {
                ImageView found = findQrImageView(g.getChildAt(i));
                if (found != null) {
                    int area = found.getWidth() * found.getHeight();
                    if (area > bestArea) { best=found; bestArea=area; }
                }
            }
            return best;
        }
        return null;
    }

    private void selectConfiguredProvider(Activity activity) {
        if (providerSelectionDone) return;
        String screen = activity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        View root = activity.getWindow().getDecorView();
        if (!(screen.contains("pay") || screen.contains("payment") || screen.contains("order")
                || containsScreenText(root, "choose payment") || containsScreenText(root, "payment method"))) return;
        String provider = prefs().getString(PREF_PROVIDER, "PhonePe");
        if (provider == null || provider.length() == 0 || "Other".equals(provider)) return;
        View option = findClickableText(root, provider, null);
        if (option != null && option.isEnabled()) {
            providerSelectionDone = option.performClick();
            if (providerSelectionDone) diagnostic = "PROVIDER SELECTED · " + provider;
        }
    }

    private View findClickableText(View view, String text, View clickableParent) {
        if (view == null) return null;
        View parent = view.isClickable() ? view : clickableParent;
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.toString().toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT)))
                return parent;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i=0;i<g.getChildCount();i++) {
                View found = findClickableText(g.getChildAt(i), text, parent);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Refresh the app's own currently visible Buy fragment rather than simulating
     * a swipe gesture. OTP-UPI/BANK implement refreshBuyList(); AutoBuy and UPI
     * reload through their existing BuyViewModel.getBuyLists(...) method.
     */
    private void refreshVisibleBuyFragment(Activity activity) {
        try {
            Method managerGetter = findMethod(activity.getClass(), "getSupportFragmentManager", 0);
            if (managerGetter == null) return;
            managerGetter.setAccessible(true);
            Object manager = managerGetter.invoke(activity);
            Object fragment = findOtpUpiFragment(manager);
            if (fragment == null) return;
            Object fragmentView = invokeObject(fragment, "getView");
            if (!(fragmentView instanceof View)) return;

            Method refresh = findMethod(fragment.getClass(), "refreshBuyList", 0);
            if (refresh != null && !"BaseBuyFragment".equals(refresh.getDeclaringClass().getSimpleName())) {
                refresh.setAccessible(true);
                refresh.invoke(fragment);
                lastRefreshAt = System.currentTimeMillis();
                diagnostic = "AUTO REFRESH · " + fragment.getClass().getSimpleName();
                return;
            }

            // AutoBuy/UPI do not override BaseBuyFragment.refreshBuyList().
            Object viewModel = invokeObject(fragment, "getViewModel");
            if (viewModel == null) return;
            String max = invokeString(fragment, "getMMaxAmount");
            String min = invokeString(fragment, "getMMinAmount");
            String payment = invokeString(fragment, "getMPaymentType");
            int pageType = readIntField(fragment, "pageType", -1);
            if (pageType < 0) return;
            Method getLists = findMethod(viewModel.getClass(), "getBuyLists", 6);
            if (getLists == null) return;
            Class<?> callbackType = getLists.getParameterTypes()[5];
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackType.getClassLoader(), new Class<?>[]{callbackType},
                    new java.lang.reflect.InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("toString".equals(method.getName())) return "ArbAutoRefreshCallback";
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("equals".equals(method.getName())) return proxy == (args == null ? null : args[0]);
                            return null;
                        }
                    });
            getLists.setAccessible(true);
            boolean showLoading = !fragment.getClass().getSimpleName().contains("Auto");
            getLists.invoke(viewModel, max, min, payment, Integer.valueOf(pageType),
                    Boolean.valueOf(showLoading), callback);
            lastRefreshAt = System.currentTimeMillis();
            diagnostic = "AUTO REFRESH · " + fragment.getClass().getSimpleName();
        } catch (Exception e) {
            diagnostic = "AUTO REFRESH RETRY";
        }
    }

    private Object findVisibleBuyFragment(Object manager) {
        if (manager == null) return null;
        try {
            Object value = manager.getClass().getMethod("getFragments").invoke(manager);
            if (!(value instanceof List)) return null;
            List<?> fragments = (List<?>) value;
            for (int i = fragments.size() - 1; i >= 0; i--) {
                Object fragment = fragments.get(i);
                if (fragment == null) continue;
                if (!invokeBoolean(fragment, "isAdded") || invokeBoolean(fragment, "isDetached")
                        || !invokeBoolean(fragment, "isVisible")) continue;
                Object childManager = invokeObject(fragment, "getChildFragmentManager");
                Object nested = findVisibleBuyFragment(childManager);
                if (nested != null) return nested;
                String name = fragment.getClass().getName();
                Object view = invokeObject(fragment, "getView");
                if (name.startsWith("com.pt.ar.wallet.home.buy.fragment.")
                        && view instanceof View && ((View) view).isShown()) return fragment;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean invokeBoolean(Object target, String name) {
        Object value = invokeObject(target, name);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == parameterCount) return method;
            }
        }
        return null;
    }

    private Object invokeObject(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = findMethod(target.getClass(), name, 0);
            if (method == null) return null;
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) { return null; }
    }

    private int readIntField(Object target, String name, int fallback) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Exception ignored) { return fallback; }
    }

    private void runBot(Activity activity, List<Object> lists) {
        if (!botRunning || !isEnabled()) return;
        long now = System.currentTimeMillis();
        if (pendingClick) {
            // The app's own beforeBuy handler navigates to its payment/KYC activity on success.
            // If this screen is still visible after this timeout, treat the row as failed
            // (including "bought by someone else") and never try that same row again.
            if (now - pendingAt < 12000L) return;
            attemptedKeys.add(pendingKey);
            pendingClick = false;
            pendingKey = null;
            diagnostic = "NO RESPONSE · ROW SKIPPED";
        }

        for (Object recycler : lists) {
            try {
                List<?> items = getAdapterItems(recycler);
                if (items == null || items.isEmpty()) continue;
                for (int position = 0; position < items.size(); position++) {
                    Object item = items.get(position);
                    String amountText = invokeString(item, "getMaximumAmount");
                    if (amountText == null) amountText = invokeString(item, "getAmount");
                    BigDecimal amount = parseAmount(amountText);
                    if (amount == null || !matchesAmount(amount)) continue;

                    String key = itemKey(item, amountText);
                    if (attemptedKeys.contains(key)) continue;

                    View row = findChildForPosition(recycler, position);
                    if (row == null) {
                        invokeScrollToPosition(recycler, position);
                        diagnostic = "MATCH FOUND · SCROLLING";
                        return;
                    }
                    View buyButton = findBuyButton(row);
                    if (buyButton == null || !buyButton.isEnabled()) continue;
                    if (now - lastClickedAt < getScanIntervalMs()) return;

                    // Reuse the app's own visible Buy button and its existing validation/payment flow.
                    if (buyButton.performClick()) {
                        pendingClick = true;
                        providerSelectionDone = false;
                        pendingKey = key;
                        pendingAt = now;
                        lastClickedAt = now;
                        diagnostic = "BUY REQUEST SENT · WAITING";
                        return;
                    }
                }
            } catch (Exception ignored) {
                diagnostic = "BUY LIST READ ERROR";
            }
        }
        if (diagnostic == null || diagnostic.length() == 0
                || diagnostic.startsWith("BUY REQUEST") || diagnostic.startsWith("NO RESPONSE")) {
            diagnostic = "WAITING FOR MATCH";
        } else if (!diagnostic.startsWith("MATCH FOUND")) {
            diagnostic = "WAITING FOR MATCH";
        }
    }

    private boolean matchesAmount(BigDecimal amount) {
        SharedPreferences p = prefs();
        if ("range".equals(p.getString(PREF_MODE, "fixed"))) {
            BigDecimal min = parseAmount(p.getString(PREF_MIN, "500"));
            BigDecimal max = parseAmount(p.getString(PREF_MAX, "900"));
            return min != null && max != null && min.compareTo(max) <= 0
                    && amount.compareTo(min) >= 0 && amount.compareTo(max) <= 0;
        }
        BigDecimal fixed = parseAmount(p.getString(PREF_FIXED, "900"));
        return fixed != null && amount.compareTo(fixed) == 0;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null) return null;
        try {
            String normalized = value.trim().replace(",", "").replaceAll("[^0-9.]+", "");
            return normalized.length() == 0 ? null : new BigDecimal(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String itemKey(Object item, String amount) {
        return String.valueOf(invokeString(item, "getPlatformOrder")) + "|"
                + String.valueOf(invokeString(item, "getPayType")) + "|"
                + String.valueOf(invokeString(item, "getMinimumAmount")) + "|"
                + String.valueOf(amount);
    }

    private List<?> getAdapterItems(Object recycler) throws Exception {
        Object adapter = recycler.getClass().getMethod("getAdapter").invoke(recycler);
        if (adapter == null || !ADAPTER_CLASS.equals(adapter.getClass().getName())) return null;
        Field differField = adapter.getClass().getDeclaredField("differ");
        differField.setAccessible(true);
        Object differ = differField.get(adapter);
        if (differ == null) return null;
        Object current = differ.getClass().getMethod("getCurrentList").invoke(differ);
        return current instanceof List ? (List<?>) current : null;
    }

    private View findChildForPosition(Object recycler, int position) throws Exception {
        Method countMethod = recycler.getClass().getMethod("getChildCount");
        Method childMethod = recycler.getClass().getMethod("getChildAt", int.class);
        Method posMethod = recycler.getClass().getMethod("getChildAdapterPosition", View.class);
        int count = ((Integer) countMethod.invoke(recycler)).intValue();
        for (int i = 0; i < count; i++) {
            View child = (View) childMethod.invoke(recycler, i);
            int adapterPosition = ((Integer) posMethod.invoke(recycler, child)).intValue();
            if (adapterPosition == position) return child;
        }
        return null;
    }

    private void invokeScrollToPosition(Object recycler, int position) {
        try {
            recycler.getClass().getMethod("scrollToPosition", int.class).invoke(recycler, position);
        } catch (Exception ignored) { }
    }

    private String invokeString(Object target, String methodName) {
        if (target == null) return null;
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private View findBuyButton(View view) {
        if (view instanceof TextView) {
            CharSequence sequence = ((TextView) view).getText();
            String value = sequence == null ? "" : sequence.toString().trim();
            if ("buy".equalsIgnoreCase(value) && view.isClickable()) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBuyButton(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void scanView(View view, RecyclerScan scan) {
        if (view == null) return;
        String className = view.getClass().getName();
        if (className.startsWith("androidx.recyclerview.widget.RecyclerView")) {
            try {
                Object adapter = view.getClass().getMethod("getAdapter").invoke(view);
                if (adapter != null && ADAPTER_CLASS.equals(adapter.getClass().getName())) {
                    scan.adapterCount++;
                    int count = ((Integer) view.getClass().getMethod("getChildCount").invoke(view)).intValue();
                    scan.visibleRows += count;
                    scan.lists.add(view);
                }
            } catch (Exception ignored) { }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scanView(group.getChildAt(i), scan);
        }
    }

    private static final class RecyclerScan {
        int adapterCount;
        int visibleRows;
        final List<Object> lists = new ArrayList<Object>();
    }

    private void pauseForNextScreen() {
        // Kept for compatibility; normal in-app navigation no longer pauses the bot.
        diagnostic = "BACKGROUND OTP UPI SEARCH";
        updatePanelStatus();
    }

    private void stopBot(String reason) {
        if (pendingClick && pendingKey != null) attemptedKeys.add(pendingKey);
        pendingClick = false;
        pendingKey = null;
        botRunning = false;
        prefs().edit().putBoolean(PREF_BOT, false).apply();
        diagnostic = reason;
        updatePanelStatus();
    }

    private void installPanel(final Activity activity) {
        if (activity == null || !isBuyActivity(activity)) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        View existing = decor.findViewWithTag(PANEL_TAG);
        if (existing != null) {
            panelRef = new WeakReference<View>(existing);
            return;
        }

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag(PANEL_TAG);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
        panel.setElevation(dp(activity, 10));
        panel.setBackground(roundRect(Color.rgb(250, 250, 252), dp(activity, 12), Color.rgb(220, 222, 226)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = makeText(activity, "ARB BOT SETTINGS", 13, Color.rgb(28, 36, 48), true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 32), 1));
        TextView close = makeText(activity, "×", 22, Color.DKGRAY, true);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                View current = panelRef.get();
                if (current != null) current.setVisibility(View.GONE);
                panelExpanded = false;
            }
        });
        panel.addView(header);

        modeGroup = new RadioGroup(activity);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton fixed = new RadioButton(activity);
        fixed.setId(1001);
        fixed.setText("Fixed");
        RadioButton range = new RadioButton(activity);
        range.setId(1002);
        range.setText("Range");
        modeGroup.addView(fixed, new RadioGroup.LayoutParams(0, dp(activity, 36), 1));
        modeGroup.addView(range, new RadioGroup.LayoutParams(0, dp(activity, 36), 1));
        panel.addView(modeGroup);

        LinearLayout amountRow = new LinearLayout(activity);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        fixedInput = makeAmountInput(activity, "Amount");
        minInput = makeAmountInput(activity, "Min");
        maxInput = makeAmountInput(activity, "Max");
        amountRow.addView(fixedInput, new LinearLayout.LayoutParams(0, dp(activity, 42), 1));
        amountRow.addView(minInput, new LinearLayout.LayoutParams(0, dp(activity, 42), 1));
        amountRow.addView(maxInput, new LinearLayout.LayoutParams(0, dp(activity, 42), 1));
        panel.addView(amountRow);

        LinearLayout speedRow = new LinearLayout(activity);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.addView(makeText(activity, "Latency (ms)", 12, Color.DKGRAY, false),
                new LinearLayout.LayoutParams(0, dp(activity, 36), 1));
        latencyInput = makeAmountInput(activity, "200");
        speedRow.addView(latencyInput, new LinearLayout.LayoutParams(dp(activity, 86), dp(activity, 38)));
        panel.addView(speedRow);

        LinearLayout refreshRow = new LinearLayout(activity);
        refreshRow.setGravity(Gravity.CENTER_VERTICAL);
        refreshRow.addView(makeText(activity, "Refresh (ms)", 12, Color.DKGRAY, false),
                new LinearLayout.LayoutParams(0, dp(activity, 36), 1));
        refreshInput = makeAmountInput(activity, "1000");
        refreshInput.setText("1000");
        refreshRow.addView(refreshInput, new LinearLayout.LayoutParams(dp(activity, 86), dp(activity, 38)));
        panel.addView(refreshRow);

        LinearLayout providerRow = new LinearLayout(activity);
        providerRow.setGravity(Gravity.CENTER_VERTICAL);
        providerRow.addView(makeText(activity, "Payment Provider", 12, Color.DKGRAY, false),
                new LinearLayout.LayoutParams(0, dp(activity, 38), 1));
        providerInput = new Spinner(activity);
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_item, new String[]{"PhonePe", "Moneyview", "Other"});
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerInput.setAdapter(providerAdapter);
        providerRow.addView(providerInput, new LinearLayout.LayoutParams(dp(activity, 124), dp(activity, 42)));
        panel.addView(providerRow);

        panelStatus = makeText(activity, "Searching for the native Buy list…", 11, Color.rgb(80, 88, 98), false);
        panelStatus.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
        panel.addView(panelStatus);

        startStopButton = new Button(activity);
        startStopButton.setText("START BOT");
        panel.addView(startStopButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)));
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartStop(activity); }
        });

        loadSettingsIntoPanel();
        if (refreshInput != null) refreshInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                BigDecimal v = parseAmount(s == null ? "" : s.toString());
                if (v != null && v.compareTo(BigDecimal.valueOf(10)) >= 0) {
                    prefs().edit().putInt(PREF_REFRESH_MS, Math.max(10, Math.min(60000, v.intValue()))).apply();
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) { updateModeInputs(); }
        });
        updateModeInputs();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(activity, 270),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = dp(activity, 124);
        params.rightMargin = dp(activity, 10);
        panel.setVisibility(View.GONE);
        ((ViewGroup) decor).addView(panel, params);
        panelRef = new WeakReference<View>(panel);
        panelExpanded = false;
        updatePanelStatus();
    }

    private void loadSettingsIntoPanel() {
        SharedPreferences p = prefs();
        if (fixedInput != null) fixedInput.setText(p.getString(PREF_FIXED, "900"));
        if (minInput != null) minInput.setText(p.getString(PREF_MIN, "500"));
        if (maxInput != null) maxInput.setText(p.getString(PREF_MAX, "900"));
        if (latencyInput != null) latencyInput.setText(String.valueOf(p.getInt(PREF_LATENCY, 200)));
        if (refreshInput != null) refreshInput.setText(String.valueOf(p.getInt(PREF_REFRESH_MS, 1000)));
        if (providerInput != null) {
            String provider = p.getString(PREF_PROVIDER, "PhonePe");
            int index = "Moneyview".equals(provider) ? 1 : ("Other".equals(provider) ? 2 : 0);
            providerInput.setSelection(index);
        }
        if (modeGroup != null) modeGroup.check("range".equals(p.getString(PREF_MODE, "fixed")) ? 1002 : 1001);
    }

    private void updateModeInputs() {
        boolean range = modeGroup != null && modeGroup.getCheckedRadioButtonId() == 1002;
        if (fixedInput != null) fixedInput.setVisibility(range ? View.GONE : View.VISIBLE);
        if (minInput != null) minInput.setVisibility(range ? View.VISIBLE : View.GONE);
        if (maxInput != null) maxInput.setVisibility(range ? View.VISIBLE : View.GONE);
    }

    private void onStartStop(Activity activity) {
        if (botRunning) {
            stopBot("BOT STOPPED");
            return;
        }
        if (!isEnabled()) {
            Toast.makeText(activity, "Turn Extension ON first", Toast.LENGTH_SHORT).show();
            return;
        }
        String mode = modeGroup != null && modeGroup.getCheckedRadioButtonId() == 1002 ? "range" : "fixed";
        String fixed = fixedInput == null ? "900" : fixedInput.getText().toString().trim();
        String min = minInput == null ? "500" : minInput.getText().toString().trim();
        String max = maxInput == null ? "900" : maxInput.getText().toString().trim();
        String latency = latencyInput == null ? "200" : latencyInput.getText().toString().trim();
        String refresh = refreshInput == null ? "2500" : refreshInput.getText().toString().trim();
        BigDecimal latencyValue = parseAmount(latency);
        BigDecimal refreshValue = parseAmount(refresh);
        if (refreshValue == null || refreshValue.compareTo(BigDecimal.valueOf(10)) < 0) {
            Toast.makeText(activity, "Refresh minimum is 10 ms", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("range".equals(mode)) {
            BigDecimal lo = parseAmount(min), hi = parseAmount(max);
            if (lo == null || hi == null || lo.compareTo(BigDecimal.ZERO) <= 0 || lo.compareTo(hi) > 0) {
                Toast.makeText(activity, "Enter a valid Min/Max range", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            BigDecimal value = parseAmount(fixed);
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                Toast.makeText(activity, "Enter a valid fixed amount", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        int delay = latencyValue == null ? 200 : Math.max(80, Math.min(5000, latencyValue.intValue()));
        prefs().edit().putString(PREF_MODE, mode).putString(PREF_FIXED, fixed)
                .putString(PREF_MIN, min).putString(PREF_MAX, max)
                .putInt(PREF_LATENCY, delay)
                .putInt(PREF_REFRESH_MS, Math.max(10, Math.min(60000, refreshValue.intValue())))
                .putString(PREF_PROVIDER, providerInput == null ? "PhonePe" : String.valueOf(providerInput.getSelectedItem()))
                .putBoolean(PREF_BOT, true).apply();
        botRunning = true;
        providerSelectionDone = false;
        pendingClick = false;
        pendingKey = null;
        diagnostic = "BOT STARTED · SEARCHING";
        Toast.makeText(activity, "ARB Bot started. OTP UPI background search runs across app pages and stops at QR.", Toast.LENGTH_LONG).show();
        updatePanelStatus();
        scan(activity);
    }

    private void updatePanelStatus() {
        if (panelStatus != null) panelStatus.setText(diagnostic + "\nVisible rows: " + visibleRows + " · One order at a time");
        if (startStopButton != null) startStopButton.setText(botRunning ? "STOP BOT" : "START BOT");
    }

    private EditText makeAmountInput(Activity activity, String hint) {
        EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setTextSize(13f);
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setPadding(dp(activity, 7), 0, dp(activity, 7), 0);
        edit.setBackground(roundRect(Color.WHITE, dp(activity, 7), Color.rgb(208, 212, 218)));
        return edit;
    }

    private TextView makeText(Activity activity, String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        bg.setStroke(1, strokeColor);
        return bg;
    }

    private int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
