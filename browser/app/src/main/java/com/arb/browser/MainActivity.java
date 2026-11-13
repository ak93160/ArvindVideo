package com.arb.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String PREFS = "arb_browser_settings";
    private static final String PREF_HOME = "startup_website";
    private static final String PREF_AMOUNT = "fixed_amount";
    private static final String PREF_PROVIDER = "payment_provider";
    private static final String DEFAULT_HOME = "https://www.google.com";
    private static final String EXTENSION_PATH = "resource://android/assets/extensions/arbbuyer/";
    private static final String EXTENSION_ID_ASSET = "extensions/extension_id.txt";
    private static final String NATIVE_APP = "arb.browser";
    private static final int PICK_XPI = 401;

    private static final String[] PROVIDER_LABELS = {
        "PhonePe", "Paytm", "FreeCharge", "Airtel", "Supermoney",
        "Freo", "TWID", "Navi", "MobiKwik", "MoneyView"
    };
    private static final String[] PROVIDER_CODES = {
        "bgphonepe", "bgpaytm", "bgfreeCharge", "bgairtel", "bgsupermoney",
        "bgfreo", "bgtwid", "bgnavi", "bgmobikwik", "bgmoneyView"
    };

    private GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView browserView;
    private EditText amountField;
    private Spinner providerSpinner;
    private TextView startButton;
    private TextView extensionStatus;
    private WebExtension.Port nativePort;
    private boolean extensionReady;
    private boolean botRunning;

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
        toolbar.setPadding(dp(5), dp(6), dp(5), dp(6));
        toolbar.setBackgroundColor(Color.rgb(232, 237, 244));

        TextView back = toolButton("‹", 21);
        back.setOnClickListener(v -> { if (session != null) session.goBack(); });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(30), dp(42)));

        TextView forward = toolButton("›", 21);
        forward.setOnClickListener(v -> { if (session != null) session.goForward(); });
        toolbar.addView(forward, new LinearLayout.LayoutParams(dp(30), dp(42)));

        amountField = new EditText(this);
        amountField.setSingleLine(true);
        amountField.setTextSize(14);
        amountField.setHint("Fixed ₹");
        amountField.setInputType(InputType.TYPE_CLASS_NUMBER);
        amountField.setPadding(dp(7), 0, dp(5), 0);
        amountField.setText(String.valueOf(getPrefs().getInt(PREF_AMOUNT, 900)));
        amountField.setBackground(roundBackground(Color.WHITE));
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(0, dp(42), 0.85f);
        amountParams.setMargins(dp(4), 0, dp(4), 0);
        toolbar.addView(amountField, amountParams);

        providerSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, PROVIDER_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);
        providerSpinner.setBackground(roundBackground(Color.WHITE));
        int savedProvider = getPrefs().getInt(PREF_PROVIDER, 0);
        providerSpinner.setSelection(Math.max(0, Math.min(savedProvider, PROVIDER_LABELS.length - 1)));
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(0, dp(42), 1.15f);
        providerParams.setMargins(dp(2), 0, dp(4), 0);
        toolbar.addView(providerSpinner, providerParams);

        startButton = toolButton("START", 12);
        startButton.setTextColor(Color.WHITE);
        startButton.setBackground(roundBackground(Color.rgb(24, 145, 83)));
        startButton.setOnClickListener(v -> toggleBot());
        toolbar.addView(startButton, new LinearLayout.LayoutParams(dp(62), dp(42)));

        TextView menu = toolButton("⋮", 22);
        menu.setOnClickListener(this::showMenu);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(30), dp(42)));

        extensionStatus = new TextView(this);
        extensionStatus.setText("Loading extension…");
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
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void initialiseGecko() {
        try {
            runtime = GeckoRuntime.create(this);
            session = new GeckoSession();
            session.open(runtime);
            browserView.setSession(session);
            installBundledExtensionThenOpenHome();
        } catch (Throwable error) {
            extensionStatus.setText("Browser engine could not start");
            Toast.makeText(this, "GeckoView error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installBundledExtensionThenOpenHome() {
        final String extensionId = readExtensionId();
        if (extensionId.isEmpty()) {
            extensionStatus.setText("Built-in extension package missing");
            openHomePage();
            return;
        }
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_PATH, extensionId)
                .accept(extension -> runOnUiThread(() -> {
                    extensionReady = true;
                    extension.setMessageDelegate(new WebExtension.MessageDelegate() {
                        @Override
                        public void onConnect(WebExtension.Port port) {
                            nativePort = port;
                            port.setDelegate(new WebExtension.PortDelegate() {
                                @Override
                                public void onPortMessage(Object message, WebExtension.Port source) {
                                    // Future extension-to-browser status messages.
                                }
                                @Override
                                public void onDisconnect(WebExtension.Port source) {
                                    if (nativePort == source) nativePort = null;
                                }
                            });
                            runOnUiThread(() -> extensionStatus.setText("Extension active · toolbar ready"));
                        }
                    }, NATIVE_APP);
                    extensionStatus.setText("Extension installed · connecting controls…");
                    openHomePage();
                }), error -> runOnUiThread(() -> {
                    extensionReady = false;
                    extensionStatus.setText("Extension could not load");
                    Toast.makeText(this, "XPI error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    openHomePage();
                }));
    }

    private String readExtensionId() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(getAssets().open(EXTENSION_ID_ASSET), "UTF-8"))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void openHomePage() {
        String url = getPrefs().getString(PREF_HOME, DEFAULT_HOME);
        if (session != null) session.loadUri(url);
    }

    private void toggleBot() {
        if (nativePort == null) {
            Toast.makeText(this, extensionReady
                    ? "Extension control channel is not ready yet"
                    : "Extension is not loaded", Toast.LENGTH_LONG).show();
            return;
        }
        String rawAmount = amountField.getText().toString().trim();
        int amount;
        try {
            amount = Integer.parseInt(rawAmount);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            amountField.setError("Enter a valid amount");
            return;
        }
        int providerIndex = providerSpinner.getSelectedItemPosition();
        providerIndex = Math.max(0, Math.min(providerIndex, PROVIDER_CODES.length - 1));
        getPrefs().edit().putInt(PREF_AMOUNT, amount).putInt(PREF_PROVIDER, providerIndex).apply();

        try {
            JSONObject message = new JSONObject();
            message.put("type", "ARB_BROWSER_SETTINGS");
            message.put("running", !botRunning);
            message.put("exactAmount", amount);
            message.put("minAmount", amount);
            message.put("maxAmount", amount);
            message.put("isAmountMode", true);
            message.put("paymentMethod", PROVIDER_CODES[providerIndex]);
            message.put("openPayment", true);
            message.put("soundEnabled", true);
            nativePort.postMessage(message);
            botRunning = !botRunning;
            updateStartButton();
            Toast.makeText(this, botRunning ? "Bot start command sent" : "Bot stop command sent",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not send bot settings: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateStartButton() {
        startButton.setText(botRunning ? "STOP" : "START");
        startButton.setBackground(roundBackground(botRunning
                ? Color.rgb(190, 52, 52) : Color.rgb(24, 145, 83)));
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Startup Website");
        popup.getMenu().add("Install Local XPI");
        popup.getMenu().add("Extension Status");
        popup.getMenu().add("Reload");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Startup Website".equals(title)) showStartupSettings();
            else if ("Install Local XPI".equals(title)) chooseLocalXpi();
            else if ("Extension Status".equals(title)) showExtensionStatus();
            else if ("Reload".equals(title) && session != null) session.reload();
            return true;
        });
        popup.show();
    }

    private void showStartupSettings() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(getPrefs().getString(PREF_HOME, DEFAULT_HOME));
        input.setHint("https://example.com");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Startup Website")
                .setMessage("App open hote hi ye website automatically khulegi.")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = normaliseUrl(input.getText().toString());
                    if (url.isEmpty()) {
                        Toast.makeText(this, "Valid website address daalo", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getPrefs().edit().putString(PREF_HOME, url).apply();
                    Toast.makeText(this, "Startup website saved", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String normaliseUrl(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        Uri parsed = Uri.parse(value);
        return parsed.getHost() == null || parsed.getHost().trim().isEmpty() ? "" : value;
    }

    private void chooseLocalXpi() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/x-xpinstall", "application/zip", "application/octet-stream"
        });
        startActivityForResult(intent, PICK_XPI);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_XPI || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri selected = data.getData();
        try {
            File localCopy = new File(getCacheDir(), "selected-extension.xpi");
            try (InputStream in = getContentResolver().openInputStream(selected);
                 FileOutputStream out = new FileOutputStream(localCopy)) {
                if (in == null) throw new IllegalStateException("Cannot read selected file");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            String fileUri = Uri.fromFile(localCopy).toString();
            extensionStatus.setText("Installing selected XPI…");
            runtime.getWebExtensionController()
                    .install(fileUri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
                    .accept(extension -> runOnUiThread(() -> {
                        extension.setMessageDelegate(new WebExtension.MessageDelegate() {
                            @Override
                            public void onConnect(WebExtension.Port port) {
                                nativePort = port;
                                port.setDelegate(new WebExtension.PortDelegate() {});
                            }
                        }, NATIVE_APP);
                        extensionStatus.setText("Local extension installed: " + extension.metaData.name);
                        Toast.makeText(this, "Extension installed", Toast.LENGTH_LONG).show();
                    }), error -> runOnUiThread(() -> {
                        extensionStatus.setText("Local XPI installation failed");
                        new AlertDialog.Builder(this).setTitle("XPI install failed")
                                .setMessage("GeckoView accepts locally selected XPI files only when Mozilla-signed.\n\n"
                                        + error.getMessage())
                                .setPositiveButton("OK", null).show();
                    }));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open XPI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                Toast.makeText(this, "Could not read extensions: " + error.getMessage(),
                        Toast.LENGTH_LONG).show()));
    }

    private TextView toolButton(String label, int size) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.rgb(35, 48, 64));
        button.setTextSize(size);
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

    @Override
    public void onBackPressed() {
        if (session != null) session.goBack();
        else super.onBackPressed();
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
        if (nativePort != null) {
            nativePort.disconnect();
            nativePort = null;
        }
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
