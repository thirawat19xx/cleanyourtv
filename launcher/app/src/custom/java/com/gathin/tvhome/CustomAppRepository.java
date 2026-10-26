package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class CustomAppRepository {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> ART = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    static final class App {
        final String key, label;
        final ResolveInfo info;
        App(ResolveInfo info, String label) {
            this.info = info; this.label = label;
            key = new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString();
        }
    }
    private CustomAppRepository() { }

    static void load(Context context, Consumer<List<App>> result) {
        Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            PackageManager pm = appContext.getPackageManager();
            Map<String, App> found = new LinkedHashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                List<ResolveInfo> entries = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
                for (ResolveInfo info : entries) {
                    if (info.activityInfo == null || !info.activityInfo.exported
                            || info.activityInfo.packageName.equals(appContext.getPackageName())
                            || found.containsKey(info.activityInfo.packageName)) continue;
                    try {
                        found.put(info.activityInfo.packageName, new App(info,
                                Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), info.activityInfo.packageName)));
                    } catch (RuntimeException unavailable) { /* App changed during the query. */ }
                }
            }
            List<App> apps = new ArrayList<>(found.values());
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> { int byName = collator.compare(a.label, b.label); return byName != 0 ? byName : a.key.compareTo(b.key); });
            MAIN.post(() -> result.accept(apps));
        });
    }

    static List<App> ordered(List<App> apps, List<String> order, boolean onlyOrdered) {
        Map<String, App> byKey = new LinkedHashMap<>();
        for (App app : apps) byKey.put(app.key, app);
        List<App> result = new ArrayList<>();
        List<String> keys = new ArrayList<>(byKey.keySet());
        for (String key : onlyOrdered ? CustomOrder.selected(order, keys) : CustomOrder.reconcile(order, keys)) result.add(byKey.get(key));
        return result;
    }

    static void art(ImageView image, App app) {
        image.setTag(app.key);
        Bitmap cached = ART.get(app.key);
        image.setImageBitmap(cached);
        if (cached != null) return;
        Context context = image.getContext().getApplicationContext();
        IO.execute(() -> {
            Bitmap bitmap = null;
            try {
                Drawable drawable = app.info.activityInfo.loadBanner(context.getPackageManager());
                boolean banner = drawable != null;
                if (drawable == null) drawable = app.info.loadIcon(context.getPackageManager());
                bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xFF101B2C);
                if (drawable != null) {
                    int sw = Math.max(1, drawable.getIntrinsicWidth()), sh = Math.max(1, drawable.getIntrinsicHeight());
                    float scale = Math.min((banner ? 256f : 100f) / sw, (banner ? 128f : 100f) / sh);
                    int w = Math.max(1, Math.round(sw * scale)), h = Math.max(1, Math.round(sh * scale));
                    Rect old = new Rect(drawable.getBounds());
                    drawable.setBounds((256 - w) / 2, (128 - h) / 2, (256 + w) / 2, (128 + h) / 2);
                    drawable.draw(canvas); drawable.setBounds(old);
                }
            } catch (RuntimeException | OutOfMemoryError unavailable) { bitmap = null; }
            Bitmap ready = bitmap;
            if (ready != null) ART.put(app.key, ready);
            MAIN.post(() -> { if (app.key.equals(image.getTag())) image.setImageBitmap(ready); });
        });
    }

    static void clearArt() { ART.evictAll(); }
    static void open(Context context, App app) {
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName.unflattenFromString(app.key))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException unavailable) {
            android.widget.Toast.makeText(context, R.string.custom_app_unavailable, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
