package com.leira.wificasa;

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
        titles = getResources().getStringArray(R.array.guide_titles);
        texts = getResources().getStringArray(R.array.guide_texts);
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
        skip.setOnClickListener(v -> finish());
        root.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
            if (page < IMAGES.length - 1) go(page + 1); else done(ACTION_SEARCH);
        });
        row.addView(back);
        row.addView(next);
        root.addView(row);

        second = button(getString(R.string.record_my_router), false);
        second.setOnClickListener(v -> done(ACTION_LEARN));
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
        if (p < 0 || p >= IMAGES.length) return;
        page = p;
        image.setImageResource(IMAGES[p]);
        title.setText(titles[p]);
        text.setText(texts[p]);
        boolean last = p == IMAGES.length - 1;
        back.setVisibility(p == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(getString(last ? R.string.find_my_router : R.string.next));
        second.setVisibility(last ? View.VISIBLE : View.GONE);
        dots.removeAllViews();
        for (int i = 0; i < IMAGES.length; i++) {
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
