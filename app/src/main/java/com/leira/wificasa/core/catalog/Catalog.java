package com.leira.wificasa.core.catalog;

import com.leira.wificasa.core.RouterCredentials;
import com.leira.wificasa.core.RouterException;
import com.leira.wificasa.core.recipe.Json;
import com.leira.wificasa.core.recipe.Recipe;
import com.leira.wificasa.core.recipe.RecipeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente del catálogo público de recetas (formato "wificasa-catalog/1").
 *
 * El catálogo es un índice JSON con una entrada por receta: fabricante, modelo, estado de
 * validación, huella SHA-256 y, opcionalmente, patrones para reconocer el router por su
 * página de inicio. Cada receta descargada se comprueba contra su huella antes de usarla.
 */
public final class Catalog {

    public static final String FORMAT = "wificasa-catalog/1";
    static final int MAX_DOWNLOAD = 1024 * 1024;

    /** Una receta del índice. */
    public static final class Entry {
        public String id = "", version = "", vendor = "", model = "", commercialName = "", status = "",
                sha256 = "", path = "";
        public List<String> isps = new ArrayList<>();
        public List<String> capabilities = new ArrayList<>();
        public List<String> detect = new ArrayList<>();

        public String title() {
            return commercialName.isEmpty() ? vendor + " " + model : vendor + " " + commercialName + " (" + model + ")";
        }

        public String subtitle() {
            String st;
            switch (status) {
                case "community-verified": st = "verificada por la comunidad"; break;
                case "tested-device": st = "probada en un router real"; break;
                case "simulator": st = "solo probada en simulador"; break;
                default: st = "sin probar";
            }
            return "v" + version + " · " + st + (isps.isEmpty() ? "" : " · " + String.join(", ", isps));
        }

        boolean matches(String q) {
            String hay = (vendor + " " + model + " " + commercialName + " " + String.join(" ", isps) + " " + id)
                    .toLowerCase(Locale.ROOT);
            for (String w : q.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                if (!w.isEmpty() && !hay.contains(w)) return false;
            }
            return true;
        }
    }

    /** Ficha de un modelo de router: si se puede bloquear, dónde y qué no tocar. */
    public static final class Sheet {
        public String id = "", vendor = "", model = "", commercialName = "", support = "unknown", how = "",
                scope = "", notes = "", recipe = "", loginAddress = "", loginUser = "", passwordHint = "";
        public List<String> isps = new ArrayList<>(), aliases = new ArrayList<>(), where = new ArrayList<>(),
                avoid = new ArrayList<>();
        public int reports = 1;

        public String title() {
            return commercialName.isEmpty() ? vendor + " " + model : vendor + " " + model + " (" + commercialName + ")";
        }

        public boolean canBlock() { return "panel".equals(support); }
    }

    private final String base;
    private List<Entry> entries;
    private List<Sheet> sheets;

    public Catalog() { this(CatalogConfig.rawBase()); }

    public Catalog(String base) { this.base = base.endsWith("/") ? base : base + "/"; }

    // ------------------------------------------------------------------ índice

    public List<Entry> entries() throws RouterException {
        load();
        return entries;
    }

    public List<Sheet> sheets() throws RouterException {
        load();
        return sheets;
    }

    private void load() throws RouterException {
        if (entries != null) return;
        String text = get(base + "index.json", true);
        entries = parseIndex(text);
        sheets = parseSheets(text);
    }

    public Entry entryById(String id) throws RouterException {
        for (Entry e : entries()) if (e.id.equals(id)) return e;
        return null;
    }

    /**
     * Busca la ficha de un router por lo que el usuario ha escrito (marca y modelo de la pegatina).
     * Compara de forma flexible: sin mayúsculas, espacios ni guiones, y también con los alias.
     */
    public Sheet findSheet(String vendor, String model) throws RouterException {
        String m = norm(model), v = norm(vendor);
        if (m.length() < 3) return null;
        Sheet best = null;
        int bestScore = 0;
        for (Sheet sh : sheets()) {
            int score = 0;
            List<String> names = new ArrayList<>(sh.aliases);
            names.add(sh.model);
            names.add(sh.commercialName);
            for (String n : names) {
                String nn = norm(n);
                if (nn.length() < 3) continue;
                if (nn.equals(m)) score = Math.max(score, 3);
                else if (nn.length() >= 4 && m.length() >= 4 && (nn.contains(m) || m.contains(nn))) score = Math.max(score, 2);
            }
            if (score > 0 && !v.isEmpty() && norm(sh.vendor).equals(v)) score++;
            for (String isp : sh.isps) if (!v.isEmpty() && norm(isp).equals(v)) score++;
            if (score > bestScore) { bestScore = score; best = sh; }
        }
        return best;
    }

    static String norm(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Enlace al formulario «Ficha de router» de GitHub con todo rellenado: la persona solo
     * tiene que revisarlo y enviarlo. No incluye ninguna contraseña.
     */
    public static String reportUrl(String vendor, String model, String isp, String support,
                                   List<String> where, List<String> avoid, String notes) {
        return CatalogConfig.repoUrl() + "/issues/new?template=ficha-router.yml"
                + "&title=" + enc("Ficha: " + vendor + " " + model)
                + "&marca=" + enc(vendor) + "&modelo=" + enc(model) + "&compania=" + enc(isp)
                + "&bloqueo=" + enc(support)
                + "&donde=" + enc(String.join("\n", where))
                + "&evitar=" + enc(String.join("\n", avoid))
                + "&notas=" + enc(notes);
    }

    public List<Entry> search(String query) throws RouterException {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries()) if (query == null || query.trim().isEmpty() || e.matches(query)) out.add(e);
        return out;
    }

    /** Recetas cuyo patrón de detección encaja con la página de inicio del router. */
    public List<Entry> detect(String routerHomePage) throws RouterException {
        List<Entry> out = new ArrayList<>();
        if (routerHomePage == null || routerHomePage.isEmpty()) return out;
        for (Entry e : entries()) {
            for (String rx : e.detect) {
                try {
                    if (Pattern.compile(rx, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(routerHomePage).find()) {
                        out.add(e);
                        break;
                    }
                } catch (Exception ignored) {
                    // patrón no válido en el índice: se ignora
                }
            }
        }
        return out;
    }

    /** Descarga la receta, comprueba su huella con la del índice y la valida. Devuelve el texto. */
    public String download(Entry e) throws RouterException {
        if (!e.path.matches("[a-z0-9][a-z0-9_./-]*\\.json") || e.path.contains("..")) {
            throw new RecipeException("Ruta de receta no válida en el catálogo.");
        }
        String text = get(base + e.path, true);
        String hash = sha256(text);
        if (!hash.equalsIgnoreCase(e.sha256)) {
            throw new RecipeException("La receta descargada no coincide con su huella del catálogo: no se instala.");
        }
        Recipe r = Recipe.parse(text);
        if (!r.id().equals(e.id) || !r.version().equals(e.version)) {
            throw new RecipeException("La receta descargada no corresponde a la entrada del catálogo.");
        }
        return text;
    }

    /** Lee la página de inicio del router (sin iniciar sesión) para reconocer el modelo. */
    public static String probeRouter(RouterCredentials c) {
        try {
            return get(c.baseUrl() + "/", false);
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ publicar

    /**
     * Enlace para proponer una receta en el catálogo: abre el formulario de GitHub con el
     * título y el informe de pruebas ya rellenados. La receta en sí va por el portapapeles,
     * porque no cabe en un enlace.
     */
    public static String publishUrl(Recipe r, String report) {
        String rep = report.length() > 1500 ? report.substring(0, 1500) + "…" : report;
        return CatalogConfig.repoUrl() + "/issues/new?template=nueva-receta.yml"
                + "&title=" + enc("Receta: " + r.displayName() + " v" + r.version())
                + "&informe=" + enc(rep + "\nHuella: " + r.fingerprint());
    }

    // ------------------------------------------------------------------ interno

    static List<Entry> parseIndex(String text) throws RecipeException {
        Object o = Json.parse(text);
        if (!(o instanceof Map)) throw new RecipeException("Índice del catálogo no válido.");
        Map<?, ?> root = (Map<?, ?>) o;
        if (!FORMAT.equals(root.get("format"))) throw new RecipeException("Formato de catálogo no admitido.");
        List<Entry> out = new ArrayList<>();
        Object list = root.get("recipes");
        if (!(list instanceof List)) return out;
        for (Object x : (List<?>) list) {
            if (!(x instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) x;
            Entry e = new Entry();
            e.id = s(m, "id");
            e.version = s(m, "version");
            e.vendor = s(m, "vendor");
            e.model = s(m, "model");
            e.commercialName = s(m, "commercialName");
            e.status = s(m, "status");
            e.sha256 = s(m, "sha256");
            e.path = s(m, "path");
            e.isps = l(m, "isps");
            e.capabilities = l(m, "capabilities");
            e.detect = l(m, "detect");
            if (!e.id.isEmpty() && !e.path.isEmpty() && !e.sha256.isEmpty()) out.add(e);
        }
        return out;
    }

    static List<Sheet> parseSheets(String text) throws RecipeException {
        Object o = Json.parse(text);
        List<Sheet> out = new ArrayList<>();
        if (!(o instanceof Map) || !(((Map<?, ?>) o).get("routers") instanceof List)) return out;
        for (Object x : (List<?>) ((Map<?, ?>) o).get("routers")) {
            if (!(x instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) x;
            Sheet sh = new Sheet();
            sh.id = s(m, "id");
            sh.vendor = s(m, "vendor");
            sh.model = s(m, "model");
            sh.commercialName = s(m, "commercialName");
            sh.isps = l(m, "isps");
            sh.aliases = l(m, "aliases");
            sh.avoid = l(m, "avoid");
            sh.recipe = s(m, "recipe");
            if (m.get("reports") instanceof Double) sh.reports = ((Double) m.get("reports")).intValue();
            if (m.get("block") instanceof Map) {
                Map<?, ?> b = (Map<?, ?>) m.get("block");
                String sup = s(b, "support");
                sh.support = sup.matches("panel|app-only|none|unknown") ? sup : "unknown";
                sh.where = l(b, "where");
                sh.how = s(b, "how");
                sh.scope = s(b, "scope");
                sh.notes = s(b, "notes");
            }
            if (m.get("login") instanceof Map) {
                Map<?, ?> lg = (Map<?, ?>) m.get("login");
                sh.loginAddress = s(lg, "address");
                sh.loginUser = s(lg, "user");
                sh.passwordHint = s(lg, "passwordHint");
            }
            if (!sh.id.isEmpty() && !sh.model.isEmpty()) out.add(sh);
        }
        return out;
    }

    private static String s(Map<?, ?> m, String k) { return m.get(k) instanceof String ? (String) m.get(k) : ""; }

    private static List<String> l(Map<?, ?> m, String k) {
        List<String> out = new ArrayList<>();
        if (m.get(k) instanceof List) for (Object o : (List<?>) m.get(k)) if (o instanceof String) out.add((String) o);
        return out;
    }

    static String get(String url, boolean internet) throws RouterException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setUseCaches(false);
            int code = c.getResponseCode();
            if (code == 404) throw new RouterException("No encontrado en el catálogo (" + url + ").");
            if (code != 200) throw new RouterException("El catálogo ha respondido HTTP " + code + ".");
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_DOWNLOAD) throw new IOException("archivo demasiado grande");
            }
            is.close();
            return bo.toString("UTF-8");
        } catch (RouterException e) {
            throw e;
        } catch (IOException e) {
            throw new RouterException(internet ? "No se puede conectar con el catálogo. ¿Tienes conexión a Internet?"
                    : "No se puede conectar con el router.", e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String sha256(String text) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }
}
