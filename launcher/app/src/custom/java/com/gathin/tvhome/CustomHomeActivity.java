package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The fork's remote-first home. All network/image work is bounded and off the UI thread. */
public final class CustomHomeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private CustomPreferences preferences;
    private ImageView wallpaper, album;
    private View shade;
    private TextView clock, date, weatherCity, weatherIcon, weatherTemperature, weatherDetails, weatherStatus;
    private TextView empty, song, artist, position, previous, playPause, next, allApps;
    private ProgressBar progress;
    private LinearLayout media;
    private RecyclerView favorites;
    private CustomAppAdapter adapter;
    private List<CustomAppRepository.App> installed = new ArrayList<>();
    private CustomMusic music;
    private MediaController controller;
    private MediaMetadata artworkMetadata;
    private boolean active, receiverRegistered, weatherBusy;
    private int generation, wallpaperGeneration, artworkGeneration;
    private String returnKey, language;
    private String wallpaperKey = "";
    private final Runnable minute = new Runnable() {
        @Override public void run() { if (!active) return; clock(); weather(); main.postDelayed(this, 60000 - System.currentTimeMillis() % 60000); }
    };
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (active) loadApps(); }
    };
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(CustomPreferences.localized(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new CustomPreferences(this);
        language = preferences.prefs.getString("language", "");
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF142D51, 0xFF030913}));
        wallpaper = new ImageView(this); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x44030812, 0x10030812, 0xE3030812}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this); dim.setBackgroundColor(0xFF000000); dim.setTag("wallpaper_dim");
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = CustomUi.column(this);
        body.setPadding(dp(34), dp(14), dp(34), dp(12)); root.addView(body, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout nameRow = CustomUi.row(this);
        TextView name = CustomUi.text(this, "CleanYourTV", 19); name.setTypeface(null, Typeface.BOLD);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView settings = CustomUi.button(this, "⚙", v -> settings("general"));
        settings.setContentDescription(getText(R.string.custom_settings)); nameRow.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(38)));
        TextView wifi = CustomUi.button(this, "Wi-Fi", v -> {
            try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
            catch (RuntimeException unavailable) { settings("general"); }
        }); nameRow.addView(wifi, new LinearLayout.LayoutParams(dp(76), dp(38))); body.addView(nameRow);

        LinearLayout top = CustomUi.row(this);
        LinearLayout time = CustomUi.column(this); time.setGravity(Gravity.CENTER_VERTICAL);
        clock = CustomUi.text(this, "", 49); clock.setIncludeFontPadding(false);
        date = CustomUi.text(this, "", 15); date.setTextColor(CustomUi.MUTED);
        time.addView(clock); time.addView(date); top.addView(time, new LinearLayout.LayoutParams(dp(190), -1));
        LinearLayout weatherPanel = CustomUi.row(this); weatherPanel.setGravity(Gravity.CENTER_VERTICAL);
        weatherPanel.setPadding(dp(14), dp(8), dp(14), dp(8)); weatherPanel.setFocusable(true); weatherPanel.setClickable(true);
        weatherPanel.setBackground(CustomUi.focus(this)); weatherPanel.setOnClickListener(v -> settings("weather"));
        weatherPanel.setContentDescription(getText(R.string.custom_weather)); weatherPanel.setTag("weather_panel");
        LinearLayout conditions = CustomUi.column(this);
        weatherCity = CustomUi.text(this, "", 14); weatherTemperature = CustomUi.text(this, "", 27);
        LinearLayout temperature = CustomUi.row(this); weatherIcon = CustomUi.text(this, "⛅", 32);
        temperature.addView(weatherIcon, new LinearLayout.LayoutParams(dp(50), -2)); temperature.addView(weatherTemperature);
        conditions.addView(weatherCity); conditions.addView(temperature);
        weatherStatus = CustomUi.text(this, "", 12); weatherStatus.setTextColor(CustomUi.MUTED); conditions.addView(weatherStatus);
        weatherPanel.addView(conditions, new LinearLayout.LayoutParams(0, -2, 1));
        weatherDetails = CustomUi.text(this, "", 14); weatherPanel.addView(weatherDetails, new LinearLayout.LayoutParams(-2, -2));
        int weatherWidth = Math.min(dp(390), getResources().getDisplayMetrics().widthPixels - dp(68 + 190));
        top.addView(weatherPanel, new LinearLayout.LayoutParams(weatherWidth, -1));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        body.addView(top, new LinearLayout.LayoutParams(-1, dp(98)));
        body.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        media = CustomUi.row(this); media.setVisibility(View.GONE);
        media.setPadding(dp(14), dp(9), dp(14), dp(9)); media.setBackground(CustomUi.panel(this, 0xD21B2D4B, 0x858CAACF));
        album = new ImageView(this); album.setScaleType(ImageView.ScaleType.CENTER_CROP);
        album.setBackgroundColor(0xFF123844); album.setContentDescription(getText(R.string.custom_album_art));
        media.addView(album, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout track = CustomUi.column(this); track.setGravity(Gravity.CENTER_VERTICAL);
        track.setPadding(dp(14), 0, dp(16), 0);
        TextView spotify = CustomUi.text(this, "Spotify", 11); spotify.setTextColor(0xFF6AE9A0); track.addView(spotify);
        song = CustomUi.text(this, "", 18); song.setTypeface(null, Typeface.BOLD); song.setSingleLine(true);
        song.setEllipsize(android.text.TextUtils.TruncateAt.END); track.addView(song);
        artist = CustomUi.text(this, "", 13); artist.setSingleLine(true); artist.setTextColor(CustomUi.MUTED);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END); track.addView(artist);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setProgressTintList(ColorStateList.valueOf(CustomUi.ACCENT));
        track.addView(progress, new LinearLayout.LayoutParams(-1, dp(9)));
        position = CustomUi.text(this, "", 11); position.setTextColor(CustomUi.MUTED); track.addView(position);
        media.addView(track, new LinearLayout.LayoutParams(0, -1, 1));
        previous = control("|‹", R.string.custom_previous, 0); playPause = control("▶", R.string.custom_play_pause, 1);
        next = control("›|", R.string.custom_next, 2);
        media.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(52)));
        media.addView(playPause, new LinearLayout.LayoutParams(dp(60), dp(60)));
        media.addView(next, new LinearLayout.LayoutParams(dp(52), dp(52)));
        body.addView(media, new LinearLayout.LayoutParams(-1, dp(94)));
        TextView heading = CustomUi.text(this, "★  " + getString(R.string.custom_favorites), 19);
        heading.setPadding(0, dp(5), 0, 0); body.addView(heading, new LinearLayout.LayoutParams(-1, dp(31)));
        LinearLayout appRow = CustomUi.row(this);
        FrameLayout strip = new FrameLayout(this);
        favorites = new RecyclerView(this); favorites.setItemAnimator(null);
        favorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new CustomAppAdapter(preferences, true, false,
                app -> { returnKey = app.key; CustomAppRepository.open(this, app); }, this::appMenu);
        favorites.setAdapter(adapter); strip.addView(favorites, new FrameLayout.LayoutParams(-1, -1));
        empty = CustomUi.text(this, getText(R.string.custom_empty_favorites), 15); empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setTextColor(CustomUi.MUTED); strip.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        appRow.addView(strip, new LinearLayout.LayoutParams(0, -1, 1));
        TextView add = CustomUi.button(this, "+\n" + getString(R.string.custom_add_favorite), v -> manage());
        add.setTextSize(14); appRow.addView(add, new LinearLayout.LayoutParams(dp(116), dp(94)));
        body.addView(appRow, new LinearLayout.LayoutParams(-1, dp(111)));
        allApps = CustomUi.button(this, "▦  " + getString(R.string.custom_all_apps) + "   ⌄", v -> catalog());
        allApps.setGravity(Gravity.CENTER_VERTICAL); body.addView(allApps, new LinearLayout.LayoutParams(-1, dp(42)));
        setContentView(root);
        music = new CustomMusic(this, this::music);
    }
    private int dp(int value) { return CustomUi.dp(this, value); }
    private TextView control(String label, int description, int action) {
        TextView button = CustomUi.button(this, label, v -> transport(action)); button.setTextSize(24);
        button.setContentDescription(getText(description)); return button;
    }
    @Override protected void onResume() {
        super.onResume();
        if (!language.equals(preferences.prefs.getString("language", ""))) { recreate(); return; }
        active = true;
        loadApps(); wallpaper();
        View weatherPanel = getWindow().getDecorView().findViewWithTag("weather_panel");
        weatherPanel.setVisibility(preferences.weather() ? View.VISIBLE : View.INVISIBLE);
        clock(); weather(); main.removeCallbacks(minute); main.postDelayed(minute, 60000 - System.currentTimeMillis() % 60000);
        if (preferences.music()) music.start(); else { music.stop(); music(null); }
        if (!receiverRegistered) {
            IntentFilter packages = new IntentFilter(); packages.addAction(Intent.ACTION_PACKAGE_ADDED); packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addAction(Intent.ACTION_PACKAGE_REPLACED); packages.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(packageChanges, packages, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageChanges, packages);
            }
            receiverRegistered = true;
        }
    }
    private void clock() {
        Date now = new Date(); boolean format24 = preferences.prefs.getBoolean("clock_24h", true);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        clock.setText(new SimpleDateFormat(format24 ? "HH:mm" : "h:mm a", locale).format(now));
        date.setText(DateFormat.getDateInstance(DateFormat.FULL, locale).format(now));
    }
    private void loadApps() {
        int request = ++generation;
        CustomAppRepository.load(this, apps -> {
            if (!active || isDestroyed() || request != generation) return;
            installed = apps; renderApps();
        });
    }
    private void renderApps() {
        adapter.submit(CustomAppRepository.ordered(installed, preferences.favorites(), true));
        empty.setVisibility(adapter.apps.isEmpty() ? View.VISIBLE : View.GONE);
        favorites.post(() -> {
            if (!active) return;
            int index = 0;
            for (int i = 0; i < adapter.apps.size(); i++) if (adapter.apps.get(i).key.equals(returnKey)) index = i;
            if (adapter.apps.isEmpty()) { allApps.requestFocus(); return; }
            favorites.scrollToPosition(index); int target = index;
            favorites.post(() -> { RecyclerView.ViewHolder holder = favorites.findViewHolderForAdapterPosition(target); if (holder != null && active) holder.itemView.requestFocus(); });
        });
    }
    private void wallpaper() {
        View dim = getWindow().getDecorView().findViewWithTag("wallpaper_dim"); dim.setAlpha(preferences.dim() / 100f);
        String key = preferences.wallpaper() + ":" + preferences.prefs.getLong("wallpaper_revision", 0);
        if (key.equals(wallpaperKey) && wallpaper.getDrawable() != null) return;
        wallpaperKey = key; int request = ++wallpaperGeneration; String choice = preferences.wallpaper();
        int w = Math.min(1920, getResources().getDisplayMetrics().widthPixels), h = Math.min(1080, getResources().getDisplayMetrics().heightPixels);
        Context context = getApplicationContext();
        CustomWallpaper.IO.execute(() -> {
            Bitmap bitmap = CustomWallpaper.decode(context, choice, w, h);
            main.post(() -> { if (isDestroyed() || request != wallpaperGeneration) return; wallpaper.setImageBitmap(bitmap); });
        });
    }
    private void weather() {
        if (!preferences.weather() || weatherBusy || !active) return;
        weatherBusy = true;
        CustomWeather.refresh(this, result -> {
            weatherBusy = false; if (!active || isDestroyed()) return;
            if (!preferences.city().equals(result.city)) { weather(); return; }
            weatherCity.setText(result.city.isEmpty() ? getText(R.string.custom_choose_city) : "⌖  " + result.city);
            CustomWeather.Snapshot s = result.snapshot;
            if (s == null) {
                weatherTemperature.setText("—"); weatherDetails.setText(""); weatherIcon.setText("⛅");
                weatherStatus.setText(result.city.isEmpty() ? R.string.custom_weather_setup : R.string.custom_weather_offline); return;
            }
            boolean f = preferences.fahrenheit(); String unit = f ? "°F" : "°C";
            weatherIcon.setText(CustomWeatherData.icon(s.code, s.day));
            weatherTemperature.setText(CustomWeatherData.degrees(s.temperature, f) + unit);
            double wind = f ? s.wind / 1.609344 : s.wind;
            weatherDetails.setText(getString(R.string.custom_weather_detail, CustomWeatherData.degrees(s.high, f), CustomWeatherData.degrees(s.low, f),
                    s.humidity, Math.round(wind), getString(f ? R.string.custom_mph : R.string.custom_kmh)));
            String label = getString(CustomWeather.description(s.code, s.day));
            String updated = new SimpleDateFormat("HH:mm", getResources().getConfiguration().getLocales().get(0)).format(new Date(s.fetched));
            weatherStatus.setText(label + " · " + getString(result.offline ? R.string.custom_cached_at : R.string.custom_updated_at, updated));
        });
    }
    private void music(MediaController value) {
        controller = value;
        if (value == null) {
            if (media.hasFocus()) allApps.requestFocus();
            media.setVisibility(View.GONE); album.setImageDrawable(null); artworkMetadata = null; artworkGeneration++; return;
        }
        media.setVisibility(View.VISIBLE);
        MediaMetadata metadata = value.getMetadata(); PlaybackState state = value.getPlaybackState();
        song.setText(metadata == null || metadata.getText(MediaMetadata.METADATA_KEY_TITLE) == null ? "Spotify" : metadata.getText(MediaMetadata.METADATA_KEY_TITLE));
        artist.setText(metadata == null ? "" : metadata.getText(MediaMetadata.METADATA_KEY_ARTIST));
        if (metadata != artworkMetadata) {
            artworkMetadata = metadata; album.setImageDrawable(null); int request = ++artworkGeneration;
            Bitmap cover = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (cover == null && metadata != null) cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (cover == null && metadata != null) cover = metadata.getDescription().getIconBitmap();
            Bitmap source = cover;
            if (source != null) CustomWallpaper.IO.execute(() -> {
                Bitmap small = null;
                try { small = Bitmap.createScaledBitmap(source, Math.min(192, source.getWidth()), Math.min(192, source.getHeight()), true); }
                catch (RuntimeException | OutOfMemoryError ignored) { }
                Bitmap result = small;
                main.post(() -> { if (!isDestroyed() && active && request == artworkGeneration) album.setImageBitmap(result); });
            });
        }
        long actions = state == null ? 0 : state.getActions();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        previous.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        next.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        playPause.setEnabled((actions & (PlaybackState.ACTION_PLAY_PAUSE | (playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY))) != 0);
        playPause.setText(playing ? "Ⅱ" : "▶");
        long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        progress.setVisibility(duration > 0 ? View.VISIBLE : View.GONE); position.setVisibility(duration > 0 ? View.VISIBLE : View.GONE);
        if (duration > 0) {
            long elapsed = CustomMusic.position(value, duration); progress.setProgress((int) Math.min(1000, elapsed * 1000.0 / duration));
            position.setText(time(elapsed) + "  /  " + time(duration));
        }
    }
    private static String time(long ms) { return String.format(Locale.ROOT, "%d:%02d", ms / 60000, ms / 1000 % 60); }
    private void transport(int action) {
        if (controller == null) return;
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (action == 0) controls.skipToPrevious();
            else if (action == 2) controls.skipToNext();
            else {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) controls.pause(); else controls.play();
            }
        } catch (RuntimeException unavailable) { music.refresh(); }
    }
    private void appMenu(CustomAppRepository.App app) {
        returnKey = app.key;
        new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{getString(R.string.custom_move_favorite),
                getString(R.string.custom_remove_favorite), getString(R.string.custom_open)}, (dialog, which) -> {
            if (which == 0) {
                List<String> order = preferences.favorites(); String[] labels = new String[adapter.apps.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ". " + adapter.apps.get(i).label;
                new AlertDialog.Builder(this).setTitle(R.string.custom_move_to).setItems(labels, (picker, at) -> {
                    // Keep unavailable favorites in storage; move relative to the visible target.
                    String target = adapter.apps.get(at).key;
                    int index = order.indexOf(target); order.remove(app.key); order.add(Math.max(0, Math.min(index, order.size())), app.key);
                    preferences.saveOrder("favorites", order); renderApps();
                }).show();
            } else if (which == 1) { preferences.favorite(app.key, false); renderApps(); }
            else CustomAppRepository.open(this, app);
        }).show();
    }
    private void settings(String section) { startActivity(new Intent(this, CustomSettingsActivity.class).putExtra("section", section)); }
    private void manage() { startActivity(new Intent(this, CustomAppsActivity.class).putExtra(CustomAppsActivity.MANAGE, true)); }
    private void rememberFocus() {
        View focused = getCurrentFocus();
        RecyclerView.ViewHolder holder = focused == null ? null : favorites.findContainingViewHolder(focused);
        if (holder != null) {
            int index = holder.getBindingAdapterPosition();
            if (index >= 0 && index < adapter.apps.size()) returnKey = adapter.apps.get(index).key;
        }
    }
    private void catalog() { rememberFocus(); startActivity(new Intent(this, CustomAppsActivity.class)); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && favorites.hasFocus()) {
            catalog(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() { if (!adapter.apps.isEmpty()) renderApps(); else allApps.requestFocus(); }
    @Override protected void onPause() {
        rememberFocus();
        active = false; main.removeCallbacks(minute); music.stop(); music(null); artworkGeneration++;
        if (receiverRegistered) { unregisterReceiver(packageChanges); receiverRegistered = false; }
        super.onPause();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (!active && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            wallpaper.setImageDrawable(null); album.setImageDrawable(null); artworkMetadata = null; CustomAppRepository.clearArt();
        }
    }
    @Override protected void onDestroy() {
        generation++; wallpaperGeneration++; artworkGeneration++; main.removeCallbacksAndMessages(null); music.stop();
        wallpaper.setImageDrawable(null); album.setImageDrawable(null); favorites.setAdapter(null); super.onDestroy();
    }
}
