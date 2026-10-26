package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.List;
import java.util.function.Consumer;

final class CustomMusic {
    private final Context context;
    private final MediaSessionManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<MediaController> changed;
    private MediaController controller;
    private boolean started, listening;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::select;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { publish(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() { attach(null); refresh(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { publish(); }
    };

    CustomMusic(Context context, Consumer<MediaController> changed) {
        this.context = context.getApplicationContext(); this.changed = changed;
        manager = this.context.getSystemService(MediaSessionManager.class);
    }
    static boolean access(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        ComponentName wanted = new ComponentName(context, CustomMusicAccessService.class);
        if (enabled != null) for (String item : enabled.split(":")) if (wanted.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    void start() {
        stop(); started = true;
        if (manager == null || !access(context)) { changed.accept(null); return; }
        try {
            manager.addOnActiveSessionsChangedListener(sessions, new ComponentName(context, CustomMusicAccessService.class), main);
            listening = true; refresh();
        } catch (SecurityException | IllegalArgumentException unavailable) { changed.accept(null); }
    }
    void refresh() {
        if (!started || manager == null) return;
        try { select(manager.getActiveSessions(new ComponentName(context, CustomMusicAccessService.class))); }
        catch (SecurityException unavailable) { attach(null); }
    }
    private static boolean usable(MediaController c) {
        PlaybackState state = c.getPlaybackState();
        return state != null && state.getState() != PlaybackState.STATE_NONE
                && state.getState() != PlaybackState.STATE_STOPPED && state.getState() != PlaybackState.STATE_ERROR;
    }
    private void select(List<MediaController> sessions) {
        if (!started) return;
        MediaController chosen = null;
        if (sessions != null) for (MediaController candidate : sessions) {
            if (!CustomMediaPolicy.spotify(candidate.getPackageName()) || !usable(candidate)) continue;
            if (chosen == null) chosen = candidate;
            PlaybackState state = candidate.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) { chosen = candidate; break; }
        }
        attach(chosen);
    }
    private void attach(MediaController next) {
        if (controller != null && next != null && controller.getSessionToken().equals(next.getSessionToken())) { publish(); return; }
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) controller.registerCallback(callback, main);
        publish();
    }
    private void publish() {
        main.removeCallbacks(tick);
        if (!started) return;
        changed.accept(controller);
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) main.postDelayed(tick, 1000);
    }
    void stop() {
        started = false; main.removeCallbacksAndMessages(null);
        if (listening && manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        listening = false;
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }
    static long position(MediaController controller, long duration) {
        PlaybackState state = controller.getPlaybackState(); if (state == null) return 0;
        return CustomMediaPolicy.position(state.getPosition(), state.getLastPositionUpdateTime(), state.getPlaybackSpeed(),
                SystemClock.elapsedRealtime(), duration, state.getState() == PlaybackState.STATE_PLAYING);
    }
}
