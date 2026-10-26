package com.gathin.tvhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class CustomAppAdapter extends RecyclerView.Adapter<CustomAppAdapter.Holder> {
    final List<CustomAppRepository.App> apps = new ArrayList<>();
    private final Consumer<CustomAppRepository.App> click, hold;
    private final boolean horizontal, checkmarks;
    private final CustomPreferences preferences;
    private int columns;

    CustomAppAdapter(CustomPreferences preferences, boolean horizontal, boolean checkmarks,
                     Consumer<CustomAppRepository.App> click, Consumer<CustomAppRepository.App> hold) {
        this.preferences = preferences; this.horizontal = horizontal; this.checkmarks = checkmarks;
        this.click = click; this.hold = hold; columns = preferences.columns();
    }
    void submit(List<CustomAppRepository.App> values) { apps.clear(); apps.addAll(values); notifyDataSetChanged(); }
    @Override public int getItemCount() { return apps.size(); }
    @Override @NonNull public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        android.content.Context c = parent.getContext();
        LinearLayout root = CustomUi.column(c);
        root.setFocusable(true); root.setClickable(true); root.setBackground(CustomUi.focus(c));
        root.setPadding(CustomUi.dp(c, 5), CustomUi.dp(c, 5), CustomUi.dp(c, 5), CustomUi.dp(c, 5));
        int width = horizontal ? CustomUi.dp(c, 142) : Math.max(CustomUi.dp(c, 100), parent.getWidth() / columns - CustomUi.dp(c, 10));
        RecyclerView.LayoutParams p = new RecyclerView.LayoutParams(width, CustomUi.dp(c, horizontal ? 101 : 115));
        p.setMargins(CustomUi.dp(c, 4), CustomUi.dp(c, 4), CustomUi.dp(c, 4), CustomUi.dp(c, 4)); root.setLayoutParams(p);
        ImageView image = new ImageView(c); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView title = CustomUi.text(c, "", 12); title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, CustomUi.dp(c, 24)));
        return new Holder(root, image, title);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        CustomAppRepository.App app = apps.get(position);
        h.title.setText((checkmarks && preferences.isFavorite(app.key) ? "★ " : "") + app.label);
        h.itemView.setContentDescription(h.title.getText());
        CustomAppRepository.art(h.image, app);
        h.itemView.setOnClickListener(v -> click.accept(app));
        h.itemView.setOnLongClickListener(v -> { hold.accept(app); return true; });
        h.itemView.setOnKeyListener((v, code, event) -> {
            if (code == android.view.KeyEvent.KEYCODE_MENU && event.getAction() == android.view.KeyEvent.ACTION_UP) { hold.accept(app); return true; }
            return false;
        });
    }
    @Override public void onViewRecycled(@NonNull Holder h) { h.image.setTag(null); h.image.setImageDrawable(null); }
    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image; final TextView title;
        Holder(View root, ImageView image, TextView title) { super(root); this.image = image; this.title = title; }
    }
}
