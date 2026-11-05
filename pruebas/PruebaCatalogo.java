import com.leira.wificasa.core.*;
import com.leira.wificasa.core.catalog.Catalog;
import com.leira.wificasa.core.recipe.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/**
 * Catálogo y pruebas de recetas de punta a punta, en local:
 *  - sirve la carpeta catalogo/ como si fuera GitHub y la consulta con el cliente de la app;
 *  - descarga e instala la receta del ZTE comprobando su huella;
 *  - la prueba con RecipeTester (lectura y corte controlado) contra el simulador del ZTE.
 * Uso: java -cp <clases> PruebaCatalogo <carpeta catalogo> http://127.0.0.1:8765
 */
public class PruebaCatalogo {
    static int f = 0;
    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        Map<String, String> override = new HashMap<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath().substring(1);
            byte[] body;
            int code = 200;
            if (override.containsKey(p)) body = override.get(p).getBytes("UTF-8");
            else if (Files.exists(dir.resolve(p))) body = Files.readAllBytes(dir.resolve(p));
            else { body = new byte[0]; code = 404; }
            ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
            if (body.length > 0) ex.getResponseBody().write(body);
            ex.close();
        });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort() + "/";

        Catalog cat = new Catalog(base);
        List<Catalog.Entry> all = cat.entries();
        ok(all.size() >= 1, "índice leído: " + all.size() + " receta(s)");
        ok(cat.search("zte livebox").size() == 1 && cat.search("parlem").size() == 1, "búsqueda por modelo y compañía");
        ok(cat.search("tplink archer").isEmpty(), "búsqueda sin resultados");
        Catalog.Entry zte = cat.search("zte").get(0);
        String text = cat.download(zte);
        ok(Recipe.parse(text).id().equals("zte-zxhn-f6640p"), "receta descargada y validada (huella correcta)");

        override.put("recetas/zte-zxhn-f6640p.json", text.replace("Livebox 6s", "Livebox 6S"));
        try { new Catalog(base).download(zte); ok(false, "debería rechazar una receta modificada"); }
        catch (RouterException e) { ok(e.getMessage().contains("huella"), "rechaza una receta modificada: " + e.getMessage()); }
        override.clear();

        Catalog.Entry evil = new Catalog.Entry();
        evil.path = "../../etc/passwd.json"; evil.sha256 = "x";
        try { cat.download(evil); ok(false, "debería rechazar rutas peligrosas"); }
        catch (RouterException e) { ok(true, "rechaza rutas fuera del catálogo"); }

        // Detección por la página de inicio
        override.put("index.json", new String(Files.readAllBytes(dir.resolve("index.json")), "UTF-8")
                .replace("\"detect\": []", "\"detect\": [\"Frm_Password\"]"));
        ok(new Catalog(base).detect("<html>login Frm_Password</html>").size() == 1, "reconoce el router por su página de inicio");
        ok(new Catalog(base).detect("<html>otro router</html>").isEmpty(), "no confunde otros routers");
        override.clear();
        // Fichas de router
        Catalog fc = new Catalog(base);
        ok(fc.sheets().size() >= 2, "fichas leídas: " + fc.sheets().size());
        Catalog.Sheet sh = fc.findSheet("Askey", "RTF8316VW");
        ok(sh != null && "app-only".equals(sh.support) && !sh.avoid.isEmpty(), "ficha del Askey: no permite bloquear desde el panel");
        ok(fc.findSheet("movistar", "rtf 8316-vw") != null, "encuentra la ficha aunque se escriba distinto");
        ok(fc.findSheet("Parlem", "Livebox 6s") != null && fc.findSheet("Parlem", "Livebox 6s").recipe.equals("zte-zxhn-f6640p"),
                "encuentra el ZTE por su nombre comercial y enlaza su receta");
        ok(fc.findSheet("TP-Link", "Archer C6") == null, "sin ficha para un router desconocido");
        ok(sh.confirmed(), "la ficha del Askey está comprobada");
        Catalog.Sheet mv = fc.findSheet("Movistar", "Smart WiFi 6");
        ok(mv != null && !mv.confirmed() && "app-only".equals(mv.support), "Smart WiFi 6 de Movistar: probable, sin confirmar (" + (mv == null ? "-" : mv.id) + ")");
        Catalog.Sheet dg = fc.findSheet("ZTE", "H3600P");
        ok(dg != null && dg.canBlock() && dg.where.size() == 2 && !dg.sources.isEmpty(), "Digi H3600P: se puede, con camino de menús y fuente");
        ok(fc.findSheet("Yoigo", "Livebox 6s") != null && fc.findSheet("Yoigo", "Livebox 6s").recipe.equals("zte-zxhn-f6640p"),
                "el Livebox 6s de Yoigo enlaza la receta del ZTE");
        ok(fc.findSheet("Orange", "Livebox 6") != null && fc.findSheet("Orange", "Livebox 6").id.equals("arcadyan-arltfibra6"),
                "Orange Livebox 6 no se confunde con el Livebox 6s");
        Catalog.Sheet l7 = fc.findSheet("Orange", "Livebox 7");
        ok(l7 != null && l7.id.equals("orange-livebox-7") && "official".equals(l7.confidence) && l7.where.size() == 4,
                "Livebox 7: camino de la ayuda oficial de Orange");
        String repUrl = Catalog.reportUrl("Askey", "RTF8316VW", "Movistar", "app-only", new ArrayList<>(), new ArrayList<>(), "x");
        ok(repUrl.contains("template=ficha-router.yml") && repUrl.contains("bloqueo=app-only") && repUrl.contains("modelo=RTF8316VW"),
                "enlace para informar con el formulario rellenado");
                srv.stop(0);

        // Pruebas de compatibilidad contra el simulador del ZTE
        RecipeAdapter ad = new RecipeAdapter(Recipe.parse(text));
        RouterCredentials c = new RouterCredentials(a[1], "admin", "clave-prueba");
        RecipeTester.Report rep = RecipeTester.readTest(ad, c);
        ok(rep.readOk && rep.devices.size() == 3, "prueba de lectura correcta");
        RecipeTester.writeTest(ad, c, rep.devices.get(1), rep);
        ok(rep.writeOk, "prueba de corte y devolución correcta");
        System.out.println(rep.text().replaceAll("(?m)^", "        "));
        ok(rep.text().contains("✓ Comprobado en una sesión nueva: con Internet"), "el informe incluye la línea que reconoce el catálogo");
        RecipeTester.Report bad = RecipeTester.readTest(ad, new RouterCredentials(a[1], "admin", "mal"));
        ok(!bad.readOk && bad.text().contains("No se pudo iniciar sesión"), "con contraseña errónea el informe lo dice");

        System.out.println(f == 0 ? "TODO CORRECTO" : f + " FALLOS");
        System.exit(f);
    }
}
