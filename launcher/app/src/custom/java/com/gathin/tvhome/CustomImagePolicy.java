package com.gathin.tvhome;

/** Keep very wide/tall imports within a decode budget even when they cannot cover both screen axes. */
final class CustomImagePolicy {
    static final long MAX_DECODE_PIXELS = 8400000L;
    private CustomImagePolicy() { }
    static int sample(int width, int height, int targetWidth, int targetHeight) {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("Invalid dimensions");
        int sample = WallpaperSize.sampleSize(width, height, targetWidth, targetHeight);
        while ((((long) width + sample - 1) / sample) * (((long) height + sample - 1) / sample) > MAX_DECODE_PIXELS) sample *= 2;
        return sample;
    }
}
