package com.makirfer.wifitimecontrol.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tipo de aparato (móvil, consola, tele…) deducido del nombre que da el router y de la marca de
 * su MAC. Siempre es una suposición: en la app se muestra como «probable» y se puede cambiar.
 * No depende de Android, así que se prueba en el ordenador.
 */
public final class DeviceType {

    public static final String PHONE = "phone", TABLET = "tablet", COMPUTER = "computer", CONSOLE = "console",
            TV = "tv", SPEAKER = "speaker", PRINTER = "printer", WATCH = "watch", CAMERA = "camera",
            SMART = "smart", NETWORK = "network", UNKNOWN = "unknown";

    /** Orden en que se ofrecen al elegir a mano. */
    public static final String[] ALL = {PHONE, TABLET, COMPUTER, CONSOLE, TV, SPEAKER, PRINTER, WATCH, CAMERA,
            SMART, NETWORK, UNKNOWN};

    private DeviceType() { }

    public static String icon(String type) {
        switch (type == null ? "" : type) {
            case PHONE: return "📱";
            case TABLET: return "📲";
            case COMPUTER: return "💻";
            case CONSOLE: return "🎮";
            case TV: return "📺";
            case SPEAKER: return "🔊";
            case PRINTER: return "🖨";
            case WATCH: return "⌚";
            case CAMERA: return "📷";
            case SMART: return "💡";
            case NETWORK: return "📡";
            default: return "❔";
        }
    }

    // El orden importa: «galaxy-watch» es un reloj y «galaxy-tab» una tablet antes que un móvil;
    // «android-tv» una tele; las impresoras HP («HP1A2B3C») antes que los ordenadores.
    private static final Object[][] RULES = {
            {WATCH, "reloj|rellotge|watch|wear ?os|galaxy-?watch|amazfit|mi-?band|smartband"},
            {CONSOLE, "consola|playstation|\\bps[345]\\b|ps[345][-_]|xbox|nintendo|\\bswitch\\b|steam-?deck|oculus|\\bquest[-_ ]?[23]?\\b|meta-?quest"},
            {TV, "\\btele\\b|televisi|\\btv\\b|tv$|^tv|webos|bravia|chromecast|fire-?tv|fire-?stick|\\broku|apple-?tv|shield|android-?tv|google-?tv|mi-?box|mitv|tizen|smart-?tv|\\bhisense|\\bvidaa|\\bphilips-?tv|\\bsat[-_ ]?box|decodificador|deco-?movistar|\\buhd\\b|\\bstb\\b"},
            {TABLET, "ipad|\\btab\\b|tablet|galaxy-?tab|sm-[tpx]\\d|mediapad|matepad|kindle|lenovo-?tab|xiaomi-?pad|redmi-?pad|\\bpad\\b"},
            {PRINTER, "printer|impresora|epson|canon|brother|officejet|deskjet|laserjet|^hp[0-9a-f]{6}|^npi[0-9a-f]|mfc-|\\bdcp-"},
            {SPEAKER, "\\becho\\b|echo-?dot|alexa|homepod|sonos|google-?home|nest-?(audio|mini|hub)|home-?mini|\\bbose\\b|speaker|altavoz|soundbar|barra-?de-?sonido"},
            {CAMERA, "\\bcam\\b|camera|camara|cámara|ipcam|ezviz|tapo-?c\\d|\\bring\\b|arlo|blink|\\byi-|hikvision|doorbell|timbre"},
            {SMART, "^esp[-_]|espressif|tasmota|shelly|tuya|smart-?plug|enchufe|\\bplug\\b|bulb|bombilla|\\bhue\\b|roomba|robot|vacuum|aspirador|roborock|ecovacs|wled|sonoff|broadlink|\\bnest\\b|thermostat|termostato|tado|netatmo|meross|\\byeelight|\\blifx|\\bwiz\\b|\\bdyson\\b|airpurifier|purificador|lavadora|washer|nevera|fridge"},
            {NETWORK, "repeater|repetidor|extender|amplificador|\\bmesh\\b|\\bdeco\\b|fritz|powerline|\\bplc\\b|access-?point|\\bap-|^re\\d{3}"},
            {COMPUTER, "^desktop-|^laptop-|^pc|[-_ ]pc$|[-_ ]pc[-_ ]|\\bpc\\b|portatil|portátil|ordenador|macbook|\\bimac|mac-?mini|mac-?studio|\\bmbp\\b|thinkpad|ideapad|chromebook|surface|^win-|windows|ubuntu|debian|fedora|linux|raspberry|\\bnas\\b|synology|qnap|-mac$|mac$"},
            {PHONE, "iphone|android|galaxy|\\bsm-[agsnmef]\\d|pixel|redmi|xiaomi|\\bpoco|oneplus|oppo|realme|\\bvivo|huawei|honor|\\bmoto|motorola|nokia|hammer|\\bmovil|\\bmóvil|\\bphone|telefono|teléfono|\\bnothing-?phone|fairphone|\\bbq-|aquaris"},
    };
    private static final Pattern[] COMPILED = new Pattern[RULES.length];
    static {
        for (int i = 0; i < RULES.length; i++) COMPILED[i] = Pattern.compile((String) RULES[i][1]);
    }

    /** Tipo según el nombre del aparato; {@link #UNKNOWN} si el nombre no da pistas. */
    public static String fromName(String name) {
        if (name == null || name.trim().isEmpty()) return UNKNOWN;
        String n = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (int i = 0; i < RULES.length; i++) if (COMPILED[i].matcher(n).find()) return (String) RULES[i][0];
        return UNKNOWN;
    }

    /**
     * MAC privada (aleatoria): la ponen los móviles y tablets modernos para no ser rastreados.
     * Se reconoce por el bit «administrada localmente» del primer byte (x2, x6, xA, xE).
     */
    public static boolean privateMac(String mac) {
        if (mac == null || mac.length() < 2) return false;
        int v = Character.digit(mac.charAt(1), 16);
        return v >= 0 && (v & 0x2) != 0;
    }

    /**
     * Suposición completa: primero el nombre; si no dice nada, la pista de la marca (Nintendo es
     * consola, Epson impresora…); si la MAC es privada, casi siempre es un móvil o una tablet.
     */
    public static String guess(String name, String brandHint, String mac) {
        String t = fromName(name);
        if (!UNKNOWN.equals(t)) return t;
        if (brandHint != null && !brandHint.isEmpty()) return brandHint;
        return privateMac(mac) ? PHONE : UNKNOWN;
    }
}
