package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class CustomUi {
    static final int WHITE = 0xFFF3F7FF, MUTED = 0xFFB5C4DB, ACCENT = 0xFF7ED9FF;
    private CustomUi() { }
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable panel(Context context, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{fill, (fill & 0xFF000000) | 0x0B1425});
        d.setCornerRadius(dp(context, 16));
        d.setStroke(dp(context, 1), stroke);
        return d;
    }
    static StateListDrawable focus(Context context) {
        StateListDrawable d = new StateListDrawable();
        GradientDrawable selected = panel(context, 0xEB163C65, ACCENT);
        selected.setStroke(dp(context, 2), ACCENT);
        d.addState(new int[]{android.R.attr.state_focused}, selected);
        d.addState(new int[]{android.R.attr.state_pressed}, selected);
        d.addState(new int[]{}, panel(context, 0xC0122036, 0x508CA6CF));
        return d;
    }
    static TextView text(Context context, CharSequence value, float size) {
        TextView v = new TextView(context);
        v.setText(value); v.setTextColor(WHITE); v.setTextSize(size);
        v.setFontFeatureSettings("tnum");
        return v;
    }
    static TextView button(Context context, CharSequence label, View.OnClickListener action) {
        TextView v = text(context, label, 16);
        v.setFocusable(true); v.setClickable(true); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        v.setMinHeight(dp(context, 48)); v.setBackground(focus(context));
        v.setOnClickListener(action);
        v.setOnFocusChangeListener((view, focused) -> v.setTextColor(focused ? Color.WHITE : WHITE));
        return v;
    }
    static LinearLayout column(Context context) {
        LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static LinearLayout row(Context context) {
        LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    static void margin(View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(view.getContext(), top); view.setLayoutParams(p);
    }
    static LinearLayout page(Activity activity, int title) {
        activity.getWindow().setStatusBarColor(Color.BLACK);
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = column(activity);
        root.setPadding(dp(activity, 36), dp(activity, 18), dp(activity, 36), dp(activity, 20));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF10223B, 0xFF060D18}));
        LinearLayout header = row(activity);
        header.addView(button(activity, "‹", v -> activity.finish()), new LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 48)));
        TextView heading = text(activity, activity.getText(title), 24); heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(activity, 18), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(activity, 50), 1));
        root.addView(header); activity.setContentView(root);
        return root;
    }
    static String onOff(Context c, boolean on) { return c.getString(on ? R.string.custom_on : R.string.custom_off); }
}
