package com.gathin.tvhome;

/** Spotify identity and progress arithmetic, without network or account credentials. */
final class CustomMediaPolicy {
    private CustomMediaPolicy() { }
    static boolean spotify(String pkg) { return "com.spotify.tv.android".equals(pkg) || "com.spotify.music".equals(pkg); }
    static long position(long reported, long updated, float speed, long now, long duration, boolean playing) {
        long value = Math.max(0, reported);
        if (playing && updated > 0 && now > updated && Float.isFinite(speed) && speed > 0) {
            double advanced = (now - updated) * (double) speed;
            value = (long) Math.min(Long.MAX_VALUE, value + advanced);
        }
        return duration > 0 ? Math.min(value, duration) : value;
    }
}
