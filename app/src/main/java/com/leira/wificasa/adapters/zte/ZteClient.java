package com.leira.wificasa.adapters.zte;

import com.leira.wificasa.core.RouterException;
import com.leira.wificasa.core.SessionExpiredException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;

/**
 * Cliente para el panel web del router ZTE Livebox 6s (ZXHN F6640P) de Parlem/Orange.
 * Replica lo que hace el panel web al usar "mi red local > dispositivo > control de acceso".
 *
 * Java puro (sin clases de Android) para poder probarlo también en un ordenador.
 * No es seguro para usar desde varios hilos a la vez: usar un único hilo de trabajo.
 *
 * Es el antiguo RouterClient de la versión 1, con el mismo protocolo. Ahora solo lo usa
 * {@link ZteF6640pAdapter}; la interfaz de la app ya no lo conoce.
 */
class ZteClient {

    /** Clave pública RSA que el propio panel usa para firmar los cambios (cabecera "Check"). */
    public static String ROUTER_PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n"
            + "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwlo/vZBnSJ2MyJ0dbNcw\n"
            + "DvzPqBN+O/BPvLX93GIJVSZmquJHD9X6Xn6VYeM9mRKzjEbXPlv73Dj/gjjtNj9j\n"
            + "Tq2QVyW2Sd4ZkY9e3h1ALCCCfkbjnmSqedyrcvXriTeW+J65jhBje6lTJbafmC5q\n"
            + "bGiItjt0OeOkT+Vb4S7hYPSWIjeYYBh+7Y/fg25Rt2a+RgC8dahvJ3ttB1LHXADr\n"
            + "oCm6q7G+lpbRAlpC8jjc0rZdS0c6HcBoYgzW8vxjj2fTuFy3CZZTrpPyTv/C8K6B\n"
            + "hjTnjRe6ocgFVyQ0RIYfx2hxSJcuauR57OzfMzlgFQv3RAXguDZtuVUFLO2sAiwL\n"
            + "ELph3Acfy9Eh58SHcswZvsOSXY0JNb0XeRM9gxpntLRfM6TB7f9hYtYTDw5oKdyN\n"
            + "BY+nnEa/IpBUjndGDrSs3Z4BxRbYcJEwkKQZkvw/5TpQYbkD6sTRVSlZPaXSjeCl\n"
            + "0hsLCttqwJqRZcjbWXrINBYFw8PYE14Xr9BCyPgqocdQh7FgvasVgG6u5mLR1PBZ\n"
            + "o4EFF/LdY0yvMG5rl9egBk1XD/UMayhRtmSQEUzYt3eEWLBbqJB6MbVJ2ygcv5EL\n"
            + "ReDY0SWXw1PIEbHeP51A/MyB6kwSgZwdoQW3JiaPnGHMaE0NqfAYPNiGJLMsmvT/\n"
            + "rNUI/8iSCW+WvSzx9tByUxsCAwEAAQ==\n"
            + "-----END PUBLIC KEY-----";

    /** Modos del control de acceso del router. */
    public static final int MODE_ALLOW = 0;   // Permitir Internet de forma permanente
    public static final int MODE_BLOCK = 1;   // Bloquear Internet de forma permanente
    public static final int MODE_SCHEDULE = 2; // Programar acceso a Internet

    private static final int HOURS_IN_WEEK = 168;

    public static class Device {
        public String hostName = "";
        public String mac = "";
        public String ip = "";
        public boolean active;
        public String devType = "1";
        /** Modo actual de acceso (MODE_*), o -1 si no se ha consultado. */
        public int mode = -1;

        public boolean isBlocked() { return mode == MODE_BLOCK; }
    }

    public static class AccessRule {
        public String instId = "";
        public String deviceName = "";
        public String enable = "0";
        public int mode = MODE_ALLOW;
        public String timePolicy = "";
    }

    private final String baseUrl;
    private String sid = null;
    private String sessionToken = "";
    private int connectTimeoutMs = 6000;
    private int readTimeoutMs = 20000;
    private long counter = System.currentTimeMillis();

    public ZteClient(String host) {
        String h = host.trim();
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        this.baseUrl = h;
    }

    public void setTimeouts(int connectMs, int readMs) {
        this.connectTimeoutMs = connectMs;
        this.readTimeoutMs = readMs;
    }

    // ------------------------------------------------------------------ sesión

    public void login(String user, String password) throws RouterException {
        sid = null;
        // 1. Página inicial: el router entrega la cookie de sesión SID.
        request("GET", "/", null, null);
        // 2. Token de sesión para el formulario de acceso.
        String entry = request("GET", "/?_type=loginData&_tag=login_entry", null, null);
        String lockingTime = jsonValue(entry, "lockingTime");
        if (lockingTime != null && !lockingTime.equals("0") && !lockingTime.isEmpty()) {
            throw new RouterException("El router ha bloqueado el acceso temporalmente por "
                    + "demasiados intentos fallidos. Espera " + lockingTime + " segundos.");
        }
        String formToken = jsonValue(entry, "sess_token");
        if (formToken == null) throw new RouterException("Respuesta inesperada del router (login_entry).");
        // 3. Número aleatorio con el que se mezcla la contraseña.
        String tokenXml = request("GET", "/?_type=loginData&_tag=login_token&_=" + nextCounter(), null, null);
        String loginToken = xmlRootText(tokenXml);
        // 4. Envío: Password = sha256(contraseña + número)
        String body = "action=login"
                + "&Password=" + sha256Hex(password + loginToken)
                + "&Username=" + enc(user)
                + "&_sessionTOKEN=" + enc(formToken);
        String resp = request("POST", "/?_type=loginData&_tag=login_entry", body, null);
        if (!resp.contains("\"login_need_refresh\":true")) {
            String err = jsonValue(resp, "loginErrMsg");
            String prompt = jsonValue(resp, "promptMsg");
            String msg = (err != null && !err.isEmpty()) ? err
                    : (prompt != null && !prompt.isEmpty()) ? prompt
                    : "usuario o contraseña incorrectos";
            throw new RouterException("No se pudo iniciar sesión en el router: " + msg);
        }
        // 5. Página principal ya autenticada: trae el token para las operaciones.
        String home = request("GET", "/", null, null);
        captureToken(home);
    }

    public void logout() {
        if (sid == null) return;
        try {
            request("POST", "/?_type=loginData&_tag=logout_entry",
                    "IF_LogOff=1&_sessionTOKEN=" + enc(sessionToken), null);
        } catch (Exception ignored) {
            // No es grave: la sesión caduca sola.
        }
        sid = null;
    }

    // ------------------------------------------------------------- dispositivos

    /** Lista de dispositivos que el router conoce (conectados y recientes). */
    public List<Device> listDevices() throws RouterException {
        String xml = request("GET", "/?_type=menuData&_tag=osp_home_lua.lua&_=" + nextCounter(), null, null);
        ensureOk(xml, "al listar los dispositivos");
        String block = section(xml, "OBJ_ACCESSDEVEXT_ID");
        List<Device> list = new ArrayList<>();
        if (block == null) {
            throw new RouterException("El router no ha devuelto la lista de dispositivos (¿sesión caducada?).");
        }
        for (Map<String, String> inst : instances(block)) {
            Device d = new Device();
            d.hostName = nz(inst.get("HostName"));
            d.mac = nz(inst.get("MACAddress")).toLowerCase();
            d.ip = nz(inst.get("IPAddress"));
            d.active = "1".equals(inst.get("Active"));
            d.devType = inst.get("DevType") == null || inst.get("DevType").isEmpty() ? "1" : inst.get("DevType");
            if (!d.mac.isEmpty()) list.add(d);
        }
        return list;
    }

    /** Lee la regla de acceso del dispositivo (abre la misma página que el panel web). */
    public AccessRule getRule(Device d) throws RouterException {
        // La página de control de acceso entrega un token nuevo, necesario para guardar.
        String page = request("GET", "/?_type=menuView&_tag=vmenu-TimeControl"
                + "&Mac=" + d.mac + "&DevType=" + enc(d.devType)
                + "&DevName=" + enc(d.hostName) + "&_=" + nextCounter(), null, null);
        captureToken(page);
        String xml = request("GET", "/?_type=menuData&_tag=osp_access_restriction_lua.lua"
                + "&Mac=" + d.mac + "&_=" + nextCounter(), null, null);
        ensureOk(xml, "al leer la regla de " + name(d));
        String block = section(xml, "OBJ_FWTIME_ID");
        if (block == null) throw new RouterException("El router no ha devuelto la regla de " + d.hostName + ".");
        List<Map<String, String>> insts = instances(block);
        if (insts.isEmpty()) throw new RouterException("Regla vacía para " + d.hostName + ".");
        Map<String, String> m = insts.get(0);
        AccessRule r = new AccessRule();
        r.instId = nz(m.get("_InstID"));
        r.deviceName = nz(m.get("DeviceName"));
        r.enable = m.get("Enable") == null || m.get("Enable").isEmpty() ? "0" : m.get("Enable");
        try {
            r.mode = Integer.parseInt(nz(m.get("Mode")).trim());
        } catch (NumberFormatException e) {
            r.mode = MODE_ALLOW;
        }
        r.timePolicy = nz(m.get("TimePolicy"));
        d.mode = r.mode;
        return r;
    }

    /**
     * Bloquea o permite Internet a un dispositivo. La comprobación posterior (volver a leer
     * la regla) la hace ahora el motor común, con la misma secuencia de peticiones que antes.
     */
    public void setBlocked(Device d, boolean blocked) throws RouterException {
        AccessRule r = getRule(d);
        int mode = blocked ? MODE_BLOCK : MODE_ALLOW;
        String policy = repeat(blocked ? '1' : '0', HOURS_IN_WEEK);
        // Mismo orden y campos que envía el panel web.
        String body = "IF_ACTION=Apply"
                + "&DeviceName=" + enc(r.deviceName)
                + "&Mac=" + enc(d.mac)
                + "&TimePolicy=" + policy
                + "&Enable=" + enc(r.enable)
                + "&_InstID=" + enc(r.instId)
                + "&Mode=" + mode
                + "&bt_cancel=&bt_save="
                + "&_sessionTOKEN=" + enc(sessionToken);
        Map<String, String> headers = new HashMap<>();
        headers.put("Check", integrityCheck(body));
        String resp = request("POST", "/?_type=menuData&_tag=osp_access_restriction_lua.lua&Mac=" + d.mac,
                body, headers);
        ensureOk(resp, "al guardar el cambio en " + name(d));
    }

    // -------------------------------------------------------------- utilidades

    /** Cabecera "Check": RSA(PKCS#1 v1.5, clave del router) de sha256(cuerpo) en hex, en base64. */
    public static String integrityCheck(String body) throws RouterException {
        try {
            String digest = sha256Hex(body);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey());
            byte[] out = cipher.doFinal(digest.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (RouterException e) {
            throw e;
        } catch (Exception e) {
            throw new RouterException("Error al firmar la petición: " + e.getMessage(), e);
        }
    }

    private static PublicKey publicKey() throws Exception {
        String pem = ROUTER_PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    public static String sha256Hex(String s) throws RouterException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RouterException("SHA-256 no disponible", e);
        }
    }

    private void captureToken(String page) throws RouterException {
        // El panel incrusta: _sessionTmpToken = "\x6b\x57...";  (nos quedamos con la última)
        Matcher m = Pattern.compile("_sessionTmpToken\\s*=\\s*\"((?:\\\\x[0-9a-fA-F]{2})+)\"").matcher(page);
        String last = null;
        while (m.find()) last = m.group(1);
        if (last == null) {
            throw new SessionExpiredException("Sesión no válida: el router ha pedido volver a iniciar sesión.");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 4 <= last.length(); i += 4) {
            sb.append((char) Integer.parseInt(last.substring(i + 2, i + 4), 16));
        }
        sessionToken = sb.toString();
    }

    String currentToken() { return sessionToken; }

    private static void ensureOk(String xml, String step) throws RouterException {
        Matcher m = Pattern.compile("<IF_ERRORSTR>(.*?)</IF_ERRORSTR>").matcher(xml);
        if (m.find()) {
            String s = m.group(1);
            if ("SUCC".equals(s)) return;
            String msg = "El router ha respondido con error " + step + ": " + s;
            if (SessionExpiredException.looksLikeSession(s)) throw new SessionExpiredException(msg);
            throw new RouterException(msg);
        }
        if (xml.contains("login_entry") || xml.contains("Frm_Password")) {
            throw new SessionExpiredException("Sesión caducada en el router " + step + ".");
        }
    }

    private static String name(Device d) {
        return d.hostName == null || d.hostName.isEmpty() ? d.mac : d.hostName;
    }

    private static String section(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static List<Map<String, String>> instances(String block) {
        List<Map<String, String>> out = new ArrayList<>();
        Matcher im = Pattern.compile("<Instance>(.*?)</Instance>", Pattern.DOTALL).matcher(block);
        Pattern pair = Pattern.compile("<ParaName>(.*?)</ParaName>\\s*<ParaValue>(.*?)</ParaValue>", Pattern.DOTALL);
        while (im.find()) {
            Map<String, String> map = new HashMap<>();
            Matcher pm = pair.matcher(im.group(1));
            while (pm.find()) map.put(pm.group(1), unescapeXml(pm.group(2)));
            out.add(map);
        }
        return out;
    }

    static String unescapeXml(String s) {
        Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        m.appendTail(sb);
        return sb.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String xmlRootText(String xml) {
        Matcher m = Pattern.compile("<ajax_response_xml_root>(.*?)</ajax_response_xml_root>", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : xml.trim();
    }

    private static String jsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[-0-9a-z.]+)").matcher(json);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(1);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private long nextCounter() { return ++counter; }

    private String request(String method, String path, String body, Map<String, String> headers)
            throws RouterException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(baseUrl + path);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
            c.setUseCaches(false);
            c.setRequestMethod(method);
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            c.setRequestProperty("Referer", baseUrl + "/");
            String cookie = "_TESTCOOKIESUPPORT=1";
            if (sid != null) cookie = "SID=" + sid + "; " + cookie;
            c.setRequestProperty("Cookie", cookie);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            }
            if (body != null) {
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = c.getOutputStream()) { os.write(data); }
            }
            int code = c.getResponseCode();
            Map<String, List<String>> hf = c.getHeaderFields();
            for (Map.Entry<String, List<String>> e : hf.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie")) {
                    for (String v : e.getValue()) {
                        Matcher m = Pattern.compile("(?:^|;\\s*)SID=([^;]*)").matcher(v);
                        if (m.find()) sid = m.group(1);
                    }
                }
            }
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = readAll(is);
            if (code != 200) throw new RouterException("El router ha respondido HTTP " + code + ".");
            return text;
        } catch (RouterException e) {
            throw e;
        } catch (IOException e) {
            throw new RouterException("No se puede conectar con el router (" + baseUrl
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
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }
}
