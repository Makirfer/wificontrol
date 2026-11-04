import com.leira.wificasa.core.recipe.*;
import java.nio.file.*;

/**
 * Comprueba que el validador de recetas rechaza lo que no debe permitirse.
 * Uso: java -cp <clases> PruebaRecetas recetas/zte-zxhn-f6640p.json
 */
public class PruebaRecetas {
    static int fallos = 0;
    static String base;

    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) fallos++; }

    static void rechaza(String que, String buscar, String reemplazo) {
        if (!base.contains(buscar)) { ok(false, que + " (no se encontró el texto a modificar)"); return; }
        try {
            Recipe.parse(base.replaceFirst(java.util.regex.Pattern.quote(buscar), java.util.regex.Matcher.quoteReplacement(reemplazo)));
            ok(false, "debería rechazar: " + que);
        } catch (Exception e) {
            ok(true, "rechaza " + que + " → " + e.getMessage());
        }
    }

    public static void main(String[] a) throws Exception {
        base = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        Recipe r = Recipe.parse(base);
        ok(r.id().equals("zte-zxhn-f6640p"), "receta ZTE válida: " + r.displayName());
        ok(Recipe.parse(base + " ").fingerprint().equals(r.fingerprint()) == false, "la huella cambia si cambia el archivo");

        rechaza("URL absoluta a otro servidor", "\"path\": \"/\"", "\"path\": \"http://evil.example/\"");
        rechaza("ruta que empieza por //", "\"path\": \"/\"", "\"path\": \"//evil.example/\"");
        rechaza("ruta con @", "\"path\": \"/\"", "\"path\": \"/@evil.example\"");
        rechaza("contraseña en la ruta", "\"path\": \"/\"", "\"path\": \"/?p=${cred.password}\"");
        rechaza("contraseña en una cabecera", "\"Referer\": \"${base}/\"", "\"Referer\": \"${cred.password}\"");
        rechaza("tipo de paso desconocido (exec)", "{\n          \"request\": {\n            \"method\": \"GET\",\n            \"path\": \"/\"\n          }\n        }",
                "{\"exec\": {\"cmd\": \"rm -rf /\"}}");
        rechaza("función no permitida", "\"fn\": \"sha256Hex\"", "\"fn\": \"eval\"");
        rechaza("transformación no permitida", "\"hexEscapeDecode\"", "\"base64Exec\"");
        rechaza("método HTTP no permitido", "\"method\": \"GET\"", "\"method\": \"DELETE\"");
        rechaza("cabecera Cookie manual", "\"X-Requested-With\"", "\"Cookie\"");
        rechaza("campo desconocido", "\"minEngine\": 1", "\"minEngine\": 1, \"script\": \"x\"");
        rechaza("motor más nuevo que la app", "\"minEngine\": 1", "\"minEngine\": 99");
        rechaza("formato distinto", "wificasa-recipe/1", "wificasa-recipe/9");
        rechaza("capacidad sin operación", "\"setBlocked\": {", "\"setBlockedX\": {");
        rechaza("capacidad desconocida", "\"UNBLOCK\"", "\"ROOT\"");
        rechaza("expresión regular rota", "\"each\": \"<Instance>(.*?)</Instance>\"", "\"each\": \"<Instance>(.*?</Instance>\"");
        rechaza("variable mal formada", "${counter}", "${counter;rm}");
        rechaza("filtro desconocido", "${device.hostName|url}", "${device.hostName|shell}");
        rechaza("clave RSA no definida", "rsa:router", "rsa:otra");
        rechaza("call fuera de setBlocked", "\"steps\": [\n        {\n          \"request\": {\n            \"method\": \"POST\",\n            \"path\": \"/?_type=loginData&_tag=logout_entry\"",
                "\"steps\": [ {\"call\": \"readAccess\"},\n        {\n          \"request\": {\n            \"method\": \"POST\",\n            \"path\": \"/?_type=loginData&_tag=logout_entry\"");
        rechaza("JSON con clave repetida", "\"id\": \"zte-zxhn-f6640p\"", "\"id\": \"zte-zxhn-f6640p\", \"id\": \"otro\"");

        System.out.println(fallos == 0 ? "TODO CORRECTO" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
