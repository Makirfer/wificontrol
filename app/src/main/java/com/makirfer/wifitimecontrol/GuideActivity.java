package com.makirfer.wifitimecontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Guía del recetario en fichas: una ilustración, un título y una frase por ficha.
 * Se pasa de una a otra deslizando o con los botones. Al terminar, devuelve qué quiere
 * hacer la persona (buscar su router en el catálogo o grabarlo).
 */
public class GuideActivity extends Activity {

    public static final String RESULT_ACTION = "accion";
    public static final String ACTION_SEARCH = "buscar";
    public static final String ACTION_LEARN = "grabar";
    /** Modo bienvenida (primera vez que se abre la app): fichas de qué hace la app. */
    public static final String EXTRA_WELCOME = "bienvenida";
    public static final String ACTION_START = "empezar";
    public static final String ACTION_LOCK = "bloqueo";

    private static final int C_BG = Color.parseColor("#F4F1EC");
    private static final int C_TEXT = Color.parseColor("#1F1D1A");
    private static final int C_MUTED = Color.parseColor("#6B665E");
    private static final int C_ACCENT = Color.parseColor("#E8590C");
    private static final int C_DOT = Color.parseColor("#D3CCC0");

    private static final int[] IMAGES = {
            R.drawable.guia_1, R.drawable.guia_2, R.drawable.guia_3,
            R.drawable.guia_4, R.drawable.guia_5, R.drawable.guia_6
    };
    /** Título y frase de cada tarjeta, en el idioma del móvil (res/values/strings.xml y sus traducciones). */
    private String[] titles, texts;

    private boolean welcome;
    private String[] icons;
    private TextView icon;
    private int pages;

    private int page = 0;
    private ImageView image;
    private TextView title, text;
    private LinearLayout dots;
    private Button back, next, second;
    private float downX;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        welcome = getIntent().getBooleanExtra(EXTRA_WELCOME, false);
        titles = getResources().getStringArray(welcome ? R.array.welcome_titles : R.array.guide_titles);
        texts = getResources().getStringArray(welcome ? R.array.welcome_texts : R.array.guide_texts);
        icons = getResources().getStringArray(R.array.welcome_icons);
        pages = welcome ? titles.length : IMAGES.length;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(24), dp(16), dp(24), dp(24));

        TextView skip = new TextView(this);
        skip.setText(getString(R.string.skip));
        skip.setTextColor(C_MUTED);
        skip.setTextSize(15);
        skip.setGravity(Gravity.END);
        skip.setPadding(0, dp(8), 0, dp(8));
        skip.setOnClickListener(v -> { if (welcome) done(ACTION_START); else finish(); });
        root.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        icon = new TextView(this);
        icon.setTextSize(110);
        icon.setGravity(Gravity.CENTER);
        if (welcome) root.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        else root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        title = new TextView(this);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, dp(12), 0, dp(8));
        root.addView(title);

        text = new TextView(this);
        text.setTextSize(17);
        text.setTextColor(C_MUTED);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setMinLines(3);
        root.addView(text);

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_HORIZONTAL);
        dots.setPadding(0, dp(12), 0, dp(16));
        root.addView(dots);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        back = button(getString(R.string.guide_back), false);
        back.setOnClickListener(v -> go(page - 1));
        next = button(getString(R.string.next), true);
        next.setOnClickListener(v -> {
            if (page < pages - 1) go(page + 1); else done(welcome ? ACTION_START : ACTION_SEARCH);
        });
        row.addView(back);
        row.addView(next);
        root.addView(row);

        second = button(getString(welcome ? R.string.welcome_lock : R.string.record_my_router), false);
        second.setOnClickListener(v -> done(welcome ? ACTION_LOCK : ACTION_LEARN));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        root.addView(second, lp);

        // Deslizar a izquierda o derecha para pasar de ficha
        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downX = e.getX(); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - downX;
                if (dx < -dp(60)) go(page + 1);
                else if (dx > dp(60)) go(page - 1);
                else v.performClick();
                return true;
            }
            return false;
        });

        setContentView(root);
        go(0);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary ? Color.WHITE : C_TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? C_ACCENT : Color.parseColor("#E6E1D8"));
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void go(int p) {
        if (p < 0 || p >= pages) return;
        page = p;
        if (welcome) icon.setText(icons[Math.min(p, icons.length - 1)]); else image.setImageResource(IMAGES[p]);
        title.setText(titles[p]);
        text.setText(texts[p]);
        boolean last = p == pages - 1;
        back.setVisibility(p == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(getString(!last ? R.string.next : welcome ? R.string.welcome_start : R.string.find_my_router));
        second.setVisibility(last ? View.VISIBLE : View.GONE);
        dots.removeAllViews();
        for (int i = 0; i < pages; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == p ? C_ACCENT : C_DOT);
            g.setCornerRadius(dp(4));
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == p ? 22 : 8), dp(8));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(d, lp);
        }
    }

    private void done(String action) {
        Intent r = new Intent();
        r.putExtra(RESULT_ACTION, action);
        setResult(RESULT_OK, r);
        finish();
    }
}
