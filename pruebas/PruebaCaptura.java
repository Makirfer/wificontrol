import com.leira.wificasa.core.learn.Capture;
import com.leira.wificasa.core.recipe.Json;
import java.nio.file.*;
import java.util.*;

/**
 * Comprueba el saneado de una grabación real del grabador (pruebas/panel_falso/eventos_grabados.txt,
 * generada en Chromium con pruebas/grabar_panel_falso.js).
 * Uso: java -cp <clases> PruebaCaptura pruebas/panel_falso/eventos_grabados.txt
 */
public class PruebaCaptura {
    static int f = 0;
    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        Capture c = new Capture("http://127.0.0.1:8790", null);
        int step = 0;
        for (String l : Files.readAllLines(Paths.get(a[0]))) {
            if (l.contains("\"type\":\"fetch\"")) c.setStep(2);
            c.record(l);
        }
        String out = c.sanitizedJson();
        Json.parse(out);
        ok(true, "la grabación saneada es JSON válido (" + out.length() + " caracteres, " + c.count() + " anotaciones)");
        ok(!out.contains("Secreta#2026") && !out.contains("Secreta%232026"), "la contraseña tecleada no aparece (ni codificada)");
        ok(!out.contains("MiClaveWifi123"), "la clave WiFi no aparece (campo oculto, JSON, variable JS, respuesta)");
        ok(!out.contains("\"type\":\"secret\""), "los avisos internos de contraseña no se guardan");
        ok(out.contains("«CONTRASEÑA»"), "la contraseña se marca como «CONTRASEÑA» para la IA");
        ok(out.contains("/api/login") && out.contains("/api/block") && out.contains("devices.json"), "se conservan las peticiones del panel");
        ok(out.contains("aa:bb:cc:dd:ee:ff"), "se conservan las MAC (necesarias para la receta)");
        ok(out.contains("doLogin") , "se conserva el script propio del panel");
        ok(!out.contains("libreria"), "no se graban librerías conocidas (jquery…)");
        ok(out.contains("\"step\":3"), "cada anotación lleva el paso en que se hizo");
        c.setRouter("Askey", "RTF8316VW", "Movistar");
        ok(c.fileName().startsWith("grabacion-askey-rtf8316vw-") && c.fileName().endsWith(".txt"),
                "nombre del archivo con marca y modelo: " + c.fileName());
        ok(Capture.slug("TP-Link Archer C6 (EU) v3.2").equals("tp-link-archer-c6-eu-v3-2") && Capture.slug("Zyxel Ñandú").equals("zyxel-nandu"),
                "identificador limpio a partir de la marca y el modelo");
                String prompt = c.promptForAi("DOCS", "EJEMPLO");
        ok(prompt.contains("wificasa-recipe/1") && prompt.endsWith("\n") && prompt.contains("DOCS"), "instrucciones para la IA preparadas");
        ok(prompt.startsWith("====") && prompt.contains("PARA TI") && prompt.contains("PARA LA IA"), "el archivo explica qué hacer a la persona y a la IA");
        ok(prompt.contains("receta-askey-rtf8316vw.json") && prompt.contains("\"id\": \"askey-rtf8316vw\""), "la IA debe nombrar la receta con el modelo");
        ok(out.contains("\"model\":\"") || c.sanitizedJson().contains("\"model\":\"RTF8316VW\""), "la grabación incluye marca y modelo");
        c.setBlockOutcome("app-only");
        String p2 = c.promptForAi("DOCS", "EJEMPLO");
        ok(p2.contains("NO permite cortar") && p2.contains("SOLO con") && c.sanitizedJson().contains("\"blockOutcome\":\"app-only\""),
                "si el router no permite bloquear, la IA lo sabe y hace una receta de solo consulta");
                System.out.println(f == 0 ? "TODO CORRECTO" : f + " FALLOS");
        System.exit(f);
    }
}
