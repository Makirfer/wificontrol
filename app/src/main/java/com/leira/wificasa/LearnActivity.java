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

    private static final String[] HELP = {
            "Escribe el usuario y la contraseña del router como siempre y entra.",
            "Busca la página donde salen los aparatos conectados. Suele llamarse Red local, Clientes, "
                    + "Dispositivos o DHCP.",
            "Elige un aparato que no moleste y córtale Internet: busca Control parental, Control de acceso, "
                    + "Bloquear o Filtro MAC, y guarda.",
            "Deshaz lo del paso anterior para devolverle Internet, y guarda.",
            "Pulsa Salir o Cerrar sesión en el panel. Luego pulsa Terminar."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        notFoundBtn = new Button(this);
        notFoundBtn.setAllCaps(false);
        notFoundBtn.setText("No lo encuentro");
        notFoundBtn.setOnClickListener(v -> notFound());
        row.addView(notFoundBtn);
        Button cancel = new Button(this);
        cancel.setAllCaps(false);
        cancel.setText("Cancelar");
        cancel.setOnClickListener(v -> confirmExit());
        row.addView(cancel);
        nextBtn = new Button(this);
        nextBtn.setAllCaps(false);
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setBackgroundColor(C_ACCENT);
        nextBtn.setOnClickListener(v -> nextStep());
        row.addView(nextBtn);
        bar.addView(row);
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
                        .setPositiveButton("Aceptar", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.confirm()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(LearnActivity.this).setMessage(message)
                        .setPositiveButton("Aceptar", (d, w) -> {
                            capture.record("{\"type\":\"dialog\",\"kind\":\"confirm\",\"answer\":true,\"message\":"
                                    + quote(message) + "}");
                            result.confirm();
                        })
                        .setNegativeButton("Cancelar", (d, w) -> result.cancel())
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
        stepTitle.setText("Paso " + (i + 1) + " de " + Capture.STEPS.length + " · " + Capture.STEPS[i]);
        stepHelp.setText(help(i));
        notFoundBtn.setVisibility(i == 2 || i == 3 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (i == 2 && ("app-only".equals(support) || "none".equals(support))) {
            // La ficha ya dice que no se puede: se salta directamente al cierre de sesión.
            capture.setBlockOutcome(support);
            capture.setStep(4);
            showStep();
            return;
        }
        nextBtn.setText(i == Capture.STEPS.length - 1 ? "Terminar" : "Hecho ▶");
        updateCounter();
    }

    /** Ayuda de cada paso; en el 3 y el 4 usa la ficha del catálogo si la hay. */
    private String help(int i) {
        if ((i == 2 || i == 3) && !where.isEmpty()) {
            String path = where.replace("\n", " → ");
            String h = (i == 2 ? "Ve a: " + path + ". Córtale Internet a un aparato que no sea tu móvil y guarda."
                    : "En el mismo sitio (" + path + "), devuélvele Internet y guarda.");
            if (!avoid.isEmpty()) h += "\n⚠ No toques: " + avoid.split("\n")[0];
            return h;
        }
        if (i == 2 && !avoid.isEmpty()) return HELP[i] + "\n⚠ No toques: " + avoid.split("\n")[0];
        return HELP[i];
    }

    /** Si el panel no tiene bloqueo, se termina sin perder el tiempo y se ofrece avisar al catálogo. */
    private void notFound() {
        String[] items = {
                "El panel dice que solo se puede desde la app de la compañía",
                "No hay ningún apartado para bloquear un aparato",
                "Seguir buscando"
        };
        new AlertDialog.Builder(this)
                .setTitle("¿Qué has encontrado?")
                .setItems(items, (d, w) -> {
                    if (w == 2) return;
                    capture.setBlockOutcome(w == 0 ? "app-only" : "none");
                    capture.setStep(4);
                    showStep();
                    new AlertDialog.Builder(this)
                            .setTitle("Entendido")
                            .setMessage("Cierra sesión en el panel y pulsa Terminar. La grabación servirá para que la app "
                                    + "te enseñe los aparatos de este router (sin pausarlos).\n\n"
                                    + "Al terminar podrás avisar al catálogo para que nadie más lo intente con este modelo.")
                            .setPositiveButton("Vale", null)
                            .show();
                })
                .show();
    }

    private void updateCounter() {
        counter.setText("● Grabando · " + capture.count() + " anotaciones" + (capture.isFull() ? " (límite alcanzado)" : ""));
    }

    private void nextStep() {
        int i = capture.step();
        if (i < Capture.STEPS.length - 1) {
            capture.setStep(i + 1);
            showStep();
        } else {
            finishRecording();
        }
    }

    private void finishRecording() {
        final String text = capture.promptForAi(asset("ia/RECETAS.md"), asset("recetas/zte-zxhn-f6640p.json"));
        new AlertDialog.Builder(this)
                .setTitle("Grabación lista · " + capture.routerTitle())
                .setMessage(capture.count() + " anotaciones en «" + capture.fileName() + "». La contraseña del router "
                        + "y las claves WiFi se han borrado.\n\n"
                        + "Siguientes pasos:\n"
                        + "1. Pulsa «Compartir con una IA» y elige Claude, ChatGPT o Gemini. El archivo ya lleva las "
                        + "instrucciones; si te pide un mensaje, escribe: «Lee las instrucciones del archivo y hazme "
                        + "la receta».\n"
                        + "2. Guarda la receta que te devuelva (receta-" + capture.slug() + ".json) o copia su texto.\n"
                        + "3. En WiFi Casa: Ajustes → Modelo de router → Mi router no está → Importar una receta.\n\n"
                        + "También puedes guardar el archivo en el móvil para hacerlo más tarde.")
                .setPositiveButton("Compartir con una IA", (d, w) -> {
                    share(text);
                    maybeReport();
                })
                .setNeutralButton("Guardar en el móvil", (d, w) -> {
                    maybeReport();
                    save(text);
                })
                .setNegativeButton("Seguir grabando", null)
                .show();
    }

    /** Ofrece avisar al catálogo solo si el usuario ha descubierto algo que la ficha no decía. */
    private void maybeReport() {
        if (!capture.blockOutcome().isEmpty() && !capture.blockOutcome().equals(support)) offerReport();
    }

    /** Abre el formulario «Ficha de router» de GitHub ya rellenado con lo comprobado. */
    private void offerReport() {
        new AlertDialog.Builder(this)
                .setTitle("¿Avisas al catálogo?")
                .setMessage("Así, quien tenga un " + capture.routerTitle() + " sabrá antes de empezar que su panel no "
                        + "permite bloquear aparatos. Se abrirá GitHub con todo rellenado: solo tienes que enviarlo.")
                .setPositiveButton("Avisar", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        com.leira.wificasa.core.catalog.Catalog.reportUrl(
                                nz(getIntent().getStringExtra(EXTRA_VENDOR)), nz(getIntent().getStringExtra(EXTRA_MODEL)),
                                nz(getIntent().getStringExtra(EXTRA_ISP)), capture.blockOutcome(),
                                new java.util.ArrayList<String>(), new java.util.ArrayList<String>(),
                                "Comprobado con el modo aprendizaje de WiFi Casa.")))))
                .setNegativeButton("Ahora no", null)
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
                    "Lee las instrucciones del archivo y hazme la receta de WiFi Casa para mi router "
                            + capture.routerTitle() + ".", "Compartir con una IA");
        } catch (Exception e) {
            Toast.makeText(this, "⚠ No se pudo compartir (" + e.getMessage() + "). Guárdalo en el móvil.",
                    Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Grabación guardada.", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "⚠ No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void askBasicAuth(final HttpAuthHandler handler, String realm) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText user = new EditText(this);
        user.setHint("Usuario");
        box.addView(user);
        final EditText pass = new EditText(this);
        pass.setHint("Contraseña");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("El router pide usuario y contraseña" + (realm == null ? "" : " (" + realm + ")"))
                .setView(box)
                .setPositiveButton("Entrar", (d, w) -> {
                    capture.addSecret(pass.getText().toString());
                    handler.proceed(user.getText().toString(), pass.getText().toString());
                })
                .setNegativeButton("Cancelar", (d, w) -> handler.cancel())
                .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("¿Salir sin guardar?")
                .setMessage("Se perderá lo grabado.")
                .setPositiveButton("Salir", (d, w) -> finish())
                .setNegativeButton("Seguir", null)
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
