package com.leira.wificasa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.leira.wificasa.core.Capability;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterCredentials;
import com.leira.wificasa.core.catalog.Catalog;
import com.leira.wificasa.core.catalog.CatalogConfig;
import com.leira.wificasa.core.recipe.Recipe;
import com.leira.wificasa.core.recipe.RecipeAdapter;
import com.leira.wificasa.core.recipe.RecipeTester;
import com.leira.wificasa.data.RecipeStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Flujos del recetario: buscar en el catálogo, aprender un router nuevo, importar una receta,
 * probarla (lectura y escritura controlada), instalarla y publicarla.
 */
final class RecipeFlows {

    /** Lo que los flujos necesitan de la pantalla principal. */
    interface Host {
        /** Credenciales de la ubicación actual, o null si falta la contraseña. */
        RouterCredentials credentials();
        /** Usa este adaptador en la ubicación actual y actualiza. */
        void useAdapter(String adapterId);
    }

    static final int REQ_OPEN_RECIPE = 52;

    private final Activity a;
    private final Host host;
    private final ExecutorService worker;
    private final RecipeStore recipes;

    RecipeFlows(Activity a, Host host, ExecutorService worker, RecipeStore recipes) {
        this.a = a;
        this.host = host;
        this.worker = worker;
        this.recipes = recipes;
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    // ================================================================== menú

    /** Opciones para un router que todavía no está en la app. */
    void showAddRouterMenu() {
        String[] items = {
                "🔎  Buscar en el catálogo",
                "🎓  Aprender un router nuevo (grabar su panel)",
                "📥  Importar una receta"
        };
        new AlertDialog.Builder(a)
                .setTitle("Añadir un modelo de router")
                .setItems(items, (d, w) -> {
                    if (w == 0) searchCatalog();
                    else if (w == 1) startLearning();
                    else importRecipe("");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ================================================================== catálogo

    void searchCatalog() {
        if (!CatalogConfig.configured()) {
            info("Catálogo", "El catálogo público todavía no está configurado en esta versión de la app.");
            return;
        }
        final RouterCredentials c = host.credentials();
        final AlertDialog wait = waiting("Consultando el catálogo…");
        worker.execute(() -> {
            List<Catalog.Entry> detected = new ArrayList<>();
            List<Catalog.Entry> all;
            String error = null;
            Catalog cat = new Catalog();
            try {
                all = cat.entries();
                if (c != null) detected = cat.detect(Catalog.probeRouter(c));
            } catch (Exception e) {
                all = new ArrayList<>();
                error = e.getMessage();
            }
            final List<Catalog.Entry> fAll = all, fDet = detected;
            final String fErr = error;
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (fErr != null) { info("Catálogo", "⚠ " + fErr); return; }
                showCatalogList(cat, fDet, fAll);
            });
        });
    }

    private void showCatalogList(Catalog cat, List<Catalog.Entry> detected, List<Catalog.Entry> all) {
        final List<Catalog.Entry> list = new ArrayList<>(detected);
        for (Catalog.Entry e : all) if (!list.contains(e)) list.add(e);
        if (list.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("Catálogo vacío")
                    .setMessage("Todavía no hay recetas en el catálogo. Puedes ser el primero: aprende tu router y publícalo.")
                    .setPositiveButton("Aprender mi router", (d, w) -> startLearning())
                    .setNegativeButton("Cerrar", null).show();
            return;
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Catalog.Entry e = list.get(i);
            items[i] = (detected.contains(e) ? "★ " : "") + e.title() + "\n" + e.subtitle();
        }
        new AlertDialog.Builder(a)
                .setTitle(detected.isEmpty() ? "Recetas del catálogo" : "★ = coincide con tu router")
                .setItems(items, (d, w) -> downloadAndInstall(cat, list.get(w)))
                .setNeutralButton("No está el mío", (d, w) -> startLearning())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void downloadAndInstall(Catalog cat, Catalog.Entry e) {
        final AlertDialog wait = waiting("Descargando y comprobando la receta…");
        worker.execute(() -> {
            String err = null;
            RecipeAdapter ad = null;
            try {
                ad = recipes.install(cat.download(e));
            } catch (Exception ex) {
                err = ex.getMessage();
            }
            final String fErr = err;
            final RecipeAdapter fAd = ad;
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (fErr != null) { info("No se ha instalado", "⚠ " + fErr); return; }
                testRead(fAd, false);
            });
        });
    }

    // ================================================================== aprender

    void startLearning() {
        RouterCredentials c = host.credentials();
        new AlertDialog.Builder(a)
                .setTitle("Aprender un router nuevo")
                .setMessage("Se abrirá el panel de tu router dentro de la app y te guiaré en 5 pasos: entrar, "
                        + "ver los aparatos, cortar Internet a uno, devolvérselo y salir.\n\n"
                        + "La app graba lo que hace el panel para poder escribir su receta. La contraseña y las "
                        + "claves WiFi se borran de la grabación. Nada se envía a ningún sitio hasta que tú decidas "
                        + "compartirla.\n\n"
                        + "Hazlo conectado al WiFi de ese router.")
                .setPositiveButton("Empezar", (d, w) -> {
                    Intent i = new Intent(a, LearnActivity.class);
                    i.putExtra(LearnActivity.EXTRA_HOST, c == null ? "192.168.1.1" : c.host);
                    if (c != null) i.putExtra(LearnActivity.EXTRA_PASSWORD, c.password);
                    a.startActivity(i);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ================================================================== importar

    void importRecipe(String initial) {
        LinearLayout box = box();
        TextView t = new TextView(a);
        t.setText("Pega aquí la receta (el JSON que te ha dado la IA) o ábrela desde un archivo.");
        t.setTextColor(Color.parseColor("#6B665E"));
        box.addView(t);
        final EditText text = new EditText(a);
        text.setText(initial);
        text.setMinLines(6);
        text.setMaxLines(12);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setTextSize(12);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = new Button(a);
        paste.setAllCaps(false);
        paste.setText("📋 Pegar");
        paste.setOnClickListener(v -> text.setText(clipboardText()));
        Button open = new Button(a);
        open.setAllCaps(false);
        open.setText("📂 Abrir archivo");
        final AlertDialog[] dlg = new AlertDialog[1];
        open.setOnClickListener(v -> {
            if (dlg[0] != null) dlg[0].dismiss();
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            a.startActivityForResult(i, REQ_OPEN_RECIPE);
        });
        row.addView(paste);
        row.addView(open);
        box.addView(row);
        box.addView(text);
        dlg[0] = new AlertDialog.Builder(a)
                .setTitle("Importar receta")
                .setView(box)
                .setPositiveButton("Validar", (d, w) -> validate(stripFences(text.getText().toString())))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Resultado de "Abrir archivo". Devuelve true si era para nosotros. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_OPEN_RECIPE) return false;
        if (resultCode != Activity.RESULT_OK || data == null) return true;
        try (InputStream in = a.getContentResolver().openInputStream(data.getData())) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 512 * 1024) bo.write(buf, 0, n);
            importRecipe(bo.toString("UTF-8"));
        } catch (Exception e) {
            info("Importar receta", "⚠ No se pudo leer el archivo: " + e.getMessage());
        }
        return true;
    }

    private void validate(final String text) {
        final Recipe r;
        try {
            r = Recipe.parse(text);
        } catch (Exception e) {
            new AlertDialog.Builder(a)
                    .setTitle("La receta no es válida")
                    .setMessage(e.getMessage() + "\n\nCopia este error y pásaselo a la IA para que la corrija.")
                    .setPositiveButton("Copiar error", (d, w) -> copy("Error al validar la receta en WiFi Casa: "
                            + e.getMessage()))
                    .setNeutralButton("Volver", (d, w) -> importRecipe(text))
                    .setNegativeButton("Cerrar", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle("Receta válida")
                .setMessage(r.displayName() + " · versión " + r.version()
                        + "\nPuede: " + capsText(r) + "\nHuella: " + r.fingerprint().substring(0, 16) + "…\n\n"
                        + "Ahora la probaré con tu router, primero solo leyendo (sin cambiar nada).")
                .setPositiveButton("Probar", (d, w) -> {
                    RecipeAdapter ad = new RecipeAdapter(r);
                    pendingText = text;
                    testRead(ad, true);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String pendingText;

    // ================================================================== pruebas

    private void testRead(final RecipeAdapter ad, final boolean imported) {
        final RouterCredentials c = host.credentials();
        if (c == null) {
            info("Falta la contraseña", "Primero pon la dirección, el usuario y la contraseña del router en Ajustes.");
            return;
        }
        final AlertDialog wait = waiting("Probando la receta (solo lectura)…");
        worker.execute(() -> {
            final RecipeTester.Report rep = RecipeTester.readTest(ad, c);
            a.runOnUiThread(() -> {
                wait.dismiss();
                AlertDialog.Builder b = new AlertDialog.Builder(a)
                        .setTitle(rep.readOk ? "Lectura correcta" : "La prueba de lectura ha fallado")
                        .setView(scrollText(rep.text()));
                boolean canWrite = ad.capabilities().contains(Capability.BLOCK)
                        && ad.capabilities().contains(Capability.UNBLOCK);
                if (rep.readOk && canWrite) {
                    b.setPositiveButton("Probar a cortar…", (d, w) -> pickDeviceForWrite(ad, rep, imported));
                } else if (rep.readOk) {
                    b.setPositiveButton("Instalar", (d, w) -> finishInstall(ad, rep, imported));
                }
                if (!rep.readOk) b.setNeutralButton("Copiar informe", (d, w) -> copy(reportForAi(rep)));
                b.setNegativeButton(rep.readOk ? "Cancelar" : "Cerrar", null).show();
            });
        });
    }

    private void pickDeviceForWrite(final RecipeAdapter ad, final RecipeTester.Report rep, final boolean imported) {
        final List<NetDevice> list = new ArrayList<>(rep.devices);
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            NetDevice d = list.get(i);
            items[i] = (d.hostName.isEmpty() ? d.mac : d.hostName) + "  " + d.ip + (d.active ? " · conectado" : "");
        }
        new AlertDialog.Builder(a)
                .setTitle("¿A qué aparato corto Internet unos segundos?")
                .setItems(items, (d, w) -> {
                    final NetDevice target = list.get(w);
                    final AlertDialog wait = waiting("Cortando y devolviendo Internet a "
                            + (target.hostName.isEmpty() ? target.mac : target.hostName) + "…");
                    final RouterCredentials c = host.credentials();
                    worker.execute(() -> {
                        RecipeTester.writeTest(ad, c, target, rep);
                        a.runOnUiThread(() -> {
                            wait.dismiss();
                            AlertDialog.Builder b = new AlertDialog.Builder(a)
                                    .setTitle(rep.writeOk ? "¡La receta funciona!" : "La prueba de corte ha fallado")
                                    .setView(scrollText(rep.text()));
                            if (rep.writeOk) {
                                b.setPositiveButton("Instalar y usar", (x, y) -> finishInstall(ad, rep, imported));
                            } else {
                                b.setNeutralButton("Copiar informe", (x, y) -> copy(reportForAi(rep)));
                            }
                            b.setNegativeButton("Cerrar", null).show();
                        });
                    });
                })
                .setNegativeButton("Saltar esta prueba", (d, w) -> finishInstall(ad, rep, imported))
                .show();
    }

    private void finishInstall(RecipeAdapter ad, RecipeTester.Report rep, boolean imported) {
        RecipeAdapter installed = ad;
        if (imported && pendingText != null) {
            try {
                installed = recipes.install(pendingText);
            } catch (Exception e) {
                info("No se ha instalado", "⚠ " + e.getMessage());
                return;
            }
        }
        host.useAdapter(installed.id());
        final RecipeAdapter fin = installed;
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Receta instalada")
                .setMessage(fin.displayName() + " ya se usa en esta ubicación.");
        if (imported && rep.writeOk) {
            b.setMessage(fin.displayName() + " ya se usa en esta ubicación.\n\n¿La compartes en el catálogo para que "
                    + "otros usuarios con este router puedan usarla?");
            b.setPositiveButton("Publicar", (d, w) -> publish(fin, rep));
            b.setNegativeButton("Ahora no", null);
        } else {
            b.setPositiveButton("Vale", null);
        }
        b.show();
    }

    // ================================================================== publicar

    private void publish(RecipeAdapter ad, RecipeTester.Report rep) {
        if (!CatalogConfig.configured()) {
            info("Catálogo", "El catálogo público todavía no está configurado en esta versión de la app.");
            return;
        }
        String text = recipes.installedText(ad.recipe().id());
        if (text == null) { info("Publicar", "Esta receta viene con la app: ya está en el catálogo."); return; }
        copy(text);
        new AlertDialog.Builder(a)
                .setTitle("Publicar en el catálogo")
                .setMessage("He copiado la receta. Se abrirá GitHub con el formulario ya preparado:\n\n"
                        + "1. Inicia sesión en GitHub (o crea una cuenta gratis).\n"
                        + "2. Mantén pulsado el recuadro «Receta» y elige Pegar.\n"
                        + "3. Pulsa «Create» o «Submit new issue».\n\n"
                        + "El catálogo la validará solo y te avisará cuando esté publicada.")
                .setPositiveButton("Abrir GitHub", (d, w) ->
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.publishUrl(ad.recipe(), rep.text())))))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ================================================================== utilidades

    private String reportForAi(RecipeTester.Report rep) {
        return "La receta ha fallado al probarla en WiFi Casa. Corrígela con este informe:\n" + rep.text();
    }

    private static String capsText(Recipe r) {
        List<String> s = new ArrayList<>();
        if (r.capabilities().contains(Capability.LIST_DEVICES)) s.add("listar");
        if (r.capabilities().contains(Capability.READ_ACCESS)) s.add("leer estado");
        if (r.capabilities().contains(Capability.BLOCK)) s.add("cortar");
        if (r.capabilities().contains(Capability.UNBLOCK)) s.add("devolver");
        return s.isEmpty() ? "nada" : String.join(", ", s);
    }

    /** Las IA a veces envuelven el JSON entre ``` ; se quitan. */
    static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1) : "";
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        int start = t.indexOf('{'), end = t.lastIndexOf('}');
        return start >= 0 && end > start ? t.substring(start, end + 1) : t;
    }

    private String clipboardText() {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
        return t == null ? "" : t.toString();
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WiFi Casa", text));
        Toast.makeText(a, "Copiado", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout box() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        return box;
    }

    private ScrollView scrollText(String s) {
        ScrollView sv = new ScrollView(a);
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(dp(20), dp(8), dp(20), dp(8));
        sv.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private AlertDialog waiting(String msg) {
        return new AlertDialog.Builder(a).setMessage(msg).setCancelable(false).show();
    }

    private void info(String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("Vale", null).show();
    }
}
