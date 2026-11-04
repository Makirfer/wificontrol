package com.leira.wificasa.core.learn;

import com.leira.wificasa.core.recipe.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grabación del modo aprendizaje (formato "wificasa-capture/1").
 *
 * Reúne lo que el grabador anota mientras el usuario usa el panel del router y produce un
 * texto SANEADO: sin la contraseña (ni sus formas codificadas o con hash más comunes), sin
 * valores de campos que parezcan claves (WiFi, PSK, WPA…) y sin los avisos internos del
 * grabador. Es lo que se comparte con la IA para escribir la receta.
 */
public final class Capture {

    public static final String FORMAT = "wificasa-capture/1";
    /** Tope de lo que se graba (en caracteres), para que el archivo se pueda compartir. */
    static final int MAX_TOTAL = 1_500_000;
    static final String MASK_PASSWORD = "«CONTRASEÑA»";
    static final String MASK = "«OCULTO»";

    public static final String[] STEPS = {
            "Inicia sesión en el panel del router",
            "Abre la lista de dispositivos conectados",
            "Corta Internet a un dispositivo",
            "Devuélvele Internet",
            "Cierra sesión"
    };

    private final String host;
    private final List<Object[]> events = new ArrayList<>(); // {paso, Map evento}
    private final Set<String> secrets = new LinkedHashSet<>();
    private final Set<String> scriptsSeen = new HashSet<>();
    private int step = 0;
    private int size = 0;
    private boolean full = false;

    public Capture(String host, String knownPassword) {
        this.host = host;
        addSecret(knownPassword);
    }

    public synchronized void setStep(int s) { step = s; }

    public synchronized int step() { return step; }

    public synchronized int count() { return events.size(); }

    public synchronized boolean isFull() { return full; }

    public synchronized void addSecret(String s) {
        if (s != null && s.length() >= 3) secrets.add(s);
    }

    /** Recibe un evento del grabador (texto JSON). Los "secret" solo alimentan el saneado. */
    @SuppressWarnings("unchecked")
    public synchronized void record(String json) {
        Object o;
        try {
            o = Json.parse(json);
        } catch (Exception e) {
            return;
        }
        if (!(o instanceof Map)) return;
        Map<String, Object> ev = (Map<String, Object>) o;
        Object type = ev.get("type");
        if ("secret".equals(type)) {
            addSecret(ev.get("value") instanceof String ? (String) ev.get("value") : null);
            return;
        }
        if ("script".equals(type)) {
            String url = String.valueOf(ev.get("url"));
            if (!scriptsSeen.add(url)) return;
        }
        if (full) return;
        size += json.length();
        if (size > MAX_TOTAL) {
            full = true;
            return;
        }
        events.add(new Object[]{step, ev});
    }

    /** Grabación saneada, lista para guardar o compartir. */
    public synchronized String sanitizedJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()));
        root.put("routerBase", host);
        List<Object> steps = new ArrayList<>();
        for (String s : STEPS) steps.add(s);
        root.put("steps", steps);
        root.put("truncated", full);
        List<Object> list = new ArrayList<>();
        for (Object[] e : events) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("step", ((Integer) e[0]) + 1);
            item.put("event", clean(e[1]));
            list.add(item);
        }
        root.put("events", list);
        return Json.write(root);
    }

    /** Texto completo para la IA: instrucciones, documentación del formato, ejemplo y grabación. */
    public String promptForAi(String recipeDocs, String exampleRecipe) {
        return "Eres un asistente que escribe recetas para la app WiFi Casa.\n\n"
                + "TAREA: a partir de la GRABACIÓN de abajo (lo que hizo el panel web de un router mientras el "
                + "usuario iniciaba sesión, abría la lista de dispositivos, cortaba Internet a uno, se lo devolvía y "
                + "cerraba sesión), escribe UNA receta en formato \"wificasa-recipe/1\".\n\n"
                + "REGLAS:\n"
                + "1. Responde solo con el JSON de la receta, sin explicaciones ni ``` alrededor.\n"
                + "2. Usa únicamente los pasos, funciones, filtros y campos de la documentación. No inventes otros.\n"
                + "3. La contraseña aparece como " + MASK_PASSWORD + " y otros valores sensibles como " + MASK
                + ". Usa ${cred.password} y ${cred.user}; si la contraseña se cifra o se resume antes de enviarse, "
                + "deduce cómo a partir de los scripts del panel y usa las funciones permitidas.\n"
                + "4. Pon \"validation\": {\"status\": \"untested\"} y \"minEngine\": 1.\n"
                + "5. Rellena \"router\" (vendor, model, commercialName, isps) con lo que se vea en la grabación y añade "
                + "en \"router.detect\" 1 o 2 expresiones regulares que reconozcan la página de inicio de este modelo.\n"
                + "6. Declara en \"capabilities\" solo lo que la grabación demuestre que se puede hacer.\n"
                + "7. Si falta información para alguna operación, omítela y explica el motivo en \"limitations\".\n\n"
                + "===== DOCUMENTACIÓN DEL FORMATO =====\n" + recipeDocs + "\n\n"
                + "===== EJEMPLO DE RECETA COMPLETA (otro router) =====\n" + exampleRecipe + "\n\n"
                + "===== GRABACIÓN =====\n" + sanitizedJson() + "\n";
    }

    // ------------------------------------------------------------------ saneado

    private static final Pattern URLENC_FIELD = Pattern.compile(
            "((?:^|[?&])[^=&\\s\"]*(?i:pass|pwd|psk|wpa|passphrase|secret|wepkey)[^=&\\s\"]*=)(?!«CONTRASEÑA»)[^&\\s\"]*");
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(\"[^\"]*(?i:pass|pwd|psk|wpa|passphrase|secret|wepkey)[^\"]*\"\\s*:\\s*\")(?!«CONTRASEÑA»)[^\"]*(\")");
    private static final Pattern XML_FIELD = Pattern.compile(
            "(<ParaName>[^<]*(?i:pass|psk|wpa|passphrase|secret|key)[^<]*</ParaName>\\s*<ParaValue>)[^<]*");
    private static final Pattern XML_TAG = Pattern.compile(
            "(<([A-Za-z_]*(?i:pass|psk|wpa|passphrase|secret|wepkey)[A-Za-z_]*)>)[^<]*(</\\2>)");
    private static final Pattern HTML_INPUT = Pattern.compile(
            "(<input[^>]*(?:type=[\"']?password|name=[\"']?[^\"' >]*(?i:pass|psk|wpa|key))[^>]*value=[\"'])[^\"']*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_ASSIGN = Pattern.compile(
            "(\\b[A-Za-z_$]*(?i:password|passwd|psk|wpakey|passphrase)[A-Za-z_$]*\\s*[:=]\\s*[\"'])[^\"']{4,}([\"'])");

    private Object clean(Object v) {
        if (v instanceof String) return cleanText((String) v);
        if (v instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                String k = String.valueOf(e.getKey());
                Object val = e.getValue();
                if (val instanceof String && k.matches("(?i).*(pass|pwd|psk|wpa|passphrase|secret).*")
                        && !MASK_PASSWORD.equals(cleanText((String) val))) {
                    out.put(k, ((String) val).isEmpty() ? "" : MASK);
                } else {
                    out.put(k, clean(val));
                }
            }
            return out;
        }
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            // Campos de formulario: [nombre, valor]
            if (l.size() == 2 && l.get(0) instanceof String && l.get(1) instanceof String
                    && ((String) l.get(0)).matches("(?i).*(pass|pwd|psk|wpa|passphrase|secret|wepkey).*")) {
                List<Object> pair = new ArrayList<>();
                pair.add(l.get(0));
                String v0 = cleanText((String) l.get(1));
                pair.add(v0.isEmpty() || v0.equals(MASK_PASSWORD) ? v0 : MASK);
                return pair;
            }
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(clean(o));
            return out;
        }
        return v;
    }

    String cleanText(String s) {
        for (String secret : secrets) {
            for (String form : forms(secret)) {
                if (form.length() >= 3) s = s.replace(form, MASK_PASSWORD);
            }
        }
        s = URLENC_FIELD.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK));
        s = JSON_FIELD.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK) + "$2");
        s = XML_FIELD.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK));
        s = XML_TAG.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK) + "$3");
        s = HTML_INPUT.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK));
        s = JS_ASSIGN.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(MASK) + "$2");
        return s;
    }

    /** La contraseña tal cual y en las formas en que los paneles suelen enviarla. */
    static List<String> forms(String p) {
        List<String> f = new ArrayList<>();
        f.add(p);
        try {
            f.add(java.net.URLEncoder.encode(p, "UTF-8"));
            f.add(java.net.URLEncoder.encode(p, "UTF-8").replace("+", "%20"));
        } catch (Exception ignored) { /* nada */ }
        byte[] b = p.getBytes(StandardCharsets.UTF_8);
        f.add(Base64.getEncoder().encodeToString(b));
        f.add(hex("MD5", b));
        f.add(hex("SHA-1", b));
        f.add(hex("SHA-256", b));
        f.add(hex("MD5", b).toUpperCase(Locale.ROOT));
        f.add(hex("SHA-256", b).toUpperCase(Locale.ROOT));
        return f;
    }

    private static String hex(String alg, byte[] b) {
        try {
            byte[] h = MessageDigest.getInstance(alg).digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
