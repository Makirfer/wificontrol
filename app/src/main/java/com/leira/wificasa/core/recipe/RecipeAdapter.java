package com.leira.wificasa.core.recipe;

import com.leira.wificasa.core.AccessState;
import com.leira.wificasa.core.Capability;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterAdapter;
import com.leira.wificasa.core.RouterCredentials;
import com.leira.wificasa.core.RouterException;
import com.leira.wificasa.core.RouterSession;
import com.leira.wificasa.core.SessionExpiredException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;

/**
 * Adaptador que ejecuta una {@link Recipe}. Solo sabe hacer lo que el vocabulario de recetas
 * permite: peticiones HTTP al router configurado, leer respuestas con expresiones regulares
 * y un puñado de funciones fijas (hash, firma RSA, decodificaciones).
 */
public final class RecipeAdapter implements RouterAdapter {

    static final int MAX_RESPONSE = 2 * 1024 * 1024;
    static final int MAX_DEVICES = 300;
    static final int MAX_CALL_DEPTH = 2;

    private final Recipe recipe;

    public RecipeAdapter(Recipe recipe) { this.recipe = recipe; }

    public Recipe recipe() { return recipe; }

    @Override public String id() { return "receta:" + recipe.id(); }
    @Override public String displayName() {
        String st = recipe.validationStatus();
        boolean proven = st.equals("tested-device") || st.equals("community-verified");
        return recipe.displayName() + " · receta " + recipe.version() + (proven ? "" : " (experimental)");
    }
    @Override public String defaultHost() { return recipe.defaultHost().isEmpty() ? "192.168.1.1" : recipe.defaultHost(); }
    @Override public String defaultUser() { return recipe.defaultUser().isEmpty() ? "admin" : recipe.defaultUser(); }
    @Override public Set<Capability> capabilities() { return recipe.capabilities(); }

    @Override
    public RouterSession open(RouterCredentials c) throws RouterException {
        Runner r = new Runner(recipe, new RouterCredentials(c.host.isEmpty() ? defaultHost() : c.host,
                c.user.isEmpty() ? defaultUser() : c.user, c.password));
        try {
            r.run("login", null, 0);
        } catch (RouterException e) {
            r.close();
            throw e;
        }
        return r;
    }

    // ====================================================================== intérprete

    private static final class Runner implements RouterSession {
        private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([^}]*)\\}");

        private final Recipe recipe;
        private final RouterCredentials cred;
        private final String base;
        private final Map<String, String> vars = new HashMap<>();
        private final String cookieName;
        private String cookieValue;
        private long counter = System.currentTimeMillis();
        private NetDevice device;
        private boolean blocked;
        private int connectTimeoutMs = 6000, readTimeoutMs = 20000;

        Runner(Recipe recipe, RouterCredentials cred) {
            this.recipe = recipe;
            this.cred = cred;
            this.base = cred.baseUrl();
            String c = Recipe.str(recipe.http(), "sessionCookie");
            this.cookieName = c.isEmpty() ? null : c;
        }

        // ------------------------------------------------------------ operaciones

        @Override
        public List<NetDevice> listDevices() throws RouterException {
            run("listDevices", null, 0);
            return parseDevices();
        }

        @Override
        public AccessState readAccess(NetDevice d) throws RouterException {
            run("readAccess", d, 0);
            d.access = parseState();
            return d.access;
        }

        @Override
        public void setBlocked(NetDevice d, boolean b) throws RouterException {
            blocked = b;
            run("setBlocked", d, 0);
        }

        @Override
        public void close() {
            if (recipe.operation("logout") == null) return;
            if (cookieName != null && cookieValue == null) return; // no llegó a haber sesión
            try {
                run("logout", null, 0);
            } catch (Exception ignored) {
                // No es grave: la sesión caduca sola.
            }
            cookieValue = null;
        }

        void run(String opName, NetDevice d, int depth) throws RouterException {
            try {
                runSteps(opName, d, depth);
            } catch (RouterException e) {
                // Errores de sesión del router → el motor reintenta con una sesión nueva.
                if (depth == 0 && !(e instanceof RecipeException) && !(e instanceof SessionExpiredException)
                        && !opName.equals("login") && SessionExpiredException.looksLikeSession(e.getMessage())) {
                    throw new SessionExpiredException(e.getMessage());
                }
                throw e;
            }
        }

        private void runSteps(String opName, NetDevice d, int depth) throws RouterException {
            Map<String, Object> op = recipe.operation(opName);
            if (op == null) throw new RouterException("La receta no define la operación " + opName + ".");
            if (depth > MAX_CALL_DEPTH) throw new RecipeException("Demasiadas llamadas encadenadas en la receta.");
            if (d != null) device = d;
            for (Object o : (List<?>) op.get("steps")) {
                @SuppressWarnings("unchecked") Map<String, Object> st = (Map<String, Object>) o;
                String type = st.keySet().iterator().next();
                Object body = st.get(type);
                switch (type) {
                    case "request": request(asMap(body)); break;
                    case "extract": extract(asMap(body)); break;
                    case "fail": check(asMap(body), true); break;
                    case "require": check(asMap(body), false); break;
                    case "set": { Map<String, Object> m = asMap(body); vars.put(s(m, "save"), fill(s(m, "value"))); break; }
                    case "compute": compute(asMap(body)); break;
                    case "choose": choose(asMap(body)); break;
                    case "call": {
                        String target = String.valueOf(body);
                        if (target.equals("readAccess")) device.access = runReadAccess(depth + 1);
                        else run(target, null, depth + 1);
                        break;
                    }
                    default: throw new RecipeException("Paso no permitido: " + type);
                }
            }
        }

        private AccessState runReadAccess(int depth) throws RouterException {
            run("readAccess", device, depth);
            return parseState();
        }

        // ------------------------------------------------------------ pasos

        private void request(Map<String, Object> m) throws RouterException {
            String method = s(m, "method");
            String path = fill(s(m, "path"));
            String body = null;
            if (m.get("form") instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object p : (List<?>) m.get("form")) {
                    List<?> pair = (List<?>) p;
                    if (sb.length() > 0) sb.append('&');
                    sb.append(pair.get(0)).append('=').append(enc(fill((String) pair.get(1))));
                }
                body = sb.toString();
            }
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Recipe.obj(recipe.http(), "headers").entrySet()) {
                headers.put(e.getKey(), fill((String) e.getValue()));
            }
            vars.put("body", body == null ? "" : body); // permite firmar el cuerpo en las cabeceras del paso
            for (Map.Entry<String, Object> e : Recipe.obj(m, "headers").entrySet()) {
                headers.put(e.getKey(), fill((String) e.getValue()));
            }
            int expect = m.get("expectStatus") instanceof Double ? ((Double) m.get("expectStatus")).intValue() : 200;
            String resp = http(method, path, body, headers, expect);
            if (m.containsKey("save")) vars.put(s(m, "save"), resp);
        }

        private void extract(Map<String, Object> m) throws RouterException {
            String src = var(s(m, "from"));
            Matcher mt = Pattern.compile(s(m, "regex"), Pattern.DOTALL).matcher(src);
            int g = m.get("group") instanceof Double ? ((Double) m.get("group")).intValue() : 1;
            boolean last = "last".equals(m.get("occurrence"));
            String found = null;
            while (mt.find()) {
                found = g <= mt.groupCount() ? nz(mt.group(g)) : mt.group();
                if (!last) break;
            }
            if (found == null) {
                boolean required = !(Boolean.FALSE.equals(m.get("required")));
                if (m.containsKey("default")) {
                    found = fill(s(m, "default"));
                } else if (required) {
                    throw new RouterException(m.containsKey("error") ? fill(s(m, "error"))
                            : "Respuesta inesperada del router (" + s(m, "save") + ").");
                } else {
                    found = "";
                }
            } else {
                found = transform(found, m.get("transform"));
            }
            vars.put(s(m, "save"), found);
        }

        private void check(Map<String, Object> m, boolean failIfMatch) throws RouterException {
            Matcher mt = Pattern.compile(s(m, "regex"), Pattern.DOTALL).matcher(var(s(m, "from")));
            boolean found = mt.find();
            if (found) vars.put("match", mt.groupCount() >= 1 && mt.group(1) != null ? mt.group(1) : mt.group());
            if (found == failIfMatch) throw new RouterException(fill(s(m, "error")));
        }

        private void compute(Map<String, Object> m) throws RouterException {
            String fn = s(m, "fn");
            List<?> args = (List<?>) m.get("args");
            String out;
            switch (fn) {
                case "sha256Hex": out = sha256Hex(fill((String) args.get(0))); break;
                case "sha1Hex": out = digestHex("SHA-1", fill((String) args.get(0))); break;
                case "md5Hex": out = digestHex("MD5", fill((String) args.get(0))); break;
                case "base64":
                    out = java.util.Base64.getEncoder().encodeToString(
                            fill((String) args.get(0)).getBytes(StandardCharsets.UTF_8));
                    break;
                case "rsaPkcs1Base64": out = rsa((String) args.get(0), fill((String) args.get(1))); break;
                case "hexEscapeDecode": out = hexEscapeDecode(fill((String) args.get(0))); break;
                case "xmlUnescape": out = xmlUnescape(fill((String) args.get(0))); break;
                case "lower": out = fill((String) args.get(0)).toLowerCase(); break;
                case "upper": out = fill((String) args.get(0)).toUpperCase(); break;
                case "repeat": {
                    String text = fill((String) args.get(0));
                    int n;
                    try {
                        n = Integer.parseInt(fill((String) args.get(1)).trim());
                    } catch (NumberFormatException e) {
                        throw new RecipeException("repeat: número no válido.");
                    }
                    if (n < 0 || n * (long) text.length() > 10000) throw new RecipeException("repeat: resultado demasiado grande.");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < n; i++) sb.append(text);
                    out = sb.toString();
                    break;
                }
                default: throw new RecipeException("Función no permitida: " + fn);
            }
            vars.put(s(m, "save"), out);
        }

        private void choose(Map<String, Object> m) throws RouterException {
            String cond = s(m, "if");
            String want = m.containsKey("equals") ? s(m, "equals") : "true";
            String val = cond.equals("blocked") ? String.valueOf(blocked) : value(cond);
            vars.put(s(m, "save"), fill(want.equals(val) ? s(m, "then") : s(m, "else")));
        }

        // ------------------------------------------------------------ resultados

        private List<NetDevice> parseDevices() throws RouterException {
            Map<String, Object> spec = Recipe.obj(recipe.operation("listDevices"), "devices");
            String src = var(Recipe.str(spec, "from"));
            if (spec.containsKey("within")) {
                Map<String, Object> w = Recipe.obj(spec, "within");
                Matcher wm = Pattern.compile(Recipe.str(w, "regex"), Pattern.DOTALL).matcher(src);
                if (!wm.find()) throw new RouterException(fill(Recipe.str(w, "error")));
                src = wm.groupCount() >= 1 ? nz(wm.group(1)) : wm.group();
            }
            List<NetDevice> out = new ArrayList<>();
            Matcher each = Pattern.compile(Recipe.str(spec, "each"), Pattern.DOTALL).matcher(src);
            Map<String, Object> fields = Recipe.obj(spec, "fields");
            while (each.find() && out.size() < MAX_DEVICES) {
                String item = each.groupCount() >= 1 ? nz(each.group(1)) : each.group();
                NetDevice d = new NetDevice();
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    @SuppressWarnings("unchecked") Map<String, Object> f = (Map<String, Object>) e.getValue();
                    String v = field(item, f);
                    switch (e.getKey()) {
                        case "mac": d.mac = v.toLowerCase(); break;
                        case "hostName": d.hostName = v; break;
                        case "ip": d.ip = v; break;
                        case "active": d.active = v.equals(f.containsKey("trueIf") ? Recipe.str(f, "trueIf") : "1"); break;
                        default: d.extra.put(e.getKey().substring("extra.".length()), v);
                    }
                }
                if (!d.mac.isEmpty()) out.add(d);
            }
            return out;
        }

        private String field(String item, Map<String, Object> f) {
            Matcher m = Pattern.compile(Recipe.str(f, "regex"), Pattern.DOTALL).matcher(item);
            int g = f.get("group") instanceof Double ? ((Double) f.get("group")).intValue() : 1;
            String v = m.find() ? (g <= m.groupCount() ? nz(m.group(g)) : m.group()) : "";
            v = transform(v, f.get("transform"));
            if (v.isEmpty() && f.containsKey("default")) v = Recipe.str(f, "default");
            return v;
        }

        private AccessState parseState() throws RouterException {
            Map<String, Object> spec = Recipe.obj(recipe.operation("readAccess"), "state");
            Matcher m = Pattern.compile(Recipe.str(spec, "regex"), Pattern.DOTALL).matcher(var(Recipe.str(spec, "from")));
            int g = spec.get("group") instanceof Double ? ((Double) spec.get("group")).intValue() : 1;
            String raw = m.find() ? (g <= m.groupCount() ? nz(m.group(g)).trim() : m.group().trim()) : null;
            Object mapped = raw == null ? null : Recipe.obj(spec, "map").get(raw);
            String st = mapped != null ? (String) mapped : Recipe.str(spec, "default");
            return st.isEmpty() ? AccessState.UNKNOWN : AccessState.valueOf(st);
        }

        // ------------------------------------------------------------ plantillas y variables

        private String fill(String t) throws RouterException {
            Matcher m = TEMPLATE.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String[] parts = m.group(1).split("\\|", -1);
                String v;
                String orDefault = null;
                for (int i = 1; i < parts.length; i++) if (parts[i].startsWith("or")) orDefault = parts[i].length() > 2 ? parts[i].substring(3) : "";
                try {
                    v = value(parts[0].trim());
                } catch (RouterException e) {
                    if (orDefault == null) throw e;
                    v = "";
                }
                if (v.isEmpty() && orDefault != null) v = orDefault;
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].equals("url")) v = enc(v);
                    else if (parts[i].equals("lower")) v = v.toLowerCase();
                    else if (parts[i].equals("upper")) v = v.toUpperCase();
                    else if (parts[i].equals("sha256Hex")) v = sha256Hex(v);
                    else if (parts[i].startsWith("rsa:")) v = rsa(parts[i].substring(4), v);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(v));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private String value(String name) throws RouterException {
            switch (name) {
                case "base": return base;
                case "counter": return String.valueOf(++counter);
                case "blocked": return String.valueOf(blocked);
                case "cred.user": return cred.user;
                case "cred.password": return cred.password;
                case "device.mac": return dev().mac;
                case "device.hostName": return dev().hostName;
                case "device.ip": return dev().ip;
                default:
                    if (name.startsWith("device.extra.")) return dev().extra(name.substring(13), "");
                    return var(name);
            }
        }

        private NetDevice dev() throws RecipeException {
            if (device == null) throw new RecipeException("Esta operación de la receta no tiene dispositivo.");
            return device;
        }

        private String var(String name) throws RouterException {
            String v = vars.get(name);
            if (v == null) throw new RecipeException("La receta usa la variable \"" + name + "\" antes de definirla.");
            return v;
        }

        // ------------------------------------------------------------ HTTP

        private String http(String method, String path, String body, Map<String, String> headers, int expect)
                throws RouterException {
            HttpURLConnection c = null;
            try {
                URL url = new URL(base + path);
                c = (HttpURLConnection) url.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(connectTimeoutMs);
                c.setReadTimeout(readTimeoutMs);
                c.setUseCaches(false);
                c.setRequestMethod(method);
                for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
                String fixed = Recipe.str(recipe.http(), "fixedCookies");
                String cookie = fixed;
                if (cookieName != null && cookieValue != null) {
                    cookie = cookieName + "=" + cookieValue + (fixed.isEmpty() ? "" : "; " + fixed);
                }
                if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
                if (body != null) {
                    byte[] data = body.getBytes(StandardCharsets.UTF_8);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    c.setFixedLengthStreamingMode(data.length);
                    try (OutputStream os = c.getOutputStream()) { os.write(data); }
                }
                int code = c.getResponseCode();
                if (cookieName != null) {
                    Pattern p = Pattern.compile("(?:^|;\\s*)" + Pattern.quote(cookieName) + "=([^;]*)");
                    for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie")) {
                            for (String v : e.getValue()) {
                                Matcher m = p.matcher(v);
                                if (m.find()) cookieValue = m.group(1);
                            }
                        }
                    }
                }
                InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String text = readAll(is);
                if (code != expect) throw new RouterException("El router ha respondido HTTP " + code + ".");
                return text;
            } catch (RouterException e) {
                throw e;
            } catch (IOException e) {
                throw new RouterException("No se puede conectar con el router (" + base
                        + "). ¿Estás conectado al WiFi de casa?", e);
            } finally {
                if (c != null) c.disconnect();
            }
        }

        private static String readAll(InputStream is) throws IOException {
            if (is == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_RESPONSE) { is.close(); throw new IOException("respuesta demasiado grande"); }
            }
            is.close();
            return bo.toString("UTF-8");
        }

        // ------------------------------------------------------------ funciones permitidas

        private String rsa(String keyName, String data) throws RouterException {
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, recipe.keys.get(keyName));
                return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new RouterException("Error al firmar la petición: " + e.getMessage(), e);
            }
        }

        private static String digestHex(String alg, String s) throws RouterException {
            try {
                byte[] h = MessageDigest.getInstance(alg).digest(s.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b & 0xff));
                return sb.toString();
            } catch (Exception e) {
                throw new RouterException(alg + " no disponible", e);
            }
        }

        private static String sha256Hex(String s) throws RouterException {
            try {
                byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b & 0xff));
                return sb.toString();
            } catch (Exception e) {
                throw new RouterException("SHA-256 no disponible", e);
            }
        }

        static String transform(String v, Object list) {
            if (!(list instanceof List)) return v;
            for (Object t : (List<?>) list) {
                switch (String.valueOf(t)) {
                    case "xmlUnescape": v = xmlUnescape(v); break;
                    case "hexEscapeDecode": v = hexEscapeDecode(v); break;
                    case "lower": v = v.toLowerCase(); break;
                    case "upper": v = v.toUpperCase(); break;
                    case "trim": v = v.trim(); break;
                    default: break;
                }
            }
            return v;
        }

        static String hexEscapeDecode(String s) {
            Matcher m = Pattern.compile("\\\\x([0-9a-fA-F]{2})").matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        static String xmlUnescape(String s) {
            Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
            }
            m.appendTail(sb);
            return sb.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&apos;", "'").replace("&amp;", "&");
        }

        private static String enc(String s) {
            try {
                return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
            } catch (Exception e) {
                return s;
            }
        }

        private static String nz(String s) { return s == null ? "" : s; }

        private static String s(Map<String, Object> m, String k) { return Recipe.str(m, k); }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }
    }
}
