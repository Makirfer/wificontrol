package com.leira.wificasa.core.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca los datos útiles del texto leído en la pegatina de un router (foto + reconocimiento de
 * texto en el móvil): marca, modelo, compañía, dirección, usuario y contraseña de acceso.
 * La clave del WiFi se ignora a propósito: no es la de entrar en el router.
 * No depende de Android, así que se prueba en el ordenador.
 */
public final class Pegatina {

    public String vendor = "", model = "", isp = "", host = "", user = "", password = "";

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
    private static final Pattern LBL_MODEL = Pattern.compile("(?i)^(model[oe]?|mod\\.|product)\\b.*");
    private static final Pattern LBL_USER = Pattern.compile("(?i).*\\b(usuario|usuari|user(?:name)?|login)\\b.*");
    private static final Pattern LBL_PASS = Pattern.compile("(?i).*\\b(contrase(?:ñ|n)a|contrasenya|clave|password|pass|pwd)\\b.*");
    /** La clave del WiFi no sirve para entrar al router: estas etiquetas se descartan. */
    private static final Pattern WIFI = Pattern.compile("(?i)wi-?fi|wpa|wlan|ssid|wireless|\\bred\\b|network\\s*key|psk|pin");
    private static final Pattern NOT_MODEL = Pattern.compile("(?i)wi-?fi|wpa|ssid|s/n|serial|mac|imei|gpon|pon\\b|pin|key|clave|contrase");
    private static final Pattern IP = Pattern.compile("\\b(?:https?://)?((?:192\\.168|10\\.\\d{1,3}|172\\.(?:1[6-9]|2\\d|3[01]))\\.\\d{1,3}\\.\\d{1,3})\\b");
    private static final Pattern HOSTNAME = Pattern.compile("(?i)\\bhttps?://([a-z][a-z0-9.\\-]{2,40})\\b");
    private static final Pattern MODEL_CODE = Pattern.compile("\\b([A-Z]{1,6}[- ]?[0-9]{3,5}[A-Z0-9\\-]{0,8})\\b");

    public static Pegatina parse(String text) {
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
        for (String[] i : ISPS) {
            if (Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(i[0]) + "(?![a-z])").matcher(low).find()) { p.isp = i[1]; break; }
        }

        for (int k = 0; k < lines.size(); k++) {
            String l = lines.get(k);
            int c = l.indexOf(':');
            String label = (c > 0 ? l.substring(0, c) : l).trim();
            String val = c > 0 ? l.substring(c + 1).trim() : "";
            if (label.length() > 45) continue; // no es una etiqueta
            boolean isModel = LBL_MODEL.matcher(label).matches();
            boolean isPass = !isModel && LBL_PASS.matcher(label).matches();
            boolean isUser = !isModel && !isPass && LBL_USER.matcher(label).matches();
            if (!isModel && !isPass && !isUser) continue;
            if (c < 0 && label.length() > 25) continue; // frase, no etiqueta
            if (val.isEmpty() && k + 1 < lines.size() && !isLabel(lines.get(k + 1))) val = lines.get(k + 1).trim();
            if (val.isEmpty() || val.toLowerCase(Locale.ROOT).startsWith("http")) continue;
            if (isModel) { if (p.model.isEmpty()) p.model = val.length() > 40 ? val.substring(0, 40) : val; continue; }
            if (WIFI.matcher(label).find()) continue; // «Clave WiFi», «WPA key»…: no es la del router
            if (isPass && p.password.isEmpty()) p.password = firstToken(val);
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
                    p.model = mc.group(1).replace(' ', '-');
                    break outer;
                }
            }
        }
        return p;
    }

    private static boolean isLabel(String l) {
        int c = l.indexOf(':');
        String label = (c > 0 ? l.substring(0, c) : l).trim();
        return LBL_MODEL.matcher(label).matches() || LBL_PASS.matcher(label).matches() || LBL_USER.matcher(label).matches()
                || WIFI.matcher(label).find();
    }

    private static String firstToken(String v) {
        String t = v.trim().split("\\s{2,}|\\s+[|·]\\s+")[0].trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    public boolean isEmpty() {
        return vendor.isEmpty() && model.isEmpty() && isp.isEmpty() && host.isEmpty() && user.isEmpty() && password.isEmpty();
    }
}
