package com.gathin.tvhome;

/** Unit conversions and refresh policy independent of Android, covered by JVM tests. */
final class CustomWeatherData {
    private CustomWeatherData() { }
    static boolean shouldRefresh(long now, long fetched, long attempted, long interval) {
        if (attempted > 0 && now >= attempted && now - attempted < 300000L) return false;
        return fetched <= 0 || now < fetched || now - fetched >= interval;
    }
    static int degrees(double celsius, boolean fahrenheit) {
        return (int) Math.round(fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius);
    }
    static boolean coordinates(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return "⛅";
        if (code <= 48) return "☁";
        if (code >= 95) return "⛈";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        return "☂";
    }
}
