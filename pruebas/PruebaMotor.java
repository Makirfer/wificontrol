import com.leira.wificasa.core.*;
import java.util.*;

/**
 * Prueba del motor de routers contra el simulador (pruebas/simulador_zte.py).
 * Reproduce los mismos flujos que la app: actualizar, cortar, devolver y contraseña errónea.
 * Uso: java -cp <clases> PruebaMotor http://127.0.0.1:8765 [idAdaptador | receta.json]
 */
public class PruebaMotor {
    static int fallos = 0;

    static void ok(boolean cond, String what) {
        System.out.println((cond ? "  OK   " : "  FALLO ") + what);
        if (!cond) fallos++;
    }

    static RouterEngine engine(String host, String adapterId, String pass) {
        return new RouterEngine(AdapterRegistry.get(adapterId), new RouterCredentials(host, "admin", pass));
    }

    public static void main(String[] a) throws Exception {
        String host = a[0];
        String id = a.length > 1 ? a[1] : AdapterRegistry.DEFAULT_ID;
        if (id.endsWith(".json")) { // probar una receta en lugar del adaptador nativo
            com.leira.wificasa.core.recipe.RecipeAdapter ra = new com.leira.wificasa.core.recipe.RecipeAdapter(
                    com.leira.wificasa.core.recipe.Recipe.parse(new String(java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get(id)), java.nio.charset.StandardCharsets.UTF_8)));
            AdapterRegistry.register(ra);
            id = ra.id();
            System.out.println("Huella de la receta: " + ra.recipe().fingerprint());
        }
        System.out.println("Adaptador: " + AdapterRegistry.get(id).displayName());
        RouterEngine e = engine(host, id, "clave-prueba");

        // 1. Actualizar
        List<NetDevice> l = e.run(ops -> ops.listarConEstado());
        ok(l.size() == 3, "lista 3 dispositivos");
        ok(l.get(0).mac.equals("aa:bb:cc:00:00:01"), "MAC normalizada a minúsculas");
        ok(l.get(0).hostName.equals("Portátil-Salón"), "nombre con acento decodificado: " + l.get(0).hostName);
        ok(l.get(0).access == AccessState.ALLOWED, "dispositivo 1 con Internet");
        ok(l.get(2).access == AccessState.SCHEDULED, "dispositivo 3 con horario");
        ok(!l.get(2).active, "dispositivo 3 no conectado");

        // 2. Cortar Internet (como toggleDevice: el trabajo + lista final con estados)
        // Igual que la app: el cambio en una sesión y la lista en otra nueva
        e.run(ops -> { ops.bloquearDispositivo(ops.buscar("aa:bb:cc:00:00:01")); return null; });
        List<NetDevice> after = e.run(ops -> ops.listarConEstado());
        ok(after.get(0).access == AccessState.BLOCKED, "dispositivo 1 sin Internet tras cortar");

        // 3. Devolver Internet
        e.run(ops -> { ops.desbloquearDispositivo(ops.buscar("aa:bb:cc:00:00:01")); return null; });
        after = e.run(ops -> ops.listarConEstado());
        ok(after.get(0).access == AccessState.ALLOWED, "dispositivo 1 con Internet tras devolver");

        // 3b. El router cierra la sesión al guardar (SessionTimeout): el motor reintenta solo
        new java.net.URL(host + "/__fail_next_post").openStream().close();
        e.run(ops -> { ops.bloquearDispositivo(ops.buscar("aa:bb:cc:00:00:02")); return null; });
        after = e.run(ops -> ops.listarConEstado());
        ok(after.get(1).access == AccessState.BLOCKED, "SessionTimeout al guardar → reintento automático correcto");
        e.run(ops -> { ops.desbloquearDispositivo(ops.buscar("aa:bb:cc:00:00:02")); return null; });

                // 3c. Modo estudio con dos aparatos en la misma sesión (varios guardados seguidos)
        e.run(ops -> {
            for (NetDevice d : ops.listarDispositivos()) if (!d.mac.endsWith(":03")) ops.bloquearDispositivo(d);
            return null;
        });
        after = e.run(ops -> ops.listarConEstado());
        ok(after.get(0).access == AccessState.BLOCKED && after.get(1).access == AccessState.BLOCKED,
                "modo estudio: varios cambios seguidos en una sesión");
        e.run(ops -> {
            for (NetDevice d : ops.listarDispositivos()) if (!d.mac.endsWith(":03")) ops.desbloquearDispositivo(d);
            return null;
        });

        // 3d. Lo que fallaba en el móvil: listar en la misma sesión tras un cambio
        try {
            e.run(ops -> { ops.bloquearDispositivo(ops.buscar("aa:bb:cc:00:00:01")); return ops.listarConEstado(); });
            ok(false, "el simulador debería imitar el SessionTimeout del router real");
        } catch (RouterException ex) {
            ok(ex.getMessage().contains("SessionTimeout"), "reproducido el fallo del móvil: " + ex.getMessage());
        }
        e.run(ops -> { ops.desbloquearDispositivo(ops.buscar("aa:bb:cc:00:00:01")); return null; });

        // 4. Contraseña errónea
        try {
            engine(host, id, "mal").run(ops -> ops.listarDispositivos());
            ok(false, "contraseña errónea debería fallar");
        } catch (RouterException ex) {
            ok(ex.getMessage().contains("No se pudo iniciar sesión"), "contraseña errónea: " + ex.getMessage());
        }

        // 5. Router inalcanzable
        try {
            engine("127.0.0.1:1", id, "x").run(ops -> ops.listarDispositivos());
            ok(false, "router inalcanzable debería fallar");
        } catch (RouterException ex) {
            ok(ex.getMessage().contains("No se puede conectar"), "router inalcanzable: " + ex.getMessage());
        }

        System.out.println(fallos == 0 ? "TODO CORRECTO" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
