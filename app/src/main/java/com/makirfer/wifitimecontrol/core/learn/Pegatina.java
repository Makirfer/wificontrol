package com.makirfer.wifitimecontrol.core.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca los datos útiles del texto leído en la pegatina de un router (foto + reconocimiento de
 * texto en el móvil): marca, modelo, compañía, dirección, usuario y contraseña de acceso.
 * La clave del WiFi no es la de entrar en el router: se aparta. Si la pegatina tiene un código QR
 * con los datos del WiFi (formato estándar «WIFI:S:…;P:…;;»), se leen letra por letra y además
 * sirven para no confundir esa clave con la contraseña del router.
 * No depende de Android, así que se prueba en el ordenador.
 */
public final class Pegatina {

    public String vendor = "", model = "", isp = "", host = "", user = "", password = "";
    /** Datos del WiFi leídos del código QR (exactos) y el texto de otros códigos QR, si los hay. */
    public String wifiName = "", wifiKey = "", qrOther = "";

    private static final String[] VENDORS = {
            "ZTE", "Askey", "MitraStar", "Mitrastar", "Sagemcom", "Huawei", "TP-Link", "TP-LINK", "Arcadyan",
            "Sercomm", "Technicolor", "Zyxel", "ZyXEL", "Comtrend", "Nokia", "FRITZ!Box", "AVM", "Xiaomi",
            "Netgear", "NETGEAR", "ASUS", "Asus", "Tenda", "Mercusys", "D-Link", "Kaon", "Vantiva"
    };
    private static final String[][] ISPS = {
            {"movistar", "Movistar"}, {"telefónica", "Movistar"}, {"telefonica", "Movistar"}, {"vodafone", "Vodafone"},
            {"orange", "Orange"}, {"livebox", "Orange"}, {"jazztel", "Jazztel"}, {"digi", "Digi"},
            {"masmovil", "MásMóvil"}, {"másmóvil", "MásMóvil"}, {"yoigo", "Yoigo"}, {"pepephone", "Pepephone"},
            {"lowi", "Lowi"}, {"parlem", "Parlem"}, {"avatel", "Avatel"}, {"finetwork", "Finetwork"},
            {"mundo r", "Mundo R"}, {"o2", "O2"}, {"simyo", "Simyo"}
    };

    // Etiquetas habituales (castellano, catalán e inglés): el valor va detrás de ":" o en la línea siguiente.
    private static final Pattern LBL_MODEL = Pattern.compile("(?i)^(?:(?:model[oe]?|product)\\b|mod\\.).*");
    private static final Pattern LBL_USER = Pattern.compile("(?i).*\\b(usuario|usuari|user(?:name)?|login)\\b.*");
    private static final Pattern LBL_PASS = Pattern.compile("(?i).*\\b(contrase(?:ñ|n)a|contrasenya|clave|password|pass|pwd)\\b.*");
    /** La clave del WiFi no sirve para entrar al router: estas etiquetas se descartan. */
    private static final Pattern WIFI = Pattern.compile("(?i)wi-?fi|wpa|wlan|ssid|wireless|\\bred\\b|network\\s*key|psk|\\bpin\\b");
    private static final Pattern NOT_MODEL = Pattern.compile(
            "(?i)wpa|ssid|s/n|serial|\\bmac\\b|imei|gpon|\\bpon\\b|\\bpin\\b|\\bkey\\b|clave|contrase|\\bcif\\b|\\bnif\\b|rii|\\bid\\b|n\\.?r\\.?c|sace|f\\.?fab|\\bce\\b|productor|fabricante|distribu|importador|direcci|address|dist\\.|\\brd\\.");
    private static final Pattern IP = Pattern.compile("\\b(?:https?://)?((?:192\\.168|10\\.\\d{1,3}|172\\.(?:1[6-9]|2\\d|3[01]))\\.\\d{1,3}\\.\\d{1,3})\\b");
    private static final Pattern HOSTNAME = Pattern.compile("(?i)\\bhttps?://([a-z][a-z0-9.\\-]{2,40})\\b");
    private static final Pattern MODEL_CODE = Pattern.compile("\\b([A-Z]{1,6}[- ]?[0-9]{3,5}[A-Z0-9\\-]{0,8})\\b");

    public static Pegatina parse(String text) { return parse(text, null); }

    /** Texto reconocido en la foto más el contenido de los códigos QR que había en ella. */
    public static Pegatina parse(String text, List<String> codes) {
        String ssid = "", key = "";
        List<String> other = new ArrayList<>();
        if (codes != null) {
            for (String c : codes) {
                if (c == null || c.trim().isEmpty()) continue;
                String[] w = wifiQr(c.trim());
                if (w != null && ssid.isEmpty() && key.isEmpty()) { ssid = w[0]; key = w[1]; }
                else if (w == null) other.add(c.trim());
            }
        }
        Pegatina p = parseText(text, key);
        p.wifiName = ssid;
        p.wifiKey = key;
        for (String o : other) {
            // Algunos QR llevan los datos de acceso («user=admin&pass=…», «Usuario: admin; Clave: …»):
            // son exactos, así que mandan sobre lo leído en el texto.
            String u = kv(o, QR_USER), pw = kv(o, QR_PASS), m = kv(o, QR_MODEL);
            if (!u.isEmpty()) p.user = u;
            if (!pw.isEmpty() && !sameKey(pw, key)) p.password = pw;
            if (!m.isEmpty() && p.model.isEmpty()) p.model = m;
            if (p.host.isEmpty()) {
                Matcher ip = IP.matcher(o);
                if (ip.find()) p.host = ip.group(1);
            }
            if (p.isp.isEmpty()) p.isp = ispIn(o.toLowerCase(Locale.ROOT));
            if (p.qrOther.isEmpty()) p.qrOther = o.length() > 200 ? o.substring(0, 200) : o;
        }
        return p;
    }

    private static final Pattern QR_USER = Pattern.compile("(?i)(?:^|[?&;,\\s{\"])(?:user(?:name)?|usuario|usuari|login|usr|admin_?user)\"?\\s*[=:]\\s*\"?([^&;,\\s\"}]+)");
    private static final Pattern QR_PASS = Pattern.compile("(?i)(?:^|[?&;,\\s{\"])(?:pass(?:word)?|pwd|contrase(?:ñ|n)a|contrasenya|admin_?pass(?:word)?)\"?\\s*[=:]\\s*\"?([^&;,\\s\"}]+)");
    private static final Pattern QR_MODEL = Pattern.compile("(?i)(?:^|[?&;,\\s{\"])(?:model[oe]?)\"?\\s*[=:]\\s*\"?([^&;,\"}]+)");

    private static String kv(String text, Pattern p) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * Código QR de WiFi: «WIFI:T:WPA;S:MiRed;P:clave;;» (los campos van en cualquier orden y
     * «\;», «\:», «\,» y «\\» son caracteres escapados). Devuelve {red, clave} o null.
     */
    static String[] wifiQr(String code) {
        if (!code.regionMatches(true, 0, "WIFI:", 0, 5)) return null;
        String ssid = "", pass = "";
        StringBuilder field = new StringBuilder();
        String body = code.substring(5);
        for (int i = 0; i <= body.length(); i++) {
            char ch = i < body.length() ? body.charAt(i) : ';';
            if (ch == '\\' && i + 1 < body.length()) { field.append(body.charAt(++i)); continue; }
            if (ch != ';') { field.append(ch); continue; }
            String f = field.toString();
            field.setLength(0);
            int c = f.indexOf(':');
            if (c <= 0) continue;
            String k = f.substring(0, c).trim().toUpperCase(Locale.ROOT), v = unquote(f.substring(c + 1));
            if (k.equals("S")) ssid = v;
            else if (k.equals("P")) pass = v;
        }
        return ssid.isEmpty() && pass.isEmpty() ? null : new String[]{ssid, pass};
    }

    private static String unquote(String v) {
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    private static String ispIn(String low) {
        for (String[] i : ISPS) {
            if (Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(i[0]) + "(?![a-z])").matcher(low).find()) return i[1];
        }
        return "";
    }

    /** ¿Es la misma clave, aunque la lectura haya confundido 0/O o 1/l/I? */
    static boolean sameKey(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
    }

    private static Pegatina parseText(String text, String wifiKey) {
        Pegatina p = new Pegatina();
        if (text == null) return p;
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\\r?\\n")) if (!l.trim().isEmpty()) lines.add(l.trim());
        String all = String.join("\n", lines), low = all.toLowerCase(Locale.ROOT);

        for (String v : VENDORS) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(v) + "\\b").matcher(all).find()) {
                p.vendor = v.equalsIgnoreCase("tp-link") ? "TP-Link" : v.equals("Mitrastar") ? "MitraStar"
                        : v.equals("NETGEAR") ? "Netgear" : v.equals("ZyXEL") ? "Zyxel" : v.equals("Asus") ? "ASUS" : v;
                break;
            }
        }
        p.isp = ispIn(low);

        String modelText = "";
        for (int k = 0; k < lines.size(); k++) {
            String l = lines.get(k);
            int c = colon(l);
            String label = (c > 0 ? l.substring(0, c) : l).trim();
            String val = c > 0 ? l.substring(c + 1).trim() : "";
            if (label.length() > 60) continue; // no es una etiqueta
            boolean isModel = LBL_MODEL.matcher(label).matches();
            boolean isPass = !isModel && LBL_PASS.matcher(label).matches();
            boolean isUser = !isModel && !isPass && LBL_USER.matcher(label).matches();
            if (!isModel && !isPass && !isUser) continue;
            if (c < 0 && label.length() > 25) continue; // frase, no etiqueta
            if (val.isEmpty() && k + 1 < lines.size() && !isLabel(lines.get(k + 1))) val = lines.get(k + 1).trim();
            if (isPass && sameKey(firstToken(val), wifiKey)) val = ""; // es la clave del WiFi (lo dice el QR)
            if (val.isEmpty() && isPass && !WIFI.matcher(label).find()) {
                // «… con la contraseña:» y la clave en otro recuadro: la primera palabra con letras y números.
                for (int j = k + 1; j < Math.min(lines.size(), k + 4) && val.isEmpty(); j++) {
                    for (String tok : lines.get(j).split("\\s+")) {
                        if (tok.matches("[A-Za-z0-9!#$%&*+.=?@_\\-]{6,24}") && tok.matches(".*[A-Za-z].*") && tok.matches(".*\\d.*")
                                && !tok.toLowerCase(Locale.ROOT).startsWith("http") && !sameKey(tok, wifiKey)) { val = tok; break; }
                    }
                }
            }
            if (!isModel && (val.isEmpty() || val.toLowerCase(Locale.ROOT).startsWith("http"))) continue;
            if (isModel) {
                if (p.model.isEmpty()) {
                    // El código del modelo («RTF8316VW») en la misma línea o en los recuadros de al lado.
                    String sameLine = c > 0 ? l.substring(c + 1).trim() : "";
                    String code = codeIn(sameLine);
                    for (int j = k + 1; code == null && j < Math.min(lines.size(), k + 4); j++) {
                        if (!NOT_MODEL.matcher(lines.get(j)).find()) code = codeIn(lines.get(j));
                    }
                    if (code != null) p.model = code;
                    else if (modelText.isEmpty()) {
                        // Sin código: el texto de la etiqueta («Model: Livebox 7») se usa solo si no hay nada mejor.
                        String t = !sameLine.isEmpty() ? sameLine : val;
                        if (t.replaceAll("[^A-Za-z]", "").length() >= 2 && colon(t) < 0 && !NOT_MODEL.matcher(t).find())
                            modelText = t.length() > 40 ? t.substring(0, 40) : t;
                    }
                }
                continue;
            }
            if (WIFI.matcher(label).find()) continue; // «Clave WiFi», «WPA key»…: no es la del router
            if (isPass && p.password.isEmpty() && !sameKey(firstToken(val), wifiKey)) p.password = firstToken(val);
            if (isUser && p.user.isEmpty()) p.user = firstToken(val);
        }
        Matcher ip = IP.matcher(all);
        if (ip.find()) p.host = ip.group(1);
        else {
            Matcher hn = HOSTNAME.matcher(all);
            if (hn.find()) p.host = hn.group(1);
        }
        if (p.model.isEmpty()) {
            // Sin etiqueta «Modelo»: el primer código con pinta de modelo (RTF8316VW, F6640P, GPT-2841GX4X5…).
            outer:
            for (String l : lines) {
                if (NOT_MODEL.matcher(l).find()) continue;
                Matcher mc = MODEL_CODE.matcher(l);
                while (mc.find()) {
                    if (!goodCode(mc.group(1))) continue;
                    p.model = mc.group(1).replace(' ', '-');
                    break outer;
                }
            }
        }
        if (p.model.isEmpty()) p.model = modelText;
        return p;
    }

    /** Posición del «:» que separa etiqueta y valor (no el de «http://»). */
    private static int colon(String l) {
        for (int i = l.indexOf(':'); i >= 0; i = l.indexOf(':', i + 1)) {
            if (!l.startsWith("//", i + 1)) return i;
        }
        return -1;
    }

    private static String codeIn(String text) {
        Matcher mc = MODEL_CODE.matcher(text);
        while (mc.find()) if (goodCode(mc.group(1))) return mc.group(1).replace(' ', '-');
        return null;
    }

    /** Un código de modelo tiene al menos dos letras (RTF8316VW, F6640P); descarta CIF y números de serie. */
    private static boolean goodCode(String c) {
        return c.replaceAll("[^A-Za-z]", "").length() >= 2;
    }

    private static boolean isLabel(String l) {
        int c = colon(l);
        String label = (c > 0 ? l.substring(0, c) : l).trim();
        if (c < 0 && label.length() > 25) return false; // una frase («Router Smart WiFi 7 Askey…»), no una etiqueta
        return LBL_MODEL.matcher(label).matches() || LBL_PASS.matcher(label).matches() || LBL_USER.matcher(label).matches()
                || (c > 0 && WIFI.matcher(label).find());
    }

    private static String firstToken(String v) {
        String t = v.trim().split("\\s{2,}|\\s+[|·]\\s+")[0].trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    public boolean isEmpty() {
        return vendor.isEmpty() && model.isEmpty() && isp.isEmpty() && host.isEmpty() && user.isEmpty() && password.isEmpty()
                && wifiName.isEmpty() && wifiKey.isEmpty();
    }
}
