package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class CustomSettingsActivity extends Activity {
    private CustomPreferences preferences;
    private LinearLayout content;
    private String section = "general";
    private final String[] sections = {"general", "wallpaper", "favorites", "layout", "weather", "music", "about"};
    private int requestGeneration;
    private int previewGeneration;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(CustomPreferences.localized(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new CustomPreferences(this);
        if (state != null) section = state.getString("section", section);
        else if (getIntent().getStringExtra("section") != null) section = getIntent().getStringExtra("section");
        LinearLayout root = CustomUi.page(this, R.string.custom_settings);
        LinearLayout body = CustomUi.row(this); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView navScroll = new ScrollView(this);
        LinearLayout nav = CustomUi.column(this); nav.setPadding(0, CustomUi.dp(this, 10), CustomUi.dp(this, 16), 0);
        navScroll.addView(nav); body.addView(navScroll, new LinearLayout.LayoutParams(CustomUi.dp(this, 205), -1));
        int[] titles = {R.string.custom_general, R.string.custom_wallpaper, R.string.custom_favorites, R.string.custom_layout,
                R.string.custom_weather, R.string.custom_music, R.string.custom_about};
        for (int i = 0; i < sections.length; i++) {
            String target = sections[i];
            TextView choice = CustomUi.button(this, getText(titles[i]), v -> { section = target; render(); });
            choice.setGravity(Gravity.CENTER_VERTICAL); nav.addView(choice); CustomUi.margin(choice, 5);
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = CustomUi.column(this); content.setPadding(CustomUi.dp(this, 16), CustomUi.dp(this, 14), CustomUi.dp(this, 16), CustomUi.dp(this, 14));
        scroll.addView(content); body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        render();
    }
    @Override protected void onResume() { super.onResume(); if (content != null) render(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("section", section); }
    private TextView row(CharSequence title, Runnable action) {
        TextView v = CustomUi.button(this, title, view -> action.run()); v.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(v); CustomUi.margin(v, 8); return v;
    }
    private void note(int text) {
        TextView v = CustomUi.text(this, getText(text), 14); v.setTextColor(CustomUi.MUTED);
        v.setPadding(CustomUi.dp(this, 3), CustomUi.dp(this, 12), 0, CustomUi.dp(this, 9)); content.addView(v);
    }
    private void toggle(int label, String key, boolean value) {
        row(getString(label) + "  ·  " + CustomUi.onOff(this, value), () -> {
            preferences.prefs.edit().putBoolean(key, !value).apply(); render();
        });
    }
    private void render() {
        View focused = content.findFocus(); int focusedIndex = -1;
        for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i) == focused) focusedIndex = i;
        content.removeAllViews();
        int previewRequest = ++previewGeneration;
        switch (section) {
            case "wallpaper":
                android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
                android.widget.ImageView image = new android.widget.ImageView(this); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription(getText(R.string.custom_wallpaper)); frame.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
                View shade = new View(this); shade.setBackgroundColor(0xFF000000); shade.setAlpha(preferences.dim() / 100f);
                frame.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1)); content.addView(frame, new LinearLayout.LayoutParams(-1, CustomUi.dp(this, 150)));
                Context application = getApplicationContext(); String choice = preferences.wallpaper();
                CustomWallpaper.IO.execute(() -> {
                    android.graphics.Bitmap bitmap = CustomWallpaper.decode(application, choice, 640, 360);
                    runOnUiThread(() -> { if (!isDestroyed() && previewGeneration == previewRequest) image.setImageBitmap(bitmap); });
                });
                row(getText(R.string.custom_choose_wallpaper), () -> startActivity(new Intent(this, CustomWallpaperActivity.class)));
                note(R.string.custom_image_hint); break;
            case "favorites":
                row(getText(R.string.custom_manage_favorites), () -> startActivity(new Intent(this, CustomAppsActivity.class).putExtra(CustomAppsActivity.MANAGE, true)));
                row(getText(R.string.custom_all_apps), () -> startActivity(new Intent(this, CustomAppsActivity.class)));
                note(R.string.custom_home_hint); break;
            case "layout":
                row(getString(R.string.custom_columns, preferences.columns()), () -> new AlertDialog.Builder(this)
                        .setTitle(R.string.columns_label).setItems(new String[]{"4", "5", "6", "7"}, (dialog, i) -> {
                            preferences.prefs.edit().putInt("columns", i + 4).apply(); render();
                        }).show());
                note(R.string.custom_layout_hint);
                row(getText(R.string.custom_classic), () -> startActivity(new Intent(this, MainActivity.class)));
                break;
            case "weather":
                toggle(R.string.custom_show_weather, "weather_enabled", preferences.weather());
                row(getString(R.string.custom_city_value, preferences.city().isEmpty() ? getString(R.string.custom_not_set) : preferences.city()), this::city);
                row(getString(R.string.custom_units_value, preferences.fahrenheit() ? "°F" : "°C"), () -> {
                    preferences.prefs.edit().putBoolean("fahrenheit", !preferences.fahrenheit()).apply(); render();
                });
                row(getString(R.string.custom_refresh_value, preferences.weatherInterval() / 60000), () -> {
                    preferences.prefs.edit().putInt("weather_interval", preferences.weatherInterval() == 1800000L ? 60 : 30).apply(); render();
                });
                note(R.string.custom_weather_privacy);
                note(R.string.custom_weather_credit); break;
            case "music":
                toggle(R.string.custom_show_music, "music_enabled", preferences.music());
                row(getString(CustomMusic.access(this) ? R.string.custom_access_granted : R.string.custom_grant_access), this::notificationAccess);
                note(R.string.custom_music_privacy);
                note(R.string.custom_music_limitations); break;
            case "about":
                TextView title = CustomUi.text(this, "CleanYourTV Custom", 28); content.addView(title);
                note(R.string.custom_about_text);
                note(R.string.custom_test_build);
                row(getText(R.string.custom_licenses), () -> new AlertDialog.Builder(this).setTitle(R.string.custom_licenses)
                        .setMessage(getText(R.string.custom_license_text)).setPositiveButton(android.R.string.ok, null).show()); break;
            default:
                toggle(R.string.custom_24_hour, "clock_24h", preferences.prefs.getBoolean("clock_24h", true));
                row(getText(R.string.custom_language), () -> new AlertDialog.Builder(this).setTitle(R.string.custom_language)
                        .setItems(new String[]{getString(R.string.custom_system_language), "ไทย", "English"}, (dialog, which) -> {
                            preferences.prefs.edit().putString("language", new String[]{"", "th", "en"}[which]).apply(); recreate();
                        }).show());
                row(getText(R.string.custom_default_home), () -> openSettings(new Intent(Settings.ACTION_HOME_SETTINGS)));
                row(getText(R.string.custom_tv_settings), () -> openSettings(new Intent(Settings.ACTION_SETTINGS)));
                note(R.string.custom_parallel_hint); break;
        }
        if (focusedIndex >= 0 && content.getChildCount() > 0) content.getChildAt(Math.min(focusedIndex, content.getChildCount() - 1)).requestFocus();
    }
    private void city() {
        EditText input = new EditText(this); input.setTextColor(CustomUi.WHITE); input.setSingleLine(true);
        input.setHint(R.string.custom_city_hint); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setPadding(CustomUi.dp(this, 20), CustomUi.dp(this, 12), CustomUi.dp(this, 20), CustomUi.dp(this, 12));
        input.setText(preferences.city()); input.selectAll();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.custom_choose_city).setView(input)
                .setPositiveButton(R.string.custom_search, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) { input.setError(getString(R.string.custom_city_hint)); return; }
                int request = ++requestGeneration;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                CustomWeather.search(query, places -> {
                    if (isDestroyed() || isFinishing() || request != requestGeneration || !dialog.isShowing()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (places.isEmpty()) { input.setError(getString(R.string.custom_city_error)); return; }
                    dialog.dismiss();
                    String[] labels = new String[places.size()];
                    for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).name + " — " + places.get(i).detail;
                    new AlertDialog.Builder(this).setTitle(R.string.custom_choose_city).setItems(labels, (picker, index) -> {
                        CustomWeather.Place place = places.get(index);
                        preferences.prefs.edit().putString("city", place.name).putString("coordinates",
                                String.format(Locale.ROOT, "%.5f,%.5f", place.latitude, place.longitude))
                                .remove("weather_attempt").remove("weather_attempt_place").apply(); render();
                    }).setNegativeButton(android.R.string.cancel, null).show();
                });
            });
            input.requestFocus();
        }); dialog.show();
    }
    private void notificationAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        try { startActivity(intent); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.custom_access_unavailable, Toast.LENGTH_LONG).show(); }
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.custom_app_unavailable, Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onDestroy() { requestGeneration++; previewGeneration++; super.onDestroy(); }
}
