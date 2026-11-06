import com.leira.wificasa.core.learn.Pegatina;

/** Lectura de pegatinas: texto como el que devuelve el reconocimiento de texto del móvil. */
public class PruebaPegatina {
    static int f = 0;
    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) f++; }

    public static void main(String[] a) {
        Pegatina p = Pegatina.parse("movistar\nRouter Movistar WiFi 7\nModelo: RTF8316VW\nAskey Computer Corp.\n"
                + "Red WiFi (SSID): MOVISTAR-WIFI7-FBA0\nClave WiFi: Ab12Cd34Ef56Gh\n"
                + "Contraseña de acceso al router: Xy7pQ2mK\nhttp://192.168.1.1\nS/N: ASK123456789");
        ok(p.vendor.equals("Askey") && p.model.equals("RTF8316VW") && p.isp.equals("Movistar"), "Askey de Movistar: marca, modelo y compañía");
        ok(p.password.equals("Xy7pQ2mK"), "contraseña de acceso, no la del WiFi (" + p.password + ")");
        ok(p.host.equals("192.168.1.1"), "dirección del router");

        p = Pegatina.parse("ZTE\nZXHN F6640P\nSSID: Parlem_5G_1234\nWPA Key: zz99yy88xx77\nUsuario\nadmin\nContraseña\nQw3rTy12\nIP: 192.168.1.1");
        ok(p.vendor.equals("ZTE") && p.model.equals("F6640P"), "ZTE sin etiqueta «Modelo»: código detectado (" + p.model + ")");
        ok(p.user.equals("admin") && p.password.equals("Qw3rTy12"), "etiquetas solas y valor en la línea siguiente");

        p = Pegatina.parse("Livebox 7\nOrange\nModel: Livebox 7\nWi-Fi password: 7f7f7f7f7f\nAdmin password: Hj5Kl8Pq\nhttp://livebox");
        ok(p.isp.equals("Orange") && p.model.equals("Livebox 7") && p.password.equals("Hj5Kl8Pq") && p.host.equals("livebox"),
                "Livebox: modelo con espacio, contraseña de admin y dirección por nombre (" + p.model + "/" + p.host + ")");

        p = Pegatina.parse("TP-Link\nArcher C6\nDefault Access: http://tplinkwifi.net\nWireless Password/PIN: 12345678");
        ok(p.vendor.equals("TP-Link") && p.password.isEmpty() && p.host.equals("tplinkwifi.net"), "la clave del WiFi no se toma por la del router");

        // Pegatina real del Askey RTF8316VW (06/11), con contraseña inventada; el lector la devolvió así.
        p = Pegatina.parse("Productor: TD SYNNEX SPAIN, S.L.U.\nCIF: B58728585 Nº RII-AEE: 744\nFabricante: Askey Computer Corp.\n"
                + "movistar\nASKEY\nMod.\nRouter Smart WiFi 7 Askey RTF8316VW\n00413012\nN.R.C\n0128\nID: 8833745AFBA0\n"
                + "Datos de acceso al equipo\nCONFIGURA tu Router en\nhttp://192.168.1.1 con la contraseña:\nQa7Zr2Lm");
        ok(p.model.equals("RTF8316VW"), "Askey: el modelo, no el CIF (" + p.model + ")");
        ok(p.password.equals("Qa7Zr2Lm") && p.host.equals("192.168.1.1"), "«con la contraseña:» y la clave en otra línea (" + p.password + ")");
        p = Pegatina.parse("CIF: B58728585\nMod.: Router Smart WiFi 7 Askey RTF8316VW\nhttp://192.168.1.1 con la contraseña: Qa7Zr2Lm");
        ok(p.model.equals("RTF8316VW") && p.password.equals("Qa7Zr2Lm"), "lo mismo en una sola línea (" + p.model + "/" + p.password + ")");

        p = Pegatina.parse("CIF: B58728585\nNº RII-AEE: 744\nRouter Smart WiFi 7 Askey RTF8316VW\nMod.\n00413012\nRed: MOVISTAR-WIFI7-FBA0");
        ok(p.model.equals("RTF8316VW"), "con los recuadros desordenados, el código del modelo igualmente (" + p.model + ")");

        ok(Pegatina.parse("").isEmpty() && Pegatina.parse(null).isEmpty(), "sin texto, sin datos");
        System.out.println(f == 0 ? "TODO CORRECTO" : f + " FALLOS");
        System.exit(f == 0 ? 0 : 1);
    }
}
