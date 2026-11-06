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
    private String vendor = "", model = "", isp = "";
    /** Lo que el usuario ha comprobado sobre el bloqueo: "" (lo ha grabado), "app-only" o "none". */
    private String blockOutcome = "";
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

    /** Marca, modelo y compañía que indica el usuario (vienen en la pegatina del router). */
    public synchronized void setRouter(String vendor, String model, String isp) {
        this.vendor = vendor == null ? "" : vendor.trim();
        this.model = model == null ? "" : model.trim();
        this.isp = isp == null ? "" : isp.trim();
    }

    /** "askey-rtf8316vw" a partir de marca y modelo; "router" si no se conocen. */
    public synchronized String slug() { return slug(vendor + " " + model); }

    public static String slug(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (n.length() > 60) n = n.substring(0, 60).replaceAll("-+$", "");
        return n.isEmpty() ? "router" : n;
    }

    /** Nombre del archivo de la grabación, p. ej. "grabacion-askey-rtf8316vw-2026-11-04.txt". */
    public String fileName() {
        return "grabacion-" + slug() + "-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".txt";
    }

    public synchronized String routerTitle() {
        String t = (vendor + " " + model).trim();
        return t.isEmpty() ? "router sin identificar" : t + (isp.isEmpty() ? "" : " (" + isp + ")");
    }

    public synchronized void setStep(int s) { step = s; }

    /** El usuario indica que el panel no permite bloquear ("app-only" o "none"). */
    public synchronized void setBlockOutcome(String outcome) { blockOutcome = outcome == null ? "" : outcome; }

    public synchronized String blockOutcome() { return blockOutcome; }

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

    /**
     * Valores asignados en el código de las páginas a variables con nombre de contraseña o clave
     * (p. ej. {@code wpaPskKey = htmlDecode('…')} o {@code sPasswd = mess_userpass("…")}).
     * Los paneles de algunos routers incrustan así la clave WiFi o la de administración.
     */
    private static final Pattern SECRET_ASSIGN = Pattern.compile(
            "\\b[A-Za-z_$]*(?i:password|passwd|psk|wpakey|passphrase|pwd|passw|secret)[A-Za-z_$0-9]*"
                    + "\\s*[:=]\\s*[A-Za-z_$.]*\\(?[A-Za-z_$.]*\\(?\\s*[\"']([^\"'\\s]{4,})[\"']");

    private void harvest(Object v) {
        if (v instanceof String) {
            Matcher m = SECRET_ASSIGN.matcher((String) v);
            while (m.find()) {
                String val = m.group(1);
                // Se descartan selectores y nombres de campos, que no son secretos.
                if (!val.matches(".*[#\\[\\]$(){}<>=,].*") && !val.matches("(?i).*(password|passwd|psk).*")) addSecret(val);
            }
        } else if (v instanceof Map) {
            for (Object o : ((Map<?, ?>) v).values()) harvest(o);
        } else if (v instanceof List) {
            for (Object o : (List<?>) v) harvest(o);
        }
    }

    /** Grabación saneada, lista para guardar o compartir. */
    public synchronized String sanitizedJson() {
        for (Object[] e : events) harvest(e[1]);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()));
        root.put("routerBase", host);
        Map<String, Object> router = new LinkedHashMap<>();
        router.put("vendor", vendor);
        router.put("model", model);
        router.put("isp", isp);
        router.put("suggestedId", slug());
        root.put("router", router);
        if (!blockOutcome.isEmpty()) root.put("blockOutcome", blockOutcome);
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

    /**
     * Archivo completo para compartir: unas líneas para la persona (qué hacer con él), las
     * instrucciones para la IA, la documentación del formato, un ejemplo y la grabación.
     * Está pensado para que funcione con cualquier IA (Claude, ChatGPT, Gemini…).
     */
    public String promptForAi(String recipeDocs, String exampleRecipe) {
        String id = slug();
        String line = "==================================================================\n";
        return line
                + "GRABACIÓN DE ROUTER PARA WIFI CASA · " + routerTitle() + "\n"
                + line
                + "PARA TI (la persona que ha grabado):\n"
                + "  1. Abre Claude, ChatGPT o Gemini y adjunta este archivo.\n"
                + "  2. Escribe: «Lee las instrucciones del archivo y hazme la receta».\n"
                + "  3. Te responderá con un bloque de texto que empieza por { : es la receta. Cópialo entero\n"
                + "     (botón «Copiar» del bloque, o mantén pulsado → Copiar).\n"
                + "  4. En WiFi Casa: Ajustes → Modelo de router → Mi router no está → Importar una receta,\n"
                + "     pulsa «Pegar» y luego «Validar». La app la probará con tu router.\n"
                + "  La contraseña del router y las claves WiFi se han borrado de este archivo. Aun así,\n"
                + "  compártelo solo con la IA.\n\n"
                + line
                + "PARA LA IA: INSTRUCCIONES\n"
                + line
                + "Eres un asistente que escribe recetas para la app Android WiFi Casa, que pausa y reanuda Internet a\n"
                + "dispositivos concretos hablando con el panel web del router.\n\n"
                + "TAREA: a partir de la GRABACIÓN del final (lo que hizo el panel web del router " + routerTitle()
                + " mientras el usuario seguía 5 pasos: 1 iniciar sesión, 2 abrir la lista de dispositivos, 3 cortar "
                + "Internet a uno, 4 devolvérselo, 5 cerrar sesión), escribe UNA receta en formato \"wificasa-recipe/1\".\n\n"
                + "CÓMO ENTREGARLA:\n"
                + "- Responde SIEMPRE con el JSON completo dentro de un único bloque de código, sin comentarios\n"
                + "  dentro: el usuario lo copiará y pegará desde el móvil, que normalmente no puede abrir\n"
                + "  archivos .json. Si además puedes crear archivos, ofrece también receta-" + id + ".json.\n"
                + "- Usa \"id\": \"" + id + "\" (marca-modelo en minúsculas con guiones).\n"
                + "- Después, explica al usuario en 2 o 3 frases sencillas qué hace la receta y qué no.\n\n"
                + (blockOutcome.isEmpty() ? "" : "IMPORTANTE: el usuario ha comprobado que este router NO permite cortar "
                        + "Internet a un aparato desde su panel web (" + ("app-only".equals(blockOutcome)
                        ? "el panel remite a la app de la compañía" : "no hay ningún apartado para hacerlo")
                        + "). Escribe una receta SOLO con \"capabilities\": [\"LIST_DEVICES\"] (y READ_ACCESS si se ve el "
                        + "estado), y explícalo en \"limitations\". No intentes improvisar un bloqueo con otros apartados.\n\n")
                + "REGLAS DE LA RECETA:\n"
                + "1. Usa únicamente los pasos, funciones, filtros y campos de la documentación. No inventes otros.\n"
                + "2. La contraseña aparece como " + MASK_PASSWORD + " y otros valores sensibles como " + MASK
                + ". Nunca escribas contraseñas en la receta: usa ${cred.password} y ${cred.user}. Si el panel cifra, "
                + "resume u ofusca la contraseña antes de enviarla, deduce cómo a partir de sus scripts y usa las "
                + "funciones permitidas.\n"
                + "3. Pon \"validation\": {\"status\": \"untested\"}, \"version\": \"0.1.0\" y \"minEngine\": 1 "
                + "(o 2 si usas xorChars).\n"
                + "4. Rellena \"router\" (vendor, model, commercialName, isps) y añade en \"router.detect\" 1 o 2 "
                + "expresiones regulares que reconozcan la página de inicio de este modelo sin iniciar sesión.\n"
                + "5. Declara en \"capabilities\" solo lo que la grabación demuestre. Para BLOCK/UNBLOCK, comprueba que "
                + "lo grabado corta de verdad el acceso a Internet del dispositivo (no confundas, por ejemplo, filtros de "
                + "puente o de VLAN con bloqueos).\n"
                + "6. Las peticiones que se hacen al cargar una página aparecen como eventos \"request\" (solo método y "
                + "dirección); su respuesta suele verse en el HTML de la página o deducirse de sus scripts.\n"
                + "7. Si falta información para alguna operación, omítela, explícalo en \"limitations\" y dile al "
                + "usuario qué paso concreto debe volver a grabar y dónde buscarlo en el panel.\n\n"
                + line + "DOCUMENTACIÓN DEL FORMATO\n" + line + recipeDocs + "\n\n"
                + line + "EJEMPLO DE RECETA COMPLETA (otro router)\n" + line + exampleRecipe + "\n\n"
                + line + "GRABACIÓN\n" + line + sanitizedJson() + "\n";
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
