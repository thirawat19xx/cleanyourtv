package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class CustomWallpaperActivity extends Activity {
    private static final int PICK = 41;
    private CustomPreferences preferences;
    private ImageView preview;
    private View dim;
    private String selected;
    private TextView apply;
    private int generation;
    private boolean importing;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(CustomPreferences.localized(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new CustomPreferences(this); selected = preferences.wallpaper();
        LinearLayout root = CustomUi.page(this, R.string.custom_wallpaper);
        LinearLayout body = CustomUi.row(this); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout choices = CustomUi.column(this);
        choices.setPadding(0, CustomUi.dp(this, 12), CustomUi.dp(this, 20), 0);
        body.addView(choices, new LinearLayout.LayoutParams(0, -1, 1));
        for (int i = 0; i < CustomWallpaper.BUILTINS.length; i++) {
            String key = "builtin:" + i;
            TextView b = CustomUi.button(this, getResources().getStringArray(R.array.custom_wallpaper_names)[i], v -> { selected = key; showPreview(); });
            b.setGravity(android.view.Gravity.CENTER_VERTICAL);
            b.setCompoundDrawablePadding(CustomUi.dp(this, 10));
            choices.addView(b); CustomUi.margin(b, 7);
            Context application = getApplicationContext();
            CustomWallpaper.IO.execute(() -> {
                Bitmap thumbnail = CustomWallpaper.decode(application, key, 160, 90);
                runOnUiThread(() -> {
                    if (isDestroyed() || thumbnail == null) return;
                    android.graphics.drawable.BitmapDrawable art = new android.graphics.drawable.BitmapDrawable(getResources(), thumbnail);
                    art.setBounds(0, 0, CustomUi.dp(this, 68), CustomUi.dp(this, 40)); b.setCompoundDrawables(art, null, null, null);
                });
            });
        }
        if (new java.io.File(getFilesDir(), "custom-wallpaper.jpg").isFile()) {
            TextView saved = CustomUi.button(this, getText(R.string.custom_saved_image), v -> { selected = "imported"; showPreview(); });
            choices.addView(saved); CustomUi.margin(saved, 7);
        }
        TextView pick = CustomUi.button(this, getText(R.string.custom_import_image), v -> pick());
        choices.addView(pick); CustomUi.margin(pick, 7);
        TextView reset = CustomUi.button(this, getText(R.string.custom_reset_wallpaper), v -> { selected = "builtin:0"; showPreview(); });
        choices.addView(reset); CustomUi.margin(reset, 7);
        LinearLayout details = CustomUi.column(this); body.addView(details, new LinearLayout.LayoutParams(0, -1, 2));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(preview, new android.widget.FrameLayout.LayoutParams(-1, -1));
        dim = new View(this); dim.setBackgroundColor(0xFF000000); dim.setAlpha(preferences.dim() / 100f);
        frame.addView(dim, new android.widget.FrameLayout.LayoutParams(-1, -1));
        details.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView label = CustomUi.text(this, getString(R.string.custom_wallpaper_dim, preferences.dim()), 15);
        details.addView(label);
        SeekBar slider = new SeekBar(this); slider.setMax(90); slider.setProgress(preferences.dim());
        slider.setContentDescription(getText(R.string.custom_dim));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                dim.setAlpha(value / 100f); label.setText(getString(R.string.custom_wallpaper_dim, value));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        }); details.addView(slider, new LinearLayout.LayoutParams(-1, CustomUi.dp(this, 42)));
        apply = CustomUi.button(this, getText(R.string.custom_apply_wallpaper), v -> apply(slider.getProgress()));
        details.addView(apply);
        TextView hint = CustomUi.text(this, getText(R.string.custom_image_hint), 12); hint.setTextColor(CustomUi.MUTED); root.addView(hint);
        showPreview();
    }
    private void showPreview() {
        int request = ++generation; String choice = selected; apply.setEnabled(false);
        Context context = getApplicationContext();
        CustomWallpaper.IO.execute(() -> {
            Bitmap bitmap = CustomWallpaper.decode(context, choice, 640, 360);
            runOnUiThread(() -> {
                if (isDestroyed() || request != generation) return;
                preview.setImageBitmap(bitmap); apply.setEnabled(bitmap != null && !importing);
                if (bitmap == null) Toast.makeText(this, R.string.custom_image_error, Toast.LENGTH_LONG).show();
            });
        });
    }
    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
        try { startActivityForResult(intent, PICK); }
        catch (android.content.ActivityNotFoundException missing) {
            try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK); }
            catch (android.content.ActivityNotFoundException unavailable) { Toast.makeText(this, R.string.custom_no_file_picker, Toast.LENGTH_LONG).show(); }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        importing = true; apply.setEnabled(false); Context context = getApplicationContext();
        android.net.Uri uri = data.getData();
        CustomWallpaper.IO.execute(() -> {
            boolean ok;
            try { CustomWallpaper.importPreview(context, uri); ok = true; } catch (Exception e) { ok = false; }
            boolean success = ok;
            runOnUiThread(() -> {
                if (isDestroyed()) return; importing = false;
                if (success) { selected = "pending"; showPreview(); }
                else { showPreview(); Toast.makeText(this, R.string.custom_image_error, Toast.LENGTH_LONG).show(); }
            });
        });
    }
    private void apply(int shade) {
        if (importing) return;
        apply.setEnabled(false); String choice = selected; Context context = getApplicationContext();
        CustomWallpaper.IO.execute(() -> {
            boolean ok = true;
            if ("pending".equals(choice)) try { CustomWallpaper.applyImport(context); } catch (Exception e) { ok = false; }
            if (ok) new CustomPreferences(context).prefs.edit().putString("wallpaper", "pending".equals(choice) ? "imported" : choice)
                    .putInt("wallpaper_dim", shade).putLong("wallpaper_revision", System.currentTimeMillis()).apply();
            boolean success = ok;
            runOnUiThread(() -> { if (isDestroyed()) return; if (success) finish(); else { apply.setEnabled(true); Toast.makeText(this, R.string.custom_image_error, Toast.LENGTH_LONG).show(); } });
        });
    }
    @Override protected void onDestroy() { generation++; preview.setImageDrawable(null); super.onDestroy(); }
}
