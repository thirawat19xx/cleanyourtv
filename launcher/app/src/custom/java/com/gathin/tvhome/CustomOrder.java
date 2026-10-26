package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Stable ordered choices. Empty means empty; newly installed apps never become favorites. */
final class CustomOrder {
    private CustomOrder() { }

    static List<String> decode(String text) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (text != null && text.length() <= 65536) {
            for (String key : text.split("\\n")) {
                if (valid(key) && unique.size() < 512) unique.add(key);
            }
        }
        return new ArrayList<>(unique);
    }

    static String encode(List<String> keys) { return String.join("\n", decode(String.join("\n", keys))); }

    static boolean valid(String key) {
        return key != null && key.length() <= 512 && key.matches(
                "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*/\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }

    static List<String> move(List<String> order, String key, int delta) {
        List<String> next = new ArrayList<>(order);
        int from = next.indexOf(key), to = from + delta;
        if (from >= 0 && to >= 0 && to < next.size()) {
            next.remove(from);
            next.add(to, key);
        }
        return next;
    }

    /** Preserve the owner's order, then append unknown installed apps in the caller's label order. */
    static List<String> reconcile(List<String> order, List<String> installed) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String key : order) if (installed.contains(key)) result.add(key);
        result.addAll(installed);
        return new ArrayList<>(result);
    }

    static List<String> selected(List<String> favorites, List<String> installed) {
        List<String> result = new ArrayList<>();
        for (String key : favorites) if (installed.contains(key) && !result.contains(key)) result.add(key);
        return result;
    }
}
