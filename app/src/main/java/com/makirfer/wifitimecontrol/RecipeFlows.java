package com.makirfer.wifitimecontrol;

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

import com.makirfer.wifitimecontrol.core.Capability;
import com.makirfer.wifitimecontrol.core.NetDevice;
import com.makirfer.wifitimecontrol.core.RouterCredentials;
import com.makirfer.wifitimecontrol.core.catalog.Catalog;
import com.makirfer.wifitimecontrol.core.catalog.CatalogConfig;
import com.makirfer.wifitimecontrol.core.recipe.Recipe;
import com.makirfer.wifitimecontrol.core.recipe.RecipeAdapter;
import com.makirfer.wifitimecontrol.core.recipe.RecipeTester;
import com.makirfer.wifitimecontrol.data.RecipeStore;

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
        /** Dirección y usuario guardados en la ubicación actual ("" si no hay). */
        /** Lector de la pegatina con la cámara (lo gestiona la actividad). */
        PegatinaScanner scanner();
        /** Id del adaptador que usa ahora la ubicación actual. */
        String currentAdapterId();
        String savedHost();
        String savedUser();
        boolean hasPassword();
        /** Guarda los datos de acceso en la ubicación actual (contraseña vacía = no cambiarla). */
        void saveCredentials(String host, String user, String pass) throws Exception;
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

    /** Texto traducido (res/values/strings.xml y sus traducciones). */
    private String t(int id, Object... x) { return x.length == 0 ? a.getString(id) : a.getString(id, x); }

    /** Estado de una receta del catálogo, en el idioma del móvil. */
    private String subtitle(Catalog.Entry e) {
        int st;
        switch (e.status) {
            case "community-verified": st = R.string.status_community; break;
            case "tested-device": st = R.string.status_tested; break;
            case "simulator": st = R.string.status_simulator; break;
            default: st = R.string.status_untested;
        }
        return "v" + e.version + " · " + t(st) + (e.isps.isEmpty() ? "" : " · " + String.join(", ", e.isps));
    }

    /** Cuánto fiarse de una ficha, en el idioma del móvil. */
    private String confidence(Catalog.Sheet sh) {
        switch (sh.confidence) {
            case "confirmed": return t(R.string.conf_confirmed, sh.reports);
            case "official": return t(R.string.conf_official);
            case "probable": return t(R.string.conf_probable);
            default: return t(R.string.conf_web);
        }
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    // ================================================================== menú

    /** Opciones para un router que todavía no está en la app. */
    void showAddRouterMenu() {
        if (!a.getSharedPreferences("wificasa", Context.MODE_PRIVATE).getBoolean("guia_vista", false)) {
            a.getSharedPreferences("wificasa", Context.MODE_PRIVATE).edit().putBoolean("guia_vista", true).apply();
            showGuide();
            return;
        }
        String[] items = {
                t(R.string.menu_guide), t(R.string.menu_catalog), t(R.string.menu_learn), t(R.string.menu_import)
        };
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.add_router_title))
                .setItems(items, (d, w) -> {
                    if (w == 0) showGuide();
                    else if (w == 1) searchCatalog();
                    else if (w == 2) startLearning();
                    else importRecipe("");
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    static final int REQ_GUIDE = 53;

    /** Guía en fichas ilustradas (GuideActivity). Al terminar, sigue con lo que elija la persona. */
    void showGuide() {
        a.startActivityForResult(new Intent(a, GuideActivity.class), REQ_GUIDE);
    }

    // ================================================================== catálogo

    void searchCatalog() {
        if (!CatalogConfig.configured()) {
            info(t(R.string.catalog), t(R.string.catalog_not_configured));
            return;
        }
        final RouterCredentials c = host.credentials();
        final AlertDialog wait = waiting(t(R.string.catalog_loading));
        worker.execute(() -> {
            List<Catalog.Entry> detected = new ArrayList<>();
            List<Catalog.Entry> all;
            List<Catalog.Sheet> sheets;
            String error = null;
            Catalog cat = new Catalog();
            try {
                all = cat.entries();
                sheets = cat.sheets();
                if (c != null) detected = cat.detect(Catalog.probeRouter(c));
            } catch (Exception e) {
                all = new ArrayList<>();
                sheets = new ArrayList<>();
                error = e.getMessage();
            }
            final List<Catalog.Entry> fAll = all, fDet = detected;
            final List<Catalog.Sheet> fSheets = sheets;
            final String fErr = error;
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (fErr != null) { info(t(R.string.catalog), "⚠ " + fErr); return; }
                if (fSheets.isEmpty()) showCatalogList(cat, fDet, fAll);
                else showRouterList(cat, fSheets, fDet, fAll, c);
            });
        });
    }

    /** Todos los routers del catálogo (fichas), con su estado; al tocar uno se abre su ficha. */
    private void showRouterList(Catalog cat, List<Catalog.Sheet> sheets, List<Catalog.Entry> detected,
                                List<Catalog.Entry> all, RouterCredentials c) {
        final List<Catalog.Sheet> list = new ArrayList<>(sheets);
        final java.util.Set<String> det = new java.util.HashSet<>();
        for (Catalog.Entry e : detected) det.add(e.id);
        java.util.Collections.sort(list, (x, y) -> {
            int dx = det.contains(x.recipe) ? 0 : 1, dy = det.contains(y.recipe) ? 0 : 1;
            if (dx != dy) return dx - dy;
            String ix = x.isps.isEmpty() ? "~" : x.isps.get(0), iy = y.isps.isEmpty() ? "~" : y.isps.get(0);
            int k = ix.compareToIgnoreCase(iy);
            return k != 0 ? k : x.title().compareToIgnoreCase(y.title());
        });
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Catalog.Sheet sh = list.get(i);
            // Cada router dice con palabras qué se puede hacer con él, sin tener que recordar iconos.
            String what = !sh.recipe.isEmpty() ? t(R.string.cat_recipe) : sh.canBlock() ? t(R.string.cat_can_block)
                    : ("app-only".equals(sh.support) || "none".equals(sh.support)) ? t(R.string.cat_cannot_block)
                    : t(R.string.cat_unknown);
            String isps = sh.isps.isEmpty() ? "" : android.text.TextUtils.join(", ", sh.isps);
            items[i] = (det.contains(sh.recipe) ? "★ " : "") + sh.title() + (isps.isEmpty() ? "" : " · " + isps)
                    + "\n" + what;
        }
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.catalog_routers, list.size()))
                .setItems(items, (d, w) -> {
                    Catalog.Sheet sh = list.get(w);
                    Catalog.Entry recipe = null;
                    for (Catalog.Entry e : all) if (e.id.equals(sh.recipe)) recipe = e;
                    showSheet(cat, sh, recipe, c, sh.vendor, sh.model, sh.isps.isEmpty() ? "" : sh.isps.get(0));
                })
                .setNeutralButton(t(R.string.mine_not_listed), (d, w) -> startLearning())
                .setNegativeButton(t(R.string.close), null)
                .show();
    }

    private void showCatalogList(Catalog cat, List<Catalog.Entry> detected, List<Catalog.Entry> all) {
        final List<Catalog.Entry> list = new ArrayList<>(detected);
        for (Catalog.Entry e : all) if (!list.contains(e)) list.add(e);
        if (list.isEmpty()) {
            new AlertDialog.Builder(a).setTitle(t(R.string.catalog_empty))
                    .setMessage(t(R.string.catalog_empty_msg))
                    .setPositiveButton(t(R.string.learn_my_router), (d, w) -> startLearning())
                    .setNegativeButton(t(R.string.close), null).show();
            return;
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Catalog.Entry e = list.get(i);
            items[i] = (detected.contains(e) ? "★ " : "") + e.title() + "\n" + subtitle(e);
        }
        new AlertDialog.Builder(a)
                .setTitle(detected.isEmpty() ? t(R.string.catalog_recipes) : t(R.string.star_matches))
                .setItems(items, (d, w) -> downloadAndInstall(cat, list.get(w)))
                .setNeutralButton(t(R.string.mine_not_listed), (d, w) -> startLearning())
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void downloadAndInstall(Catalog cat, Catalog.Entry e) {
        final AlertDialog wait = waiting(t(R.string.downloading_recipe));
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
                if (fErr != null) { info(t(R.string.not_installed), "⚠ " + fErr); return; }
                testRead(fAd, false);
            });
        });
    }

    // ================================================================== aprender

    void startLearning() {
        final RouterCredentials c = host.credentials();
        LinearLayout box = box();
        TextView t = new TextView(a);
        t.setText(t(R.string.learn_sticker));
        t.setTextColor(Color.parseColor("#6B665E"));
        box.addView(t);
        final EditText vendor = field(box, t(R.string.hint_vendor));
        final EditText model = field(box, t(R.string.hint_model));
        final EditText isp = field(box, t(R.string.hint_isp));
        box.addView(photoButton(() -> host.scanner().scan(p -> {
            if (!p.vendor.isEmpty()) vendor.setText(p.vendor);
            if (!p.model.isEmpty()) model.setText(p.model);
            if (!p.isp.isEmpty()) isp.setText(p.isp);
        })));
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.learn_new_title))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.next), (d, w) -> {
                    String v = vendor.getText().toString().trim(), m = model.getText().toString().trim();
                    if (v.isEmpty() || m.isEmpty()) {
                        Toast.makeText(a, t(R.string.need_vendor_model), Toast.LENGTH_LONG).show();
                        startLearning();
                        return;
                    }
                    checkSheet(c, v, m, isp.getText().toString().trim());
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    /**
     * Antes de grabar, mira en el catálogo qué se sabe de este router: si ya tiene receta, si no
     * permite bloquear (para no hacer perder el tiempo) o dónde está el bloqueo (para guiar).
     */
    private void checkSheet(final RouterCredentials c, final String vendor, final String model, final String isp) {
        if (!CatalogConfig.configured()) { confirmLearning(c, vendor, model, isp, null); return; }
        final AlertDialog wait = waiting(t(R.string.looking_sheet));
        worker.execute(() -> {
            Catalog cat = new Catalog();
            Catalog.Sheet sh = null;
            Catalog.Entry recipe = null;
            try {
                sh = cat.findSheet(vendor, model);
                if (sh != null && !sh.recipe.isEmpty()) recipe = cat.entryById(sh.recipe);
            } catch (Exception ignored) {
                // sin conexión con el catálogo: se sigue sin ficha
            }
            final Catalog.Sheet fSh = sh;
            final Catalog.Entry fRe = recipe;
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (fSh == null) { noSheet(c, vendor, model, isp); return; }
                showSheet(cat, fSh, fRe, c, vendor, model, isp);
            });
        });
    }

    private void showSheet(Catalog cat, Catalog.Sheet sh, Catalog.Entry recipe, RouterCredentials c,
                           String vendor, String model, String isp) {
        StringBuilder sb = new StringBuilder();
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(sh.title());
        if (recipe != null) {
            sb.append(t(R.string.sheet_has_recipe, subtitle(recipe)));
            b.setPositiveButton(t(R.string.install_recipe), (d, w) -> downloadAndInstall(cat, recipe));
            b.setNeutralButton(t(R.string.record_anyway), (d, w) -> confirmLearning(c, vendor, model, isp, sh));
        } else if ("app-only".equals(sh.support) || "none".equals(sh.support)) {
            boolean appOnly = "app-only".equals(sh.support);
            sb.append(t(sh.confirmed() ? (appOnly ? R.string.sheet_app_only : R.string.sheet_none)
                    : (appOnly ? R.string.sheet_app_only_prob : R.string.sheet_none_prob)));
            if (!sh.how.isEmpty()) sb.append("\n\n").append(sh.how);
            if (sh.confirmed()) {
                sb.append("\n\n").append(t(R.string.sheet_list_only));
                b.setPositiveButton(t(R.string.record_list_only), (d, w) -> confirmLearning(c, vendor, model, isp, sh));
            } else {
                sb.append("\n\n").append(t(R.string.sheet_unchecked));
                b.setPositiveButton(t(R.string.check_myself), (d, w) -> confirmLearning(c, vendor, model, isp, sh));
            }
            b.setNegativeButton(t(R.string.dont_record), null);
        } else {
            sb.append(t(sh.canBlock() ? R.string.sheet_can_block : R.string.sheet_unknown));
            if (!sh.where.isEmpty()) {
                sb.append("\n\n").append(t(R.string.where_is_it));
                for (int i = 0; i < sh.where.size(); i++) sb.append("\n").append(i + 1).append(". ").append(sh.where.get(i));
            }
            if (!sh.how.isEmpty()) sb.append("\n\n").append(sh.how);
            b.setPositiveButton(t(R.string.start_recording), (d, w) -> confirmLearning(c, vendor, model, isp, sh));
            b.setNegativeButton(t(R.string.cancel), null);
        }
        if (!sh.avoid.isEmpty()) {
            sb.append("\n\n").append(t(R.string.dont_touch));
            for (String av : sh.avoid) sb.append("\n• ").append(av);
        }
        if (!sh.passwordHint.isEmpty()) sb.append("\n\n🔑 ").append(sh.passwordHint);
        sb.append("\n\nℹ ").append(confidence(sh));
        if (!sh.sources.isEmpty()) sb.append("\n").append(t(R.string.source, sh.sources.get(0)));
        if (!java.util.Locale.getDefault().getLanguage().equals("es")) sb.append("\n\n").append(t(R.string.catalog_data_lang));
        b.setView(scrollText(sb.toString())).show();
    }

    private void noSheet(RouterCredentials c, String vendor, String model, String isp) {
        new AlertDialog.Builder(a)
                .setTitle(vendor + " " + model)
                .setView(scrollText(t(R.string.no_sheet)))
                .setPositiveButton(t(R.string.start_recording), (d, w) -> confirmLearning(c, vendor, model, isp, null))
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void confirmLearning(RouterCredentials c, String vendor, String model, String isp, Catalog.Sheet sh) {
        new AlertDialog.Builder(a)
                .setTitle(vendor + " " + model)
                .setMessage(t(R.string.confirm_learning))
                .setPositiveButton(t(R.string.start), (d, w) -> {
                    Intent i = new Intent(a, LearnActivity.class);
                    i.putExtra(LearnActivity.EXTRA_HOST, c == null ? "192.168.1.1" : c.host);
                    if (c != null) i.putExtra(LearnActivity.EXTRA_PASSWORD, c.password);
                    i.putExtra(LearnActivity.EXTRA_VENDOR, vendor);
                    i.putExtra(LearnActivity.EXTRA_MODEL, model);
                    i.putExtra(LearnActivity.EXTRA_ISP, isp);
                    if (sh != null) {
                        i.putExtra(LearnActivity.EXTRA_WHERE, String.join("\n", sh.where));
                        i.putExtra(LearnActivity.EXTRA_AVOID, String.join("\n", sh.avoid));
                        // Solo se salta el bloqueo si está comprobado; si no, la persona lo comprueba.
                        if (sh.confirmed()) i.putExtra(LearnActivity.EXTRA_SUPPORT, sh.support);
                    }
                    a.startActivity(i);
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    /** Botón «📷 Leer la pegatina». */
    private Button photoButton(Runnable onClick) {
        Button b = new Button(a);
        b.setAllCaps(false);
        b.setText(t(R.string.photo_button));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private TextView smallLabel(String text) {
        TextView l = new TextView(a);
        l.setText(text);
        l.setTextSize(12);
        l.setTextColor(Color.parseColor("#6B665E"));
        l.setPadding(0, dp(10), 0, 0);
        return l;
    }

    private EditText field(LinearLayout box, String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(e);
        return e;
    }

    // ================================================================== importar

    void importRecipe(String initial) {
        LinearLayout box = box();
        TextView t = new TextView(a);
        t.setText(t(R.string.import_help));
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
        paste.setText(t(R.string.paste));
        paste.setOnClickListener(v -> text.setText(clipboardText()));
        Button open = new Button(a);
        open.setAllCaps(false);
        open.setText(t(R.string.open_file));
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
                .setTitle(t(R.string.import_title))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.validate), (d, w) -> validate(stripFences(text.getText().toString())))
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    /** Resultado de "Abrir archivo". Devuelve true si era para nosotros. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_GUIDE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (GuideActivity.ACTION_LEARN.equals(data.getStringExtra(GuideActivity.RESULT_ACTION))) startLearning();
                else searchCatalog();
            }
            return true;
        }
        if (requestCode != REQ_OPEN_RECIPE) return false;
        if (resultCode != Activity.RESULT_OK || data == null) return true;
        try (InputStream in = a.getContentResolver().openInputStream(data.getData())) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 512 * 1024) bo.write(buf, 0, n);
            importRecipe(bo.toString("UTF-8"));
        } catch (Exception e) {
            info(t(R.string.import_title), t(R.string.file_read_error, e.getMessage()));
        }
        return true;
    }

    private void validate(final String text) {
        final Recipe r;
        try {
            r = Recipe.parse(text);
        } catch (Exception e) {
            new AlertDialog.Builder(a)
                    .setTitle(t(R.string.recipe_invalid))
                    .setMessage(t(R.string.recipe_invalid_msg, e.getMessage()))
                    .setPositiveButton(t(R.string.copy_error), (d, w) -> copy("Error al validar la receta en WiFi Time Control: "
                            + e.getMessage()))
                    .setNeutralButton(t(R.string.back), (d, w) -> importRecipe(text))
                    .setNegativeButton(t(R.string.close), null)
                    .show();
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.recipe_valid))
                .setMessage(t(R.string.recipe_valid_msg, r.displayName(), r.version(), capsText(r),
                        r.fingerprint().substring(0, 16)))
                .setPositiveButton(t(R.string.test), (d, w) -> {
                    RecipeAdapter ad = new RecipeAdapter(r);
                    pendingText = text;
                    testRead(ad, true);
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private String pendingText;

    // ================================================================== pruebas

    /**
     * Antes de probar una receta se piden los datos de acceso del router en la misma pantalla,
     * con lo que diga su ficha del catálogo (dirección, usuario, dónde está la contraseña).
     */
    private void testRead(final RecipeAdapter ad, final boolean imported) {
        final AlertDialog wait = waiting(t(R.string.looking_sheet));
        worker.execute(() -> {
            Catalog.Sheet sh = null;
            try {
                if (CatalogConfig.configured()) sh = new Catalog().findSheet(ad.recipe().vendor(), ad.recipe().model());
            } catch (Exception ignored) {
                // sin catálogo: se piden los datos sin pistas
            }
            final Catalog.Sheet fSh = sh;
            a.runOnUiThread(() -> { wait.dismiss(); askCredentials(ad, imported, fSh); });
        });
    }

    private void askCredentials(final RecipeAdapter ad, final boolean imported, Catalog.Sheet sh) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView info = new TextView(a);
        StringBuilder sb = new StringBuilder(t(R.string.cred_intro, ad.recipe().displayName()));
        if (sh != null && !sh.passwordHint.isEmpty()) sb.append("\n\n🔑 ").append(sh.passwordHint);
        else sb.append("\n\n").append(t(R.string.cred_sticker));
        info.setText(sb.toString());
        box.addView(info);
        // Los datos guardados solo valen si son de este mismo router; si la ubicación usaba otro
        // (p. ej. el ZTE con usuario admin), se proponen los de la receta y su ficha.
        boolean same = ad.id().equals(host.currentAdapterId());
        String hostVal = same && !host.savedHost().isEmpty() ? host.savedHost()
                : sh != null && !sh.loginAddress.isEmpty() ? sh.loginAddress : ad.defaultHost();
        String userVal = same && !host.savedUser().isEmpty() ? host.savedUser()
                : sh != null && !sh.loginUser.isEmpty() ? sh.loginUser : ad.defaultUser();
        final boolean keepPass = same && host.hasPassword();
        box.addView(smallLabel(t(R.string.label_host)));
        final EditText hostF = field(box, t(R.string.label_host));
        hostF.setText(hostVal);
        box.addView(smallLabel(t(R.string.label_user)));
        final EditText userF = field(box, t(R.string.label_user));
        userF.setText(userVal);
        box.addView(smallLabel(t(R.string.label_password)));
        final EditText passF = field(box, keepPass ? t(R.string.password_saved_hint) : t(R.string.password));
        passF.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(photoButton(() -> host.scanner().scan(p -> {
            if (!p.host.isEmpty()) hostF.setText(p.host);
            if (!p.user.isEmpty()) userF.setText(p.user);
            if (!p.password.isEmpty()) {
                passF.setText(p.password);
                // Se muestra lo leído para poder revisarlo (la lectura puede confundir 0/O o 1/l).
                passF.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
        })));
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.cred_title))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.test), (d, w) -> {
                    String p = passF.getText().toString();
                    if (p.isEmpty() && !keepPass) {
                        Toast.makeText(a, t(R.string.missing_password), Toast.LENGTH_LONG).show();
                        askCredentials(ad, imported, sh);
                        return;
                    }
                    try {
                        host.saveCredentials(hostF.getText().toString().trim(), userF.getText().toString().trim(), p);
                    } catch (Exception e) {
                        info(t(R.string.missing_password), "⚠ " + e.getMessage());
                        return;
                    }
                    runReadTest(ad, imported);
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void runReadTest(final RecipeAdapter ad, final boolean imported) {
        final RouterCredentials c0 = host.credentials();
        if (c0 == null) {
            info(t(R.string.missing_password), t(R.string.missing_password_msg));
            return;
        }
        // Si la ubicación no tenía dirección o usuario, se usan los de la receta.
        final RouterCredentials c = new RouterCredentials(host.savedHost().isEmpty() ? ad.defaultHost() : c0.host,
                host.savedUser().isEmpty() ? ad.defaultUser() : c0.user, c0.password);
        final AlertDialog wait = waiting(t(R.string.testing_read));
        worker.execute(() -> {
            final RecipeTester.Report rep = RecipeTester.readTest(ad, c);
            a.runOnUiThread(() -> {
                wait.dismiss();
                AlertDialog.Builder b = new AlertDialog.Builder(a)
                        .setTitle(t(rep.readOk ? R.string.read_ok : R.string.read_failed))
                        .setView(scrollText(rep.text()));
                boolean canWrite = ad.capabilities().contains(Capability.BLOCK)
                        && ad.capabilities().contains(Capability.UNBLOCK);
                if (rep.readOk && canWrite) {
                    b.setPositiveButton(t(R.string.try_cut), (d, w) -> pickDeviceForWrite(ad, rep, imported));
                } else if (rep.readOk) {
                    b.setPositiveButton(t(R.string.install), (d, w) -> finishInstall(ad, rep, imported));
                }
                if (!rep.readOk) {
                    b.setNeutralButton(t(R.string.copy_report), (d, w) -> copy(reportForAi(rep)));
                    b.setPositiveButton(t(R.string.send_report), (d, w) -> sendReport(ad, rep));
                }
                b.setNegativeButton(t(rep.readOk ? R.string.cancel : R.string.close), null).show();
            });
        });
    }

    private void pickDeviceForWrite(final RecipeAdapter ad, final RecipeTester.Report rep, final boolean imported) {
        final List<NetDevice> list = new ArrayList<>(rep.devices);
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            NetDevice d = list.get(i);
            items[i] = (d.hostName.isEmpty() ? d.mac : d.hostName) + "  " + d.ip + (d.active ? " · " + t(R.string.connected) : "");
        }
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.pick_device))
                .setItems(items, (d, w) -> {
                    final NetDevice target = list.get(w);
                    final AlertDialog wait = waiting(t(R.string.cutting_and_back,
                            target.hostName.isEmpty() ? target.mac : target.hostName));
                    final RouterCredentials c = host.credentials();
                    worker.execute(() -> {
                        RecipeTester.writeTest(ad, c, target, rep);
                        a.runOnUiThread(() -> {
                            wait.dismiss();
                            AlertDialog.Builder b = new AlertDialog.Builder(a)
                                    .setTitle(t(rep.writeOk ? R.string.recipe_works : R.string.cut_failed))
                                    .setView(scrollText(rep.text()));
                            if (rep.writeOk) {
                                b.setPositiveButton(t(R.string.install_and_use), (x, y) -> finishInstall(ad, rep, imported));
                            } else {
                                b.setNeutralButton(t(R.string.copy_report), (x, y) -> copy(reportForAi(rep)));
                                b.setPositiveButton(t(R.string.send_report), (x, y) -> sendReport(ad, rep));
                            }
                            b.setNegativeButton(t(R.string.close), null).show();
                        });
                    });
                })
                .setNegativeButton(t(R.string.skip_test), (d, w) -> finishInstall(ad, rep, imported))
                .show();
    }

    private void finishInstall(RecipeAdapter ad, RecipeTester.Report rep, boolean imported) {
        RecipeAdapter installed = ad;
        if (imported && pendingText != null) {
            try {
                installed = recipes.install(pendingText);
            } catch (Exception e) {
                info(t(R.string.not_installed), "⚠ " + e.getMessage());
                return;
            }
        }
        host.useAdapter(installed.id());
        final RecipeAdapter fin = installed;
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(t(R.string.recipe_installed))
                .setMessage(t(R.string.recipe_in_use, fin.displayName()));
        boolean listOnly = !installed.capabilities().contains(Capability.BLOCK);
        if (imported && (rep.writeOk || (listOnly && rep.readOk))) {
            b.setMessage(t(R.string.recipe_share_q, fin.displayName()));
            b.setPositiveButton(t(R.string.publish), (d, w) -> publish(fin, rep));
            b.setNeutralButton(t(R.string.send_file), (d, w) -> shareRecipe(fin));
            b.setNegativeButton(t(R.string.not_now), null);
        } else {
            b.setPositiveButton(t(R.string.ok), null);
            if (imported) b.setNeutralButton(t(R.string.send_file), (d, w) -> shareRecipe(fin));
        }
        b.show();
    }

    /** Envía la receta como archivo receta-<id>.json (a otra persona, a la IA para mejorarla…). */
    private void shareRecipe(RecipeAdapter ad) {
        String text = recipes.installedText(ad.recipe().id());
        if (text == null) return;
        try {
            ShareProvider.shareText(a, "receta-" + ad.recipe().id() + ".json", text, null, t(R.string.send_recipe));
        } catch (Exception e) {
            info(t(R.string.send_recipe), "⚠ " + e.getMessage());
        }
    }

    // ================================================================== publicar

    private void publish(RecipeAdapter ad, RecipeTester.Report rep) {
        if (!CatalogConfig.configured()) {
            info(t(R.string.catalog), t(R.string.catalog_not_configured));
            return;
        }
        String text = recipes.installedText(ad.recipe().id());
        if (text == null) { info(t(R.string.publish), t(R.string.already_bundled)); return; }
        copy(text);
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.publish_title))
                .setMessage(t(R.string.publish_msg, ad.recipe().displayName()))
                .setPositiveButton(t(R.string.open_github), (d, w) ->
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.publishUrl(ad.recipe(), rep.text())))))
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    // ================================================================== utilidades

    /** Manda el informe de la prueba (sin contraseñas) por WhatsApp, correo… con un toque. */
    private void sendReport(RecipeAdapter ad, RecipeTester.Report rep) {
        Recipe r = ad.recipe();
        Feedback.send(a, t(R.string.report_subject, r.displayName()),
                reportForAi(rep) + "\n\nReceta: " + r.id() + " " + r.version() + "\n" + Feedback.deviceInfo(a, r.displayName()));
    }

    private String reportForAi(RecipeTester.Report rep) {
        return "La receta ha fallado al probarla en WiFi Time Control. Corrígela con este informe:\n" + rep.text();
    }

    private String capsText(Recipe r) {
        List<String> s = new ArrayList<>();
        if (r.capabilities().contains(Capability.LIST_DEVICES)) s.add(t(R.string.cap_list));
        if (r.capabilities().contains(Capability.READ_ACCESS)) s.add(t(R.string.cap_read));
        if (r.capabilities().contains(Capability.BLOCK)) s.add(t(R.string.cap_block));
        if (r.capabilities().contains(Capability.UNBLOCK)) s.add(t(R.string.cap_unblock));
        return s.isEmpty() ? t(R.string.cap_none) : String.join(", ", s);
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
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WiFi Time Control", text));
        Toast.makeText(a, t(R.string.copied), Toast.LENGTH_SHORT).show();
    }

    private LinearLayout box() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        return box;
    }

    private ScrollView scrolled(LinearLayout box) {
        ScrollView sv = new ScrollView(a);
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
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
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton(t(R.string.ok), null).show();
    }
}
