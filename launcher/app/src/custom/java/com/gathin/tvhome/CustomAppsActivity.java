package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public final class CustomAppsActivity extends Activity {
    static final String MANAGE = "manage_favorites";
    private CustomPreferences preferences;
    private RecyclerView grid;
    private CustomAppAdapter adapter;
    private List<CustomAppRepository.App> installed = new ArrayList<>();
    private boolean manage;
    private String returnKey;
    private int generation;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(CustomPreferences.localized(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new CustomPreferences(this);
        manage = getIntent().getBooleanExtra(MANAGE, false);
        LinearLayout root = CustomUi.page(this, manage ? R.string.custom_favorites : R.string.custom_all_apps);
        TextView hint = CustomUi.text(this, getText(manage ? R.string.custom_manage_hint : R.string.custom_apps_hint), 14);
        hint.setTextColor(CustomUi.MUTED); hint.setPadding(0, 0, 0, CustomUi.dp(this, 10)); root.addView(hint);
        grid = new RecyclerView(this); grid.setItemAnimator(null);
        grid.setLayoutManager(new GridLayoutManager(this, preferences.columns()));
        adapter = new CustomAppAdapter(preferences, false, true, app -> {
            returnKey = app.key;
            if (manage) { preferences.favorite(app.key, !preferences.isFavorite(app.key)); render(); }
            else CustomAppRepository.open(this, app);
        }, this::menu);
        grid.setAdapter(adapter); root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    @Override public void onResume() {
        super.onResume(); int request = ++generation;
        CustomAppRepository.load(this, apps -> {
            if (isDestroyed() || request != generation) return;
            installed = apps; render();
        });
    }
    private void render() {
        adapter.submit(CustomAppRepository.ordered(installed, preferences.allOrder(), false));
        if (returnKey != null) focus(returnKey);
        else if (!adapter.apps.isEmpty()) focus(adapter.apps.get(0).key);
    }
    private void focus(String key) {
        for (int i = 0; i < adapter.apps.size(); i++) if (adapter.apps.get(i).key.equals(key)) {
            int index = i; grid.scrollToPosition(index);
            grid.post(() -> { RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(index); if (h != null) h.itemView.requestFocus(); });
            break;
        }
    }
    private void menu(CustomAppRepository.App app) {
        returnKey = app.key;
        List<String> actions = new ArrayList<>();
        actions.add(getString(preferences.isFavorite(app.key) ? R.string.custom_remove_favorite : R.string.custom_add_favorite));
        actions.add(getString(R.string.custom_move_all));
        boolean favorite = preferences.isFavorite(app.key);
        if (favorite) actions.add(getString(R.string.custom_move_favorite));
        actions.add(getString(R.string.custom_open)); actions.add(getString(R.string.custom_app_info));
        new AlertDialog.Builder(this).setTitle(app.label).setItems(actions.toArray(new String[0]), (dialog, which) -> {
            if (which == 0) { preferences.favorite(app.key, !favorite); render(); }
            else if (which == 1) move(app, false);
            else if (favorite && which == 2) move(app, true);
            else if (which == actions.size() - 2) CustomAppRepository.open(this, app);
            else {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.activityInfo.packageName))); }
                catch (RuntimeException unavailable) { android.widget.Toast.makeText(this, R.string.custom_app_unavailable, android.widget.Toast.LENGTH_SHORT).show(); }
            }
        }).show();
    }
    private void move(CustomAppRepository.App app, boolean favorite) {
        List<CustomAppRepository.App> list = CustomAppRepository.ordered(installed,
                favorite ? preferences.favorites() : preferences.allOrder(), favorite);
        String[] names = new String[list.size()]; List<String> keys = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) { names[i] = (i + 1) + ". " + list.get(i).label; keys.add(list.get(i).key); }
        new AlertDialog.Builder(this).setTitle(R.string.custom_move_to).setItems(names, (dialog, index) -> {
            keys.remove(app.key); keys.add(Math.min(index, keys.size()), app.key);
            preferences.saveOrder(favorite ? "favorites" : "all_order", keys); render();
        }).show();
    }
    @Override protected void onDestroy() { generation++; super.onDestroy(); }
}
