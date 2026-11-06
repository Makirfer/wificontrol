package com.leira.wificasa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.leira.wificasa.core.learn.Capture;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Modo aprendizaje: abre el panel web del router dentro de la app, guía al usuario por los
 * pasos necesarios y graba lo que el panel envía y recibe. Al terminar, la grabación saneada
 * (junto con las instrucciones para la IA) se guarda en un archivo o se comparte.
 */
public class LearnActivity extends Activity {

    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PASSWORD = "password";
    public static final String EXTRA_VENDOR = "vendor";
    public static final String EXTRA_MODEL = "model";
    public static final String EXTRA_ISP = "isp";
    /** De la ficha del catálogo: camino hasta el bloqueo, qué no tocar y si se puede bloquear. */
    public static final String EXTRA_WHERE = "where";
    public static final String EXTRA_AVOID = "avoid";
    public static final String EXTRA_SUPPORT = "support";

    private static final int REQ_SAVE = 41;
    private static final int C_BAR = Color.parseColor("#1F1D1A");
    private static final int C_ACCENT = Color.parseColor("#E8590C");

    private Capture capture;
    private WebView web;
    private TextView stepTitle, stepHelp, counter;
    private Button nextBtn, notFoundBtn;
    private String where = "", avoid = "", support = "";
    private String recorderJs = "";
    private String pendingText;

    /** Nombre y ayuda de cada paso, en el idioma del móvil (res/values/strings.xml y sus traducciones). */
    private String[] steps, help;

    private String t(int id, Object... a) { return a.length == 0 ? getString(id) : getString(id, a); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        steps = getResources().getStringArray(R.array.learn_steps);
        help = getResources().getStringArray(R.array.learn_help);
        String host = getIntent().getStringExtra(EXTRA_HOST);
        if (host == null || host.trim().isEmpty()) host = "192.168.1.1";
        host = host.trim();
        if (!host.startsWith("http://") && !host.startsWith("https://")) host = "http://" + host;
        capture = new Capture(host, getIntent().getStringExtra(EXTRA_PASSWORD));
        capture.setRouter(getIntent().getStringExtra(EXTRA_VENDOR), getIntent().getStringExtra(EXTRA_MODEL),
                getIntent().getStringExtra(EXTRA_ISP));
        recorderJs = asset("aprendizaje/grabador.js");
        where = nz(getIntent().getStringExtra(EXTRA_WHERE));
        avoid = nz(getIntent().getStringExtra(EXTRA_AVOID));
        support = nz(getIntent().getStringExtra(EXTRA_SUPPORT));
        buildUi();
        showStep();
        // Muchos routers están en la misma dirección (192.168.1.1): se borran las cookies que dejó
        // otro router o una grabación anterior, para que la grabación muestre solo las de este.
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        web.loadUrl(host + "/");
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(C_BAR);
        bar.setPadding(dp(14), dp(12), dp(14), dp(10));

        stepTitle = new TextView(this);
        stepTitle.setTextColor(Color.WHITE);
        stepTitle.setTextSize(17);
        stepTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(stepTitle);

        stepHelp = new TextView(this);
        stepHelp.setTextColor(Color.parseColor("#D8D2C8"));
        stepHelp.setTextSize(13);
        stepHelp.setPadding(0, dp(4), 0, dp(8));
        bar.addView(stepHelp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        counter = new TextView(this);
        counter.setTextColor(Color.parseColor("#D8D2C8"));
        counter.setTextSize(12);
        counter.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(counter);
        // «No lo encuentro» va en su propia fila: con Cancelar y Hecho no cabía en muchos móviles.
        notFoundBtn = new Button(this);
        notFoundBtn.setAllCaps(false);
        notFoundBtn.setText(t(R.string.not_found));
        notFoundBtn.setOnClickListener(v -> notFound());
        LinearLayout.LayoutParams nfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nfLp.topMargin = dp(6);
        notFoundBtn.setLayoutParams(nfLp);
        Button cancel = new Button(this);
        cancel.setAllCaps(false);
        cancel.setText(t(R.string.cancel));
        cancel.setOnClickListener(v -> confirmExit());
        row.addView(cancel);
        nextBtn = new Button(this);
        nextBtn.setAllCaps(false);
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setBackgroundColor(C_ACCENT);
        nextBtn.setOnClickListener(v -> nextStep());
        row.addView(nextBtn);
        bar.addView(row);
        bar.addView(notFoundBtn);
        root.addView(bar);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false); // las ventanas nuevas del panel se abren aquí mismo
        web.addJavascriptInterface(new Bridge(), "WifiCasaRec");
        // Sin esto, los avisos del panel ("¿Seguro que quieres guardar?") se responderían
        // solos como Cancelar y el cambio no se aplicaría.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                capture.record("{\"type\":\"dialog\",\"kind\":\"alert\",\"message\":" + quote(message) + "}");
                new AlertDialog.Builder(LearnActivity.this).setMessage(message)
                        .setPositiveButton(t(R.string.accept), (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.confirm()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(LearnActivity.this).setMessage(message)
                        .setPositiveButton(t(R.string.accept), (d, w) -> {
                            capture.record("{\"type\":\"dialog\",\"kind\":\"confirm\",\"answer\":true,\"message\":"
                                    + quote(message) + "}");
                            result.confirm();
                        })
                        .setNegativeButton(t(R.string.cancel), (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(recorderJs, null);
                runOnUiThread(() -> updateCounter());
            }

            /**
             * Las peticiones que el panel hace mientras carga una página llegan antes que el
             * grabador. Aquí se anotan al menos su método y su dirección (sin contenido).
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String u = request.getUrl().toString();
                    if (!u.matches("(?i).*\\.(png|gif|jpe?g|svg|ico|css|woff2?|ttf)(\\?.*)?$")) {
                        capture.record("{\"type\":\"request\",\"method\":" + quote(request.getMethod())
                                + ",\"url\":" + quote(u) + "}");
                        recordCookies(u);
                    }
                } catch (Exception ignored) {
                    // solo es una anotación
                }
                return null; // la petición sigue su curso normal
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String h, String realm) {
                capture.record("{\"type\":\"basic-auth\",\"realm\":" + quote(realm) + "}");
                askBasicAuth(handler, realm);
            }
        });
        web.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(web);
        setContentView(root);
    }

    private String lastCookies = "";

    /**
     * Cookies que el navegador enviará al router (también las HttpOnly, que el JavaScript no ve).
     * Se anotan cuando cambian; los valores largos se ocultan (pueden ser tokens de sesión).
     */
    private void recordCookies(String url) {
        String raw = android.webkit.CookieManager.getInstance().getCookie(url);
        if (raw == null) raw = "";
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String name = p.substring(0, eq), val = p.substring(eq + 1);
            if (sb.length() > 0) sb.append("; ");
            sb.append(name).append('=').append(val.length() > 16 ? "«" + val.length() + " caracteres»" : val);
        }
        String now = sb.toString();
        if (now.equals(lastCookies)) return;
        lastCookies = now;
        capture.record("{\"type\":\"cookies\",\"url\":" + quote(url) + ",\"cookies\":" + quote(now) + "}");
    }

    /** Puente que usa el grabador. Se llama desde un hilo del WebView. */
    public final class Bridge {
        @JavascriptInterface
        public void record(String json) {
            capture.record(json);
            runOnUiThread(() -> updateCounter());
        }
    }

    private void showStep() {
        int i = capture.step();
        stepTitle.setText(t(R.string.step_title, i + 1, steps.length, steps[i]));
        stepHelp.setText(help(i));
        notFoundBtn.setVisibility(i == 2 || i == 3 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (i == 2 && ("app-only".equals(support) || "none".equals(support))) {
            // La ficha ya dice que no se puede: se salta directamente al cierre de sesión.
            capture.setBlockOutcome(support);
            capture.setStep(4);
            showStep();
            return;
        }
        nextBtn.setText(t(i == steps.length - 1 ? R.string.finish : R.string.done_next));
        updateCounter();
    }

    /** Ayuda de cada paso; en el 3 y el 4 usa la ficha del catálogo si la hay. */
    private String help(int i) {
        if ((i == 2 || i == 3) && !where.isEmpty()) {
            String path = where.replace("\n", " → ");
            String h = t(i == 2 ? R.string.go_to : R.string.same_place, path);
            if (!avoid.isEmpty()) h += "\n" + t(R.string.dont_touch_one, avoid.split("\n")[0]);
            return h;
        }
        if (i == 2 && !avoid.isEmpty()) return help[i] + "\n" + t(R.string.dont_touch_one, avoid.split("\n")[0]);
        return help[i];
    }

    /** Si el panel no tiene bloqueo, se termina sin perder el tiempo y se ofrece avisar al catálogo. */
    private void notFound() {
        String[] items = { t(R.string.nf_app_only), t(R.string.nf_none), t(R.string.nf_keep) };
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.nf_title))
                .setItems(items, (d, w) -> {
                    if (w == 2) return;
                    capture.setBlockOutcome(w == 0 ? "app-only" : "none");
                    capture.setStep(4);
                    showStep();
                    new AlertDialog.Builder(this)
                            .setTitle(t(R.string.understood))
                            .setMessage(t(R.string.nf_msg))
                            .setPositiveButton(t(R.string.ok), null)
                            .show();
                })
                .show();
    }

    private void updateCounter() {
        counter.setText(t(R.string.recording_counter, capture.count()) + (capture.isFull() ? t(R.string.limit_reached) : ""));
    }

    private void nextStep() {
        int i = capture.step();
        if (i < steps.length - 1) {
            capture.setStep(i + 1);
            showStep();
        } else {
            finishRecording();
        }
    }

    private void finishRecording() {
        final String text = capture.promptForAi(asset("ia/RECETAS.md"), asset("recetas/zte-zxhn-f6640p.json"));
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.recording_ready, capture.routerTitle()))
                .setMessage(t(R.string.recording_ready_msg, capture.count(), capture.fileName(), capture.slug()))
                .setPositiveButton(t(R.string.share_ai), (d, w) -> {
                    share(text);
                    maybeReport();
                })
                .setNeutralButton(t(R.string.save_phone), (d, w) -> {
                    maybeReport();
                    save(text);
                })
                .setNegativeButton(t(R.string.keep_recording), null)
                .show();
    }

    /** Ofrece avisar al catálogo solo si el usuario ha descubierto algo que la ficha no decía. */
    private void maybeReport() {
        // support solo llega si la ficha está comprobada; si no, el aviso sirve para confirmarla.
        if (!capture.blockOutcome().isEmpty() && !capture.blockOutcome().equals(support)) offerReport();
    }

    /** Abre el formulario «Ficha de router» de GitHub ya rellenado con lo comprobado. */
    private void offerReport() {
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.report_q))
                .setMessage(t(R.string.report_msg, capture.routerTitle()))
                .setPositiveButton(t(R.string.report), (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        com.leira.wificasa.core.catalog.Catalog.reportUrl(
                                nz(getIntent().getStringExtra(EXTRA_VENDOR)), nz(getIntent().getStringExtra(EXTRA_MODEL)),
                                nz(getIntent().getStringExtra(EXTRA_ISP)), capture.blockOutcome(),
                                new java.util.ArrayList<String>(), new java.util.ArrayList<String>(),
                                "Comprobado con el modo aprendizaje de WiFi Casa.")))))
                .setNegativeButton(t(R.string.not_now), null)
                .show();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private void save(String text) {
        pendingText = text;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, capture.fileName());
        startActivityForResult(i, REQ_SAVE);
    }

    private void share(String text) {
        try {
            ShareProvider.shareText(this, capture.fileName(), text,
                    t(R.string.share_ai_msg, capture.routerTitle()), t(R.string.share_ai));
        } catch (Exception e) {
            Toast.makeText(this, t(R.string.share_error, e.getMessage()), Toast.LENGTH_LONG).show();
            save(text);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE || resultCode != RESULT_OK || data == null || pendingText == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            out.write(pendingText.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, t(R.string.recording_saved), Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, t(R.string.save_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void askBasicAuth(final HttpAuthHandler handler, String realm) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText user = new EditText(this);
        user.setHint(t(R.string.label_user));
        box.addView(user);
        final EditText pass = new EditText(this);
        pass.setHint(t(R.string.password));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.basic_auth) + (realm == null ? "" : " (" + realm + ")"))
                .setView(box)
                .setPositiveButton(t(R.string.login), (d, w) -> {
                    capture.addSecret(pass.getText().toString());
                    handler.proceed(user.getText().toString(), pass.getText().toString());
                })
                .setNegativeButton(t(R.string.cancel), (d, w) -> handler.cancel())
                .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.exit_q))
                .setMessage(t(R.string.exit_msg))
                .setPositiveButton(t(R.string.exit), (d, w) -> finish())
                .setNegativeButton(t(R.string.keep_going), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else confirmExit();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private String asset(String path) {
        try (InputStream in = getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
