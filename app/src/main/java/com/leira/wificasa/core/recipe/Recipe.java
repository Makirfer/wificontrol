package com.leira.wificasa.core.recipe;

import com.leira.wificasa.core.Capability;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Receta de router (formato "wificasa-recipe/1") ya validada.
 *
 * Una receta solo DESCRIBE peticiones HTTP al router y cómo leer sus respuestas, con un
 * vocabulario cerrado de pasos y funciones. No puede contener ni ejecutar código, ni hablar
 * con otro servidor que no sea el router configurado. Todo lo que no esté permitido
 * explícitamente se rechaza al cargarla. Las reglas completas están en docs/RECETAS.md.
 */
public final class Recipe {

    public static final String FORMAT = "wificasa-recipe/1";
    /** Versión del motor de recetas de esta app. Una receta con "minEngine" mayor se rechaza. */
    public static final int ENGINE_VERSION = 2;

    static final int MAX_STEPS = 60;
    static final int MAX_REGEX = 500;
    static final int MAX_FILE = 256 * 1024;

    static final Set<String> STEP_TYPES = set("request", "extract", "fail", "require", "set", "compute", "choose", "call");
    static final Set<String> FUNCTIONS = set("sha256Hex", "sha1Hex", "md5Hex", "base64", "xorChars", "rsaPkcs1Base64",
            "hexEscapeDecode", "xmlUnescape", "lower", "upper", "repeat");
    static final Set<String> TRANSFORMS = set("xmlUnescape", "hexEscapeDecode", "lower", "upper", "trim");
    static final Set<String> FILTERS = set("url", "lower", "upper", "or", "sha256Hex", "rsa");
    static final Set<String> OPERATIONS = set("login", "logout", "listDevices", "readAccess", "setBlocked");
    static final Set<String> STATES = set("ALLOWED", "BLOCKED", "SCHEDULED", "UNKNOWN");

    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([^}]*)\\}");
    private static final Pattern VAR_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,40}");
    private static final Pattern BUILTIN = Pattern.compile(
            "base|body|counter|blocked|match|cred\\.user|cred\\.password|device\\.(mac|hostName|ip)|device\\.extra\\.[A-Za-z0-9_.]{1,40}");

    final Map<String, Object> root;
    final Map<String, PublicKey> keys = new HashMap<>();
    private final String fingerprint;
    private final Set<Capability> capabilities;

    private Recipe(Map<String, Object> root, String fingerprint, Set<Capability> caps) {
        this.root = root;
        this.fingerprint = fingerprint;
        this.capabilities = caps;
    }

    // ------------------------------------------------------------------ acceso

    public String id() { return str(root, "id"); }
    public String version() { return str(root, "version"); }
    /** SHA-256 del archivo tal cual: detecta cualquier modificación respecto a la versión publicada. */
    public String fingerprint() { return fingerprint; }
    public Set<Capability> capabilities() { return capabilities; }

    public String displayName() {
        Map<String, Object> r = obj(root, "router");
        String commercial = str(r, "commercialName");
        String vm = str(r, "vendor") + " " + str(r, "model");
        return commercial.isEmpty() ? vm : str(r, "vendor") + " " + commercial + " (" + str(r, "model") + ")";
    }

    /** untested, simulator, tested-device o community-verified. */
    public String validationStatus() { return str(obj(root, "validation"), "status"); }

    public String defaultHost() { return str(obj(root, "defaults"), "host"); }
    public String defaultUser() { return str(obj(root, "defaults"), "user"); }

    @SuppressWarnings("unchecked")
    Map<String, Object> operation(String name) {
        Object o = obj(root, "operations").get(name);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    Map<String, Object> http() { return obj(root, "http"); }

    // ------------------------------------------------------------------ carga y validación

    public static Recipe parse(String text) throws RecipeException {
        if (text.length() > MAX_FILE) throw new RecipeException("La receta es demasiado grande.");
        Object o = Json.parse(text);
        if (!(o instanceof Map)) throw new RecipeException("La receta debe ser un objeto JSON.");
        @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) o;
        Validator v = new Validator();
        Set<Capability> caps = v.validateRoot(root);
        Recipe r = new Recipe(root, sha256(text), caps);
        for (Map.Entry<String, Object> e : obj(root, "keys").entrySet()) {
            r.keys.put(e.getKey(), parseKey(e.getKey(), (String) e.getValue()));
        }
        for (String k : v.usedKeys) {
            if (!r.keys.containsKey(k)) throw new RecipeException("La clave \"" + k + "\" no está definida en \"keys\".");
        }
        return r;
    }

    private static final class Validator {
        final Set<String> usedKeys = new HashSet<>();
        String where = "";

        Set<Capability> validateRoot(Map<String, Object> root) throws RecipeException {
            allowed(root, "receta", set("format", "id", "version", "minEngine", "router", "defaults", "capabilities",
                    "limitations", "validation", "changelog", "http", "keys", "operations"),
                    set("format", "id", "version", "minEngine", "router", "capabilities", "operations"));
            if (!FORMAT.equals(root.get("format"))) {
                throw new RecipeException("Formato no admitido: se esperaba \"" + FORMAT + "\".");
            }
            matches(root.get("id"), "[a-z0-9][a-z0-9-]{2,63}", "id");
            matches(root.get("version"), "\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}", "version");
            Object me = root.get("minEngine");
            if (!(me instanceof Double)) throw new RecipeException("\"minEngine\" debe ser un número.");
            if ((Double) me > ENGINE_VERSION) {
                throw new RecipeException("Esta receta necesita una versión más nueva de la app (motor "
                        + ((Double) me).intValue() + ").");
            }

            Map<String, Object> router = objReq(root, "router");
            allowed(router, "router", set("vendor", "model", "commercialName", "isps", "firmware", "detect"),
                    set("vendor", "model"));
            for (String k : Arrays.asList("vendor", "model", "commercialName")) optString(router, k);
            optStringList(router, "isps");
            optStringList(router, "firmware");
            optStringList(router, "detect");
            if (router.get("detect") instanceof List) {
                where = "router.detect";
                for (Object rx : (List<?>) router.get("detect")) regex(rx);
            }

            if (root.containsKey("defaults")) {
                Map<String, Object> d = objReq(root, "defaults");
                allowed(d, "defaults", set("host", "user"), set());
                optString(d, "host");
                optString(d, "user");
            }
            optStringList(root, "limitations");
            if (root.containsKey("validation")) {
                Map<String, Object> val = objReq(root, "validation");
                allowed(val, "validation", set("date", "status", "notes"), set("status"));
                if (!set("untested", "simulator", "tested-device", "community-verified").contains(val.get("status"))) {
                    throw new RecipeException("\"validation.status\" no válido.");
                }
            }
            if (root.containsKey("changelog")) {
                if (!(root.get("changelog") instanceof List)) throw new RecipeException("\"changelog\" debe ser una lista.");
                for (Object c : (List<?>) root.get("changelog")) {
                    if (!(c instanceof Map)) throw new RecipeException("Entrada de \"changelog\" no válida.");
                    allowed(asObj(c), "changelog", set("version", "date", "changes"), set("version", "changes"));
                }
            }
            if (root.containsKey("http")) {
                Map<String, Object> h = objReq(root, "http");
                allowed(h, "http", set("sessionCookie", "fixedCookies", "headers"), set());
                if (h.containsKey("sessionCookie")) matches(h.get("sessionCookie"), "[A-Za-z0-9_\\-]{1,40}", "http.sessionCookie");
                if (h.containsKey("fixedCookies")) matches(h.get("fixedCookies"), "[A-Za-z0-9_\\-=; ]{0,200}", "http.fixedCookies");
                where = "http.headers";
                if (h.containsKey("headers")) headers(objReq(h, "headers"), "http.headers");
            }
            if (root.containsKey("keys")) {
                for (Map.Entry<String, Object> e : objReq(root, "keys").entrySet()) {
                    matches(e.getKey(), "[A-Za-z0-9_]{1,40}", "nombre de clave");
                    if (!(e.getValue() instanceof String)) throw new RecipeException("La clave \"" + e.getKey() + "\" debe ser texto PEM.");
                }
            }

            // Capacidades declaradas → operaciones obligatorias
            if (!(root.get("capabilities") instanceof List)) throw new RecipeException("\"capabilities\" debe ser una lista.");
            Set<Capability> caps = EnumSet.noneOf(Capability.class);
            for (Object c : (List<?>) root.get("capabilities")) {
                try {
                    caps.add(Capability.valueOf(String.valueOf(c)));
                } catch (IllegalArgumentException e) {
                    throw new RecipeException("Capacidad desconocida: " + c);
                }
            }
            Map<String, Object> ops = objReq(root, "operations");
            allowed(ops, "operations", OPERATIONS, set("login"));
            need(caps, Capability.LIST_DEVICES, ops, "listDevices");
            need(caps, Capability.READ_ACCESS, ops, "readAccess");
            need(caps, Capability.BLOCK, ops, "setBlocked");
            need(caps, Capability.UNBLOCK, ops, "setBlocked");
            for (Map.Entry<String, Object> e : ops.entrySet()) operation(e.getKey(), asObj(e.getValue()));
            return Collections.unmodifiableSet(caps);
        }

        void need(Set<Capability> caps, Capability c, Map<String, Object> ops, String op) throws RecipeException {
            if (caps.contains(c) && !ops.containsKey(op)) {
                throw new RecipeException("Declara " + c + " pero falta la operación \"" + op + "\".");
            }
        }

        void operation(String name, Map<String, Object> op) throws RecipeException {
            where = name;
            Set<String> keys = set("steps");
            if (name.equals("listDevices")) keys.add("devices");
            if (name.equals("readAccess")) keys.add("state");
            Set<String> req = new HashSet<>(keys);
            allowed(op, name, keys, req);
            if (!(op.get("steps") instanceof List)) throw new RecipeException(name + ": \"steps\" debe ser una lista.");
            List<?> steps = (List<?>) op.get("steps");
            if (steps.size() > MAX_STEPS) throw new RecipeException(name + ": demasiados pasos (máx. " + MAX_STEPS + ").");
            for (int i = 0; i < steps.size(); i++) {
                where = name + " paso " + (i + 1);
                step(name, asObj(steps.get(i)));
            }
            where = name;
            if (name.equals("listDevices")) devices(objReq(op, "devices"));
            if (name.equals("readAccess")) state(objReq(op, "state"));
        }

        void step(String opName, Map<String, Object> st) throws RecipeException {
            if (st.size() != 1) throw new RecipeException(where + ": cada paso debe tener un único tipo.");
            String type = st.keySet().iterator().next();
            if (!STEP_TYPES.contains(type)) throw new RecipeException(where + ": tipo de paso no permitido \"" + type + "\".");
            Object body = st.get(type);
            switch (type) {
                case "call": {
                    String target = String.valueOf(body);
                    boolean ok = opName.equals("setBlocked") && (target.equals("readAccess") || target.equals("listDevices"));
                    if (!ok) throw new RecipeException(where + ": \"call\" solo puede usarse en setBlocked hacia readAccess o listDevices.");
                    return;
                }
                case "request": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("method", "path", "form", "headers", "save", "expectStatus"), set("method", "path"));
                    if (!set("GET", "POST").contains(m.get("method"))) throw new RecipeException(where + ": método no permitido.");
                    String path = string(m, "path");
                    if (!path.startsWith("/") || path.startsWith("//") || path.contains("://") || path.contains("\\")
                            || path.contains("@")) {
                        throw new RecipeException(where + ": la ruta debe ser relativa al router (empezar por \"/\").");
                    }
                    template(path, false);
                    if (m.containsKey("form")) {
                        if (!(m.get("form") instanceof List)) throw new RecipeException(where + ": \"form\" debe ser una lista de pares.");
                        for (Object pair : (List<?>) m.get("form")) {
                            if (!(pair instanceof List) || ((List<?>) pair).size() != 2
                                    || !(((List<?>) pair).get(0) instanceof String) || !(((List<?>) pair).get(1) instanceof String)) {
                                throw new RecipeException(where + ": cada campo de \"form\" debe ser [\"nombre\", \"valor\"].");
                            }
                            template((String) ((List<?>) pair).get(1), true);
                        }
                    }
                    if (m.containsKey("headers")) headers(objReq(m, "headers"), where);
                    if (m.containsKey("save")) var(m.get("save"));
                    if (m.containsKey("expectStatus") && !(m.get("expectStatus") instanceof Double)) {
                        throw new RecipeException(where + ": \"expectStatus\" debe ser un número.");
                    }
                    return;
                }
                case "extract": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("from", "regex", "group", "occurrence", "save", "transform", "required", "default", "error"),
                            set("from", "regex", "save"));
                    var(m.get("from"));
                    regex(m.get("regex"));
                    var(m.get("save"));
                    group(m);
                    if (m.containsKey("occurrence") && !set("first", "last").contains(m.get("occurrence"))) {
                        throw new RecipeException(where + ": \"occurrence\" debe ser first o last.");
                    }
                    transforms(m.get("transform"));
                    if (m.containsKey("required") && !(m.get("required") instanceof Boolean)) {
                        throw new RecipeException(where + ": \"required\" debe ser true o false.");
                    }
                    if (m.containsKey("default")) template(string(m, "default"), false);
                    if (m.containsKey("error")) template(string(m, "error"), false);
                    return;
                }
                case "fail":
                case "require": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("from", "regex", "error"), set("from", "regex", "error"));
                    var(m.get("from"));
                    regex(m.get("regex"));
                    template(string(m, "error"), false);
                    return;
                }
                case "set": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("save", "value"), set("save", "value"));
                    var(m.get("save"));
                    template(string(m, "value"), false);
                    return;
                }
                case "compute": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("fn", "args", "save"), set("fn", "args", "save"));
                    String fn = string(m, "fn");
                    if (!FUNCTIONS.contains(fn)) throw new RecipeException(where + ": función no permitida \"" + fn + "\".");
                    if (!(m.get("args") instanceof List)) throw new RecipeException(where + ": \"args\" debe ser una lista.");
                    List<?> args = (List<?>) m.get("args");
                    int want = fn.equals("rsaPkcs1Base64") || fn.equals("repeat") || fn.equals("xorChars") ? 2 : 1;
                    if (args.size() != want) throw new RecipeException(where + ": " + fn + " necesita " + want + " argumento(s).");
                    for (int i = 0; i < args.size(); i++) {
                        if (!(args.get(i) instanceof String)) throw new RecipeException(where + ": los argumentos deben ser texto.");
                        if (fn.equals("rsaPkcs1Base64") && i == 0) {
                            usedKeys.add((String) args.get(0));
                        } else {
                            template((String) args.get(i), true);
                        }
                    }
                    var(m.get("save"));
                    return;
                }
                case "choose": {
                    Map<String, Object> m = asObj(body);
                    allowed(m, where, set("if", "equals", "then", "else", "save"), set("if", "then", "else", "save"));
                    String cond = string(m, "if");
                    if (!VAR_NAME.matcher(cond).matches() && !BUILTIN.matcher(cond).matches()) {
                        throw new RecipeException(where + ": variable no válida en \"if\".");
                    }
                    if (cond.equals("cred.password")) throw new RecipeException(where + ": no se puede usar la contraseña aquí.");
                    if (m.containsKey("equals")) string(m, "equals");
                    template(string(m, "then"), false);
                    template(string(m, "else"), false);
                    var(m.get("save"));
                    return;
                }
                default:
                    throw new RecipeException(where + ": tipo de paso no permitido.");
            }
        }

        void devices(Map<String, Object> d) throws RecipeException {
            where = "listDevices.devices";
            allowed(d, where, set("from", "within", "each", "fields"), set("from", "each", "fields"));
            var(d.get("from"));
            if (d.containsKey("within")) {
                Map<String, Object> w = objReq(d, "within");
                allowed(w, where + ".within", set("regex", "error"), set("regex", "error"));
                regex(w.get("regex"));
                template(string(w, "error"), false);
            }
            regex(d.get("each"));
            Map<String, Object> fields = objReq(d, "fields");
            if (!fields.containsKey("mac")) throw new RecipeException(where + ": falta el campo obligatorio \"mac\".");
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                String k = e.getKey();
                if (!set("mac", "hostName", "ip", "active").contains(k) && !k.matches("extra\\.[A-Za-z0-9_.]{1,40}")) {
                    throw new RecipeException(where + ": campo no admitido \"" + k + "\".");
                }
                Map<String, Object> f = asObj(e.getValue());
                allowed(f, where + "." + k, set("regex", "group", "transform", "default", "trueIf"), set("regex"));
                regex(f.get("regex"));
                group(f);
                transforms(f.get("transform"));
                if (f.containsKey("default")) string(f, "default");
                if (f.containsKey("trueIf")) string(f, "trueIf");
            }
        }

        void state(Map<String, Object> s) throws RecipeException {
            where = "readAccess.state";
            allowed(s, where, set("from", "regex", "group", "map", "default"), set("from", "regex", "map"));
            var(s.get("from"));
            regex(s.get("regex"));
            group(s);
            for (Map.Entry<String, Object> e : objReq(s, "map").entrySet()) {
                if (!STATES.contains(e.getValue())) throw new RecipeException(where + ": estado no válido \"" + e.getValue() + "\".");
            }
            if (s.containsKey("default") && !STATES.contains(s.get("default"))) {
                throw new RecipeException(where + ": estado por defecto no válido.");
            }
        }

        void headers(Map<String, Object> h, String w) throws RecipeException {
            for (Map.Entry<String, Object> e : h.entrySet()) {
                matches(e.getKey(), "[A-Za-z][A-Za-z0-9\\-]{0,40}", w + ": nombre de cabecera");
                String lk = e.getKey().toLowerCase();
                if (lk.equals("host") || lk.equals("cookie") || lk.equals("content-length") || lk.startsWith("proxy-")) {
                    throw new RecipeException(w + ": la cabecera \"" + e.getKey() + "\" la gestiona la app.");
                }
                if (!(e.getValue() instanceof String)) throw new RecipeException(w + ": el valor de cabecera debe ser texto.");
                template((String) e.getValue(), false);
            }
        }

        void group(Map<String, Object> m) throws RecipeException {
            if (!m.containsKey("group")) return;
            Object g = m.get("group");
            if (!(g instanceof Double) || (Double) g < 0 || (Double) g > 9) throw new RecipeException(where + ": \"group\" no válido.");
        }

        void transforms(Object t) throws RecipeException {
            if (t == null) return;
            if (!(t instanceof List)) throw new RecipeException(where + ": \"transform\" debe ser una lista.");
            for (Object x : (List<?>) t) {
                if (!TRANSFORMS.contains(x)) throw new RecipeException(where + ": transformación no permitida \"" + x + "\".");
            }
        }

        void regex(Object r) throws RecipeException {
            if (!(r instanceof String)) throw new RecipeException(where + ": la expresión regular debe ser texto.");
            String s = (String) r;
            if (s.length() > MAX_REGEX) throw new RecipeException(where + ": expresión regular demasiado larga.");
            try {
                Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                throw new RecipeException(where + ": expresión regular no válida: " + e.getDescription());
            }
        }

        void var(Object v) throws RecipeException {
            if (!(v instanceof String) || !VAR_NAME.matcher((String) v).matches()) {
                throw new RecipeException(where + ": nombre de variable no válido \"" + v + "\".");
            }
        }

        /** Comprueba ${...}; la contraseña solo puede ir a campos de formulario y a funciones. */
        void template(String t, boolean passwordAllowed) throws RecipeException {
            Matcher m = TEMPLATE.matcher(t);
            while (m.find()) {
                String[] parts = m.group(1).split("\\|", -1);
                String name = parts[0].trim();
                if (!BUILTIN.matcher(name).matches() && !VAR_NAME.matcher(name).matches()) {
                    throw new RecipeException(where + ": variable no válida \"${" + m.group(1) + "}\".");
                }
                if (name.equals("cred.password") && !passwordAllowed) {
                    throw new RecipeException(where + ": la contraseña solo puede usarse en campos de formulario o en funciones.");
                }
                for (int i = 1; i < parts.length; i++) {
                    String f = parts[i];
                    String fname = f.contains(":") ? f.substring(0, f.indexOf(':')) : f;
                    if (!FILTERS.contains(fname)) throw new RecipeException(where + ": filtro no permitido \"" + f + "\".");
                    if (fname.equals("rsa")) {
                        if (!f.startsWith("rsa:") || f.length() < 5) throw new RecipeException(where + ": usa rsa:nombreDeClave.");
                        usedKeys.add(f.substring(4));
                    }
                }
            }
        }

        void allowed(Map<String, Object> m, String w, Set<String> allowed, Set<String> required) throws RecipeException {
            for (String k : m.keySet()) {
                if (!allowed.contains(k)) throw new RecipeException(w + ": campo no permitido \"" + k + "\".");
            }
            for (String k : required) {
                if (!m.containsKey(k)) throw new RecipeException(w + ": falta el campo \"" + k + "\".");
            }
        }

        void matches(Object v, String regex, String what) throws RecipeException {
            if (!(v instanceof String) || !((String) v).matches(regex)) throw new RecipeException("\"" + what + "\" no válido.");
        }

        String string(Map<String, Object> m, String k) throws RecipeException {
            if (!(m.get(k) instanceof String)) throw new RecipeException(where + ": \"" + k + "\" debe ser texto.");
            return (String) m.get(k);
        }

        void optString(Map<String, Object> m, String k) throws RecipeException {
            if (m.containsKey(k) && !(m.get(k) instanceof String)) throw new RecipeException("\"" + k + "\" debe ser texto.");
        }

        void optStringList(Map<String, Object> m, String k) throws RecipeException {
            if (!m.containsKey(k)) return;
            if (!(m.get(k) instanceof List)) throw new RecipeException("\"" + k + "\" debe ser una lista.");
            for (Object o : (List<?>) m.get(k)) if (!(o instanceof String)) throw new RecipeException("\"" + k + "\" debe contener texto.");
        }

        Map<String, Object> objReq(Map<String, Object> m, String k) throws RecipeException {
            if (!(m.get(k) instanceof Map)) throw new RecipeException("\"" + k + "\" debe ser un objeto.");
            return asObj(m.get(k));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> asObj(Object o) throws RecipeException {
            if (!(o instanceof Map)) throw new RecipeException(where + ": se esperaba un objeto.");
            return (Map<String, Object>) o;
        }
    }

    // ------------------------------------------------------------------ utilidades

    static Set<String> set(String... s) { return new HashSet<>(Arrays.asList(s)); }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof Map ? (Map<String, Object>) o : Collections.<String, Object>emptyMap();
    }

    static String str(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof String ? (String) o : "";
    }

    private static PublicKey parseKey(String name, String pem) throws RecipeException {
        try {
            String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            throw new RecipeException("La clave \"" + name + "\" no es una clave pública RSA válida.");
        }
    }

    static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
