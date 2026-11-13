package com.arb.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String PREFS = "arb_browser_settings";
    private static final String PREF_HOME = "startup_website";
    private static final String DEFAULT_HOME = "https://www.google.com";
    private static final String EXTENSION_PATH = "resource://android/assets/extensions/arbbuyer/";
    private static final String EXTENSION_ID_ASSET = "extensions/extension_id.txt";

    private GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView browserView;
    private EditText addressBar;
    private TextView backButton;
    private TextView forwardButton;
    private TextView extensionStatus;
    private boolean extensionReady;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildBrowserUi();
        initialiseGecko();
    }

    private void buildBrowserUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(6), dp(6), dp(6), dp(6));
        toolbar.setBackgroundColor(Color.rgb(232, 237, 244));

        backButton = toolButton("‹");
        backButton.setOnClickListener(v -> {
            if (session != null) session.goBack();
        });
        toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(34), dp(42)));

        forwardButton = toolButton("›");
        forwardButton.setOnClickListener(v -> {
            if (session != null) session.goForward();
        });
        toolbar.addView(forwardButton, new LinearLayout.LayoutParams(dp(34), dp(42)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(14);
        addressBar.setHint("Enter website address");
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(10), 0, dp(10), 0);
        addressBar.setBackground(roundBackground(Color.WHITE));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        toolbar.addView(addressBar, addressParams);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openAddress(addressBar.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });

        TextView go = toolButton("GO");
        go.setTextSize(12);
        go.setOnClickListener(v -> {
            openAddress(addressBar.getText().toString());
            hideKeyboard();
        });
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView menu = toolButton("⋮");
        menu.setTextSize(22);
        menu.setOnClickListener(this::showMenu);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(34), dp(42)));

        extensionStatus = new TextView(this);
        extensionStatus.setText("Preparing XPI extension…");
        extensionStatus.setTextSize(11);
        extensionStatus.setTextColor(Color.rgb(79, 91, 108));
        extensionStatus.setPadding(dp(10), dp(3), dp(10), dp(3));
        extensionStatus.setBackgroundColor(Color.rgb(246, 248, 251));

        browserView = new GeckoView(this);
        browserView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(toolbar);
        root.addView(extensionStatus);
        root.addView(browserView);
        setContentView(root);

        String home = getHomeUrl();
        addressBar.setText(home);
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
    }

    private void initialiseGecko() {
        try {
            runtime = GeckoRuntime.create(this);
            session = new GeckoSession();
            session.open(runtime);
            browserView.setSession(session);
            session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(GeckoSession s, String url,
                        List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                        Boolean hasUserGesture) {
                    if (url != null) addressBar.setText(url);
                }

                @Override
                public void onCanGoBack(GeckoSession s, boolean canGoBack) {
                    backButton.setEnabled(canGoBack);
                    backButton.setAlpha(canGoBack ? 1.0f : 0.45f);
                }

                @Override
                public void onCanGoForward(GeckoSession s, boolean canGoForward) {
                    forwardButton.setEnabled(canGoForward);
                    forwardButton.setAlpha(canGoForward ? 1.0f : 0.45f);
                }
            });

            installBundledExtensionThenOpenHome();
        } catch (Throwable error) {
            extensionStatus.setText("Browser engine could not start");
            Toast.makeText(this, "GeckoView error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installBundledExtensionThenOpenHome() {
        final String extensionId = readExtensionId();
        if (extensionId.isEmpty()) {
            extensionStatus.setText("XPI extension package was not included");
            openHomePage();
            return;
        }
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_PATH, extensionId)
                .accept(extension -> runOnUiThread(() -> {
                    extensionReady = true;
                    String name = extension.metaData != null && extension.metaData.name != null
                            ? extension.metaData.name : "XPI extension";
                    extensionStatus.setText("Extension active: " + name);
                    openHomePage();
                }), error -> runOnUiThread(() -> {
                    extensionStatus.setText("Extension could not load");
                    Toast.makeText(this, "XPI error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    openHomePage();
                }));
    }

    private String readExtensionId() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(EXTENSION_ID_ASSET), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ignored) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getAssets().open(EXTENSION_ID_ASSET), StandardCharsets.UTF_8))) {
                return reader.readLine();
            } catch (Exception e) {
                return "";
            }
        }
    }

    private void openHomePage() {
        String url = getHomeUrl();
        addressBar.setText(url);
        if (session != null) session.loadUri(url);
    }

    private String getHomeUrl() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOME, DEFAULT_HOME);
    }

    private void openAddress(String raw) {
        String url = normaliseUrl(raw);
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter a valid website address", Toast.LENGTH_SHORT).show();
            return;
        }
        addressBar.setText(url);
        if (session != null) session.loadUri(url);
    }

    private String normaliseUrl(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        Uri parsed = Uri.parse(value);
        if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) return "";
        return value;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Startup Website");
        popup.getMenu().add("Extension Status");
        popup.getMenu().add("Reload");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Startup Website".equals(title)) showStartupSettings();
            else if ("Extension Status".equals(title)) showExtensionStatus();
            else if ("Reload".equals(title) && session != null) session.reload();
            return true;
        });
        popup.show();
    }

    private void showStartupSettings() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(getHomeUrl());
        input.setHint("https://example.com");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Startup Website")
                .setMessage("App open hote hi ye website automatically khulegi.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = normaliseUrl(input.getText().toString());
                    if (url.isEmpty()) {
                        Toast.makeText(this, "Valid website address daalo", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_HOME, url).apply();
                    addressBar.setText(url);
                    Toast.makeText(this, "Startup website saved", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showExtensionStatus() {
        if (runtime == null) return;
        runtime.getWebExtensionController().list().accept(extensions -> runOnUiThread(() -> {
            if (extensions == null || extensions.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("Extensions")
                        .setMessage("No extension installed").setPositiveButton("OK", null).show();
                return;
            }
            StringBuilder info = new StringBuilder();
            for (WebExtension extension : extensions) {
                info.append(extension.metaData != null && extension.metaData.name != null
                        ? extension.metaData.name : extension.id)
                    .append("\nVersion: ")
                    .append(extension.metaData == null ? "unknown" : extension.metaData.version)
                    .append("\nID: ").append(extension.id).append("\n\n");
            }
            new AlertDialog.Builder(this).setTitle("Installed XPI Extensions")
                    .setMessage(info.toString()).setPositiveButton("OK", null).show();
        }), error -> runOnUiThread(() ->
                Toast.makeText(this, "Could not read extensions: " + error.getMessage(), Toast.LENGTH_LONG).show()));
    }

    private TextView toolButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.rgb(35, 48, 64));
        button.setTextSize(20);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundBackground(Color.rgb(222, 229, 238)));
        button.setClickable(true);
        return button;
    }

    private android.graphics.drawable.GradientDrawable roundBackground(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(9));
        return shape;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
            addressBar.clearFocus();
        } catch (Exception ignored) { }
    }

    @Override
    public void onBackPressed() {
        if (session != null) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) session.setActive(true);
    }

    @Override
    protected void onPause() {
        if (session != null) session.setActive(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
