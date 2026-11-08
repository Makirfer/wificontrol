import com.makirfer.wifitimecontrol.core.DeviceType;
import com.makirfer.wifitimecontrol.core.Oui;

import java.io.FileInputStream;

/** Tipo de aparato por el nombre y marca por la MAC. Uso: java PruebaTipos app/src/main/assets/oui.txt */
public class PruebaTipos {
    static int f = 0;
    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) f++; }
    static void tipo(String name, String want) {
        String got = DeviceType.fromName(name);
        ok(got.equals(want), "«" + name + "» → " + got + (got.equals(want) ? "" : " (esperado " + want + ")"));
    }

    public static void main(String[] a) throws Exception {
        tipo("iPhone-de-Ana", DeviceType.PHONE);
        tipo("android-5f3a9c21d4e1b2aa", DeviceType.PHONE);
        tipo("Galaxy-S23", DeviceType.PHONE);
        tipo("Redmi-Note-12", DeviceType.PHONE);
        tipo("HAMMER_6_LTE", DeviceType.PHONE);
        tipo("Galaxy-Tab-S8", DeviceType.TABLET);
        tipo("iPad", DeviceType.TABLET);
        tipo("Galaxy-Watch5", DeviceType.WATCH);
        tipo("PS5-123", DeviceType.CONSOLE);
        tipo("XboxOne", DeviceType.CONSOLE);
        tipo("Nintendo Switch", DeviceType.CONSOLE);
        tipo("LGwebOSTV", DeviceType.TV);
        tipo("Chromecast", DeviceType.TV);
        tipo("Android_TV", DeviceType.TV);
        tipo("Samsung-TV-Salon", DeviceType.TV);
        tipo("DESKTOP-8H2K1LM", DeviceType.COMPUTER);
        tipo("PcNilCasa", DeviceType.COMPUTER);
        tipo("MacBook-Pro-de-Jordi", DeviceType.COMPUTER);
        tipo("raspberrypi", DeviceType.COMPUTER);
        tipo("HP7C2F4A", DeviceType.PRINTER);
        tipo("EPSON1A2B3C", DeviceType.PRINTER);
        tipo("Echo-Dot", DeviceType.SPEAKER);
        tipo("Google-Home-Mini", DeviceType.SPEAKER);
        tipo("ESP_3A1B2C", DeviceType.SMART);
        tipo("shelly1-34945478", DeviceType.SMART);
        tipo("Roomba-i7", DeviceType.SMART);
        tipo("RE450", DeviceType.NETWORK);
        tipo("Tapo_C200", DeviceType.CAMERA);
        tipo("Consola", DeviceType.CONSOLE);
        tipo("Tele del salón", DeviceType.TV);
        tipo("Movil-Ana", DeviceType.PHONE);
        tipo("Portatil-cole", DeviceType.COMPUTER);
        tipo("Altavoz", DeviceType.SPEAKER);
        tipo("Smart-TV", DeviceType.TV);
        tipo("", DeviceType.UNKNOWN);
        tipo("abc123", DeviceType.UNKNOWN);
        tipo("request-server", DeviceType.UNKNOWN);

        ok(DeviceType.privateMac("da:a1:19:00:00:01") && DeviceType.privateMac("12:00:00:00:00:00")
                && !DeviceType.privateMac("3c:2e:f9:00:00:01"), "MAC privada por el segundo dígito (2, 6, A, E)");
        ok(DeviceType.guess("", "", "da:a1:19:00:00:01").equals(DeviceType.PHONE), "sin nombre y MAC privada: probablemente un móvil");
        ok(DeviceType.guess("", "console", "00:1f:32:00:00:01").equals(DeviceType.CONSOLE), "sin nombre: la marca da la pista");
        ok(DeviceType.guess("iPad", "console", "00:1f:32:00:00:01").equals(DeviceType.TABLET), "el nombre manda sobre la marca");

        Oui oui = new Oui(new FileInputStream(a[0]));
        ok(oui.size() > 5000, "lista de marcas cargada (" + oui.size() + " prefijos)");
        ok(oui.brand("3c:2e:f9:12:34:56").equals("Apple"), "3C:2E:F9 es Apple (" + oui.brand("3c:2e:f9:12:34:56") + ")");
        ok(oui.brand("da:a1:19:00:00:01").isEmpty(), "una MAC privada no tiene marca");
        ok(oui.brand("ff:ff:ff:00:00:01").isEmpty() && oui.brand(null).isEmpty(), "MAC desconocida: sin marca");
        String nin = null;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(a[0]))) {
            for (String l; (l = r.readLine()) != null; ) if (l.contains("\tNintendo\t")) { nin = l.substring(0, 6); break; }
        }
        String mac = nin.substring(0, 2) + ":" + nin.substring(2, 4) + ":" + nin.substring(4, 6) + ":00:00:01";
        ok(oui.brand(mac).equals("Nintendo") && oui.hint(mac).equals(DeviceType.CONSOLE), "Nintendo → consola (" + mac + ")");

        System.out.println(f == 0 ? "TODO CORRECTO" : f + " FALLOS");
        System.exit(f == 0 ? 0 : 1);
    }
}
