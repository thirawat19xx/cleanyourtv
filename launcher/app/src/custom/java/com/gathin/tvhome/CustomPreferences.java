package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CustomPreferences {
    static final String NAME = "custom_edition";
    final SharedPreferences prefs;

    CustomPreferences(Context context) { prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    static Context localized(Context context) {
        String language = new CustomPreferences(context).prefs.getString("language", "");
        if (language.isEmpty()) return context;
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        return context.createConfigurationContext(config);
    }

    List<String> favorites() { return CustomOrder.decode(prefs.getString("favorites", "")); }
    List<String> allOrder() { return CustomOrder.decode(prefs.getString("all_order", "")); }
    void saveOrder(String key, List<String> values) {
        prefs.edit().putString(key, CustomOrder.encode(values)).apply();
    }
    void favorite(String key, boolean selected) {
        List<String> values = favorites();
        if (selected && !values.contains(key)) values.add(key);
        else if (!selected) values.remove(key);
        saveOrder("favorites", values);
    }
    boolean isFavorite(String key) { return favorites().contains(key); }
    int dim() { return Math.max(0, Math.min(90, prefs.getInt("wallpaper_dim", 35))); }
    int columns() { return Math.max(4, Math.min(7, prefs.getInt("columns", 5))); }
    boolean weather() { return prefs.getBoolean("weather_enabled", true); }
    boolean music() { return prefs.getBoolean("music_enabled", true); }
    boolean fahrenheit() { return prefs.getBoolean("fahrenheit", false); }
    long weatherInterval() { return prefs.getInt("weather_interval", 30) == 60 ? 3600000L : 1800000L; }
    String city() { return prefs.getString("city", ""); }
    String wallpaper() { return prefs.getString("wallpaper", "builtin:0"); }
}
