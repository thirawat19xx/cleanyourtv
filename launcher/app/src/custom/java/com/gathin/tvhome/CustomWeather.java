package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Open-Meteo current weather, selected manually; no background service or GPS. */
final class CustomWeather {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final class Place {
        final String name, detail; final double latitude, longitude;
        Place(String name, String detail, double lat, double lon) { this.name = name; this.detail = detail; latitude = lat; longitude = lon; }
    }
    static final class Snapshot {
        final double temperature, high, low, wind;
        final int humidity, code; final boolean day; final long fetched;
        Snapshot(double temp, double high, double low, double wind, int humidity, int code, boolean day, long fetched) {
            this.temperature = temp; this.high = high; this.low = low; this.wind = wind;
            this.humidity = humidity; this.code = code; this.day = day; this.fetched = fetched;
        }
    }
    static final class Result {
        final Snapshot snapshot; final boolean offline; final String city;
        Result(Snapshot snapshot, boolean offline, String city) { this.snapshot = snapshot; this.offline = offline; this.city = city; }
    }
    private CustomWeather() { }

    static void search(String text, Consumer<List<Place>> done) {
        IO.execute(() -> {
            List<Place> places = new ArrayList<>();
            try {
                String query = text.trim();
                if (query.isEmpty() || query.length() > 120) throw new IOException("Invalid city");
                JSONObject data = get("https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&name="
                        + URLEncoder.encode(query, "UTF-8") + "&language=" + Locale.getDefault().getLanguage());
                JSONArray list = data.optJSONArray("results");
                if (list != null) for (int i = 0; i < Math.min(6, list.length()); i++) {
                    JSONObject place = list.getJSONObject(i);
                    double lat = place.getDouble("latitude"), lon = place.getDouble("longitude");
                    if (CustomWeatherData.coordinates(lat, lon)) places.add(new Place(place.getString("name"),
                            place.optString("admin1") + ", " + place.optString("country"), lat, lon));
                }
            } catch (Exception unavailable) { places.clear(); }
            MAIN.post(() -> done.accept(places));
        });
    }
    static void refresh(Context context, Consumer<Result> done) {
        Context application = context.getApplicationContext();
        IO.execute(() -> {
            CustomPreferences p = new CustomPreferences(application);
            String city = p.city(), coordinates = p.prefs.getString("coordinates", "");
            Snapshot snapshot = cached(application, coordinates);
            boolean offline = false;
            if (!city.isEmpty() && !coordinates.isEmpty()) {
                long now = System.currentTimeMillis();
                String lastPlace = p.prefs.getString("weather_attempt_place", "");
                long attempted = coordinates.equals(lastPlace) ? p.prefs.getLong("weather_attempt", 0) : 0;
                if (CustomWeatherData.shouldRefresh(now, snapshot == null ? 0 : snapshot.fetched, attempted, p.weatherInterval())) {
                    p.prefs.edit().putString("weather_attempt_place", coordinates).putLong("weather_attempt", now).apply();
                    try {
                        String[] pair = coordinates.split(",");
                        if (pair.length != 2 || !CustomWeatherData.coordinates(Double.parseDouble(pair[0]), Double.parseDouble(pair[1]))) throw new IOException("Invalid coordinates");
                        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?latitude=" + pair[0] + "&longitude=" + pair[1]
                                + "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1");
                        JSONObject current = reply.getJSONObject("current"), daily = reply.getJSONObject("daily");
                        Snapshot fresh = new Snapshot(current.getDouble("temperature_2m"), daily.getJSONArray("temperature_2m_max").getDouble(0),
                                daily.getJSONArray("temperature_2m_min").getDouble(0), current.getDouble("wind_speed_10m"),
                                current.getInt("relative_humidity_2m"), current.getInt("weather_code"), current.getInt("is_day") == 1, now);
                        if (!valid(fresh)) throw new IOException("Invalid weather");
                        snapshot = fresh;
                        save(application, coordinates, fresh);
                    } catch (Exception unavailable) { offline = true; }
                } else offline = snapshot == null || now - snapshot.fetched >= p.weatherInterval();
            }
            Result result = new Result(snapshot, offline, city);
            MAIN.post(() -> done.accept(result));
        });
    }
    private static boolean valid(Snapshot s) {
        return Double.isFinite(s.temperature) && Double.isFinite(s.high) && Double.isFinite(s.low) && Double.isFinite(s.wind)
                && s.temperature >= -150 && s.temperature <= 100 && s.low <= s.high && s.wind >= 0 && s.humidity >= 0 && s.humidity <= 100;
    }
    private static AtomicFile cache(Context context) { return new AtomicFile(new File(context.getFilesDir(), "custom-weather.json")); }
    private static Snapshot cached(Context context, String coordinates) {
        try {
            byte[] bytes = cache(context).readFully(); if (bytes.length > 65536) return null;
            JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!coordinates.equals(data.getString("coordinates"))) return null;
            Snapshot s = new Snapshot(data.getDouble("temperature"), data.getDouble("high"), data.getDouble("low"), data.getDouble("wind"),
                    data.getInt("humidity"), data.getInt("code"), data.getBoolean("day"), data.getLong("fetched"));
            return valid(s) ? s : null;
        } catch (Exception unavailable) { return null; }
    }
    private static void save(Context context, String coordinates, Snapshot s) {
        AtomicFile file = cache(context); FileOutputStream out = null;
        try {
            JSONObject data = new JSONObject().put("coordinates", coordinates).put("temperature", s.temperature).put("high", s.high)
                    .put("low", s.low).put("wind", s.wind).put("humidity", s.humidity).put("code", s.code).put("day", s.day).put("fetched", s.fetched);
            out = file.startWrite(); out.write(data.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out);
        } catch (Exception unavailable) { if (out != null) file.failWrite(out); }
    }
    private static JSONObject get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Weather unavailable");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (out.size() + count > 1024 * 1024) throw new IOException("Response too large");
                    out.write(buffer, 0, count);
                }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    static int description(int code, boolean day) {
        if (code == 0) return day ? R.string.custom_sunny : R.string.custom_clear;
        if (code <= 2) return R.string.custom_partly_cloudy;
        if (code == 3) return R.string.custom_cloudy;
        if (code == 45 || code == 48) return R.string.custom_fog;
        if (code >= 95) return R.string.custom_thunderstorm;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.string.custom_snow;
        return R.string.custom_rain;
    }
}
