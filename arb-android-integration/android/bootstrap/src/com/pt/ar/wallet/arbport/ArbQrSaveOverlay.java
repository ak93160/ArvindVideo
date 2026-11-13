package com.pt.ar.wallet.arbport;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Draggable QR save button with a per-device persistent position. */
public final class ArbQrSaveOverlay {
    private static final String PREFS = "arb_extension_port";
    private static final String TAG = "ARB_QR_SAVE_BUTTON";
    private ArbQrSaveOverlay() { }

    public static void show(final Activity activity, final ImageView qrImage) {
        if (activity == null || qrImage == null || activity.isFinishing()) return;
        final View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        final FrameLayout root = (FrameLayout) decor;
        if (root.findViewWithTag(TAG) != null) return;

        final TextView button = new TextView(activity);
        button.setTag(TAG);
        button.setText("SAVE QR");
        button.setTextColor(Color.WHITE);
        button.setTextSize(11f);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 11), dp(activity, 8), dp(activity, 11), dp(activity, 8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(27, 105, 73));
        bg.setCornerRadius(dp(activity, 18));
        bg.setStroke(dp(activity, 1), Color.WHITE);
        button.setBackground(bg);
        button.setElevation(dp(activity, 8));
        button.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 38), Gravity.TOP | Gravity.LEFT);
        root.addView(button, lp);

        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(activity, qrImage, root); }
        });
        final float[] start = new float[4];
        final boolean[] moved = new boolean[]{false};
        final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    start[0] = event.getRawX(); start[1] = event.getRawY();
                    start[2] = v.getX(); start[3] = v.getY();
                    moved[0] = false; return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - start[0], dy = event.getRawY() - start[1];
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved[0] = true;
                    if (moved[0]) {
                        float maxX = Math.max(0, root.getWidth() - v.getWidth());
                        float maxY = Math.max(0, root.getHeight() - v.getHeight());
                        v.setX(Math.max(0, Math.min(maxX, start[2] + dx)));
                        v.setY(Math.max(0, Math.min(maxY, start[3] + dy)));
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float maxX = Math.max(1, root.getWidth() - v.getWidth());
                    float maxY = Math.max(1, root.getHeight() - v.getHeight());
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putFloat("qr_button_x", Math.max(0, Math.min(1, v.getX() / maxX)))
                            .putFloat("qr_button_y", Math.max(0, Math.min(1, v.getY() / maxY))).apply();
                    if (!moved[0]) v.performClick();
                    return true;
                }
                return true;
            }
        });
        root.post(new Runnable() {
            @Override public void run() {
                float x = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("qr_button_x", 0.78f);
                float y = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("qr_button_y", 0.70f);
                float maxX = Math.max(0, root.getWidth() - button.getWidth());
                float maxY = Math.max(0, root.getHeight() - button.getHeight());
                button.setX(Math.max(0, Math.min(maxX, x * maxX)));
                button.setY(Math.max(0, Math.min(maxY, y * maxY)));
            }
        });
    }

    private static void save(Activity activity, ImageView image, View root) {
        Bitmap qr = null, out = null;
        try {
            int w = image.getWidth(), h = image.getHeight();
            if (w < 2 || h < 2) throw new IllegalStateException("QR image is not ready");
            qr = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            image.draw(new Canvas(qr));
            String orderId = findOrderId(root);
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            int footer = dp(activity, 58);
            out = Bitmap.createBitmap(w, h + footer, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(qr, 0, 0, null);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(28, 35, 44));
            canvas.drawRect(0, h, w, h + footer, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(Math.max(12f, dp(activity, 12)));
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            canvas.drawText("Order: " + orderId, dp(activity, 10), h + dp(activity, 23), paint);
            paint.setTextSize(Math.max(10f, dp(activity, 10)));
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            canvas.drawText("Saved: " + stamp, dp(activity, 10), h + dp(activity, 43), paint);
            String fileName = "ARB_QR_" + orderId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ARB QR");
                Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Gallery could not create file");
                OutputStream stream = activity.getContentResolver().openOutputStream(uri);
                if (stream == null) throw new IllegalStateException("Gallery stream unavailable");
                try { out.compress(Bitmap.CompressFormat.PNG, 100, stream); } finally { stream.close(); }
            } else {
                File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ARB QR");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create QR folder");
                File file = new File(folder, fileName);
                FileOutputStream stream = new FileOutputStream(file);
                try { out.compress(Bitmap.CompressFormat.PNG, 100, stream); } finally { stream.close(); }
                android.media.MediaScannerConnection.scanFile(activity, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
            }
            Toast.makeText(activity, "QR saved to Pictures/ARB QR", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "QR save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (qr != null) qr.recycle();
            if (out != null) out.recycle();
        }
    }

    private static String findOrderId(View view) {
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null) {
                String s = cs.toString();
                if (s.toLowerCase(Locale.ROOT).contains("order")) {
                    Matcher m = Pattern.compile("(?i)order\\s*(?:id|no\\.?|number)?\\s*[:#-]?\\s*([A-Za-z0-9_-]{4,})").matcher(s);
                    if (m.find()) return m.group(1);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i=0;i<g.getChildCount();i++) {
                String found = findOrderId(g.getChildAt(i));
                if (!"unknown".equals(found)) return found;
            }
        }
        return "unknown";
    }

    private static int dp(Activity a, int value) {
        return (int)(value * a.getResources().getDisplayMetrics().density + 0.5f);
    }
}
