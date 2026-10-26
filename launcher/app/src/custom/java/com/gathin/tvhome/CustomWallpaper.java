package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local static images only. Imports are bounded, resized and copied into private storage. */
final class CustomWallpaper {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final String[] BUILTINS = {"aurora.jpg", "ruska.jpg", "glass.jpg"};
    private CustomWallpaper() { }
    private static InputStream open(Context context, String choice) throws IOException {
        if (choice.startsWith("builtin:")) {
            int index;
            try { index = Integer.parseInt(choice.substring(8)); } catch (NumberFormatException e) { throw new IOException("Invalid image", e); }
            if (index < 0 || index >= BUILTINS.length) throw new IOException("Invalid image");
            return context.getAssets().open("custom_wallpapers/" + BUILTINS[index]);
        }
        String name = "pending".equals(choice) ? "wallpaper-preview.jpg" : "custom-wallpaper.jpg";
        return new FileInputStream(new File("pending".equals(choice) ? context.getCacheDir() : context.getFilesDir(), name));
    }
    static Bitmap decode(Context context, String choice, int targetW, int targetH) {
        if ("none".equals(choice)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream stream = open(context, choice)) { BitmapFactory.decodeStream(stream, null, bounds); }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 50000000L) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = CustomImagePolicy.sample(bounds.outWidth, bounds.outHeight, targetW, targetH);
            Bitmap source;
            try (InputStream stream = open(context, choice)) { source = BitmapFactory.decodeStream(stream, null, options); }
            if (source == null) return null;
            Bitmap output = crop(source, targetW, targetH); source.recycle(); return output;
        } catch (IOException | RuntimeException | OutOfMemoryError unavailable) { return null; }
    }
    private static Bitmap crop(Bitmap source, int w, int h) {
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        float scale = Math.max((float) w / source.getWidth(), (float) h / source.getHeight());
        int sw = Math.min(source.getWidth(), Math.max(1, Math.round(w / scale)));
        int sh = Math.min(source.getHeight(), Math.max(1, Math.round(h / scale)));
        int x = (source.getWidth() - sw) / 2, y = (source.getHeight() - sh) / 2;
        new Canvas(output).drawBitmap(source, new Rect(x, y, x + sw, y + sh), new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        return output;
    }
    static void importPreview(Context context, Uri uri) throws IOException {
        File input = new File(context.getCacheDir(), "wallpaper-import.tmp");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(input)) {
                if (in == null) throw new IOException("Image unavailable");
                byte[] buffer = new byte[16384]; long total = 0;
                for (int count; (count = in.read(buffer)) != -1;) {
                    total += count; if (total > 32L * 1024 * 1024) throw new IOException("Image exceeds 32 MiB");
                    out.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(input.getPath(), options);
            if (!("image/jpeg".equals(options.outMimeType) || "image/png".equals(options.outMimeType))
                    || options.outWidth <= 0 || options.outHeight <= 0 || (long) options.outWidth * options.outHeight > 50000000L) {
                throw new IOException("Unsupported image");
            }
            int w = Math.min(1920, context.getResources().getDisplayMetrics().widthPixels);
            int h = Math.min(1080, context.getResources().getDisplayMetrics().heightPixels);
            options.inJustDecodeBounds = false;
            options.inSampleSize = CustomImagePolicy.sample(options.outWidth, options.outHeight, w, h);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap source = BitmapFactory.decodeFile(input.getPath(), options);
            if (source == null) throw new IOException("Cannot decode image");
            Bitmap output = null;
            try {
                output = crop(source, w, h);
                try (FileOutputStream out = new FileOutputStream(new File(context.getCacheDir(), "wallpaper-preview.jpg"))) {
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw new IOException("Cannot save preview");
                }
            } finally { source.recycle(); if (output != null) output.recycle(); }
        } catch (OutOfMemoryError e) { throw new IOException("Image too large", e); }
        finally { input.delete(); }
    }
    static void applyImport(Context context) throws IOException {
        android.util.AtomicFile target = new android.util.AtomicFile(new File(context.getFilesDir(), "custom-wallpaper.jpg"));
        FileOutputStream out = null;
        try (InputStream in = new FileInputStream(new File(context.getCacheDir(), "wallpaper-preview.jpg"))) {
            out = target.startWrite(); byte[] buffer = new byte[16384];
            for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
            target.finishWrite(out);
        } catch (IOException e) { if (out != null) target.failWrite(out); throw e; }
    }
}
