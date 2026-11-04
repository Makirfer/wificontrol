package com.leira.wificasa.core.recipe;

import com.leira.wificasa.core.AccessState;
import com.leira.wificasa.core.Capability;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterAdapter;
import com.leira.wificasa.core.RouterCredentials;
import com.leira.wificasa.core.RouterEngine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pruebas de compatibilidad de una receta contra el router real, en dos fases:
 * primero solo lectura y, con permiso del usuario, una escritura controlada
 * (cortar y devolver Internet a un dispositivo elegido, comprobando cada paso).
 */
public final class RecipeTester {

    /** Resultado legible de las pruebas; también se adjunta al publicar en el catálogo. */
    public static final class Report {
        public final List<String> lines = new ArrayList<>();
        public boolean readOk;
        public boolean writeOk;
        public boolean writeTested;
        public List<NetDevice> devices = new ArrayList<>();

        void ok(String s) { lines.add("✓ " + s); }
        void fail(String s) { lines.add("✗ " + s); }
        void info(String s) { lines.add("· " + s); }

        public String text() { return String.join("\n", lines); }
    }

    private RecipeTester() { }

    /** Fase 1, solo lectura: iniciar sesión, listar y leer estados. No cambia nada en el router. */
    public static Report readTest(RouterAdapter a, RouterCredentials c) {
        Report r = new Report();
        r.info("Prueba del " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())
                + " · " + a.displayName());
        RouterEngine e = new RouterEngine(a, c);
        try {
            List<NetDevice> list = e.run(ops -> ops.listarDispositivos());
            r.ok("Sesión iniciada y cerrada");
            r.ok("Lista de dispositivos: " + list.size() + " encontrados");
            if (list.isEmpty()) {
                r.fail("El router no ha devuelto ningún dispositivo: revisa la operación listDevices");
                return r;
            }
            int withMac = 0;
            for (NetDevice d : list) if (d.mac.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}")) withMac++;
            if (withMac == list.size()) r.ok("Todas las MAC tienen formato correcto");
            else r.fail((list.size() - withMac) + " dispositivo(s) con MAC mal leída: revisa el campo mac");
            if (a.capabilities().contains(Capability.READ_ACCESS)) {
                List<NetDevice> states = e.run(ops -> ops.listarConEstado());
                int known = 0;
                for (NetDevice d : states) if (d.access != AccessState.UNKNOWN) known++;
                r.ok("Estado de acceso leído en " + known + " de " + states.size());
                list = states;
            } else {
                r.info("La receta no declara lectura de estado (READ_ACCESS)");
            }
            r.devices = list;
            r.readOk = withMac == list.size();
        } catch (Exception ex) {
            r.fail(ex.getMessage());
        }
        return r;
    }

    /**
     * Fase 2, escritura controlada sobre UN dispositivo: cortar, comprobar en una sesión nueva,
     * devolver y comprobar. Si algo falla después de cortar, intenta siempre devolver Internet.
     */
    public static void writeTest(RouterAdapter a, RouterCredentials c, NetDevice target, Report r) {
        r.writeTested = true;
        Caps caps = new Caps(a);
        if (!caps.block || !caps.unblock) {
            r.info("La receta no permite cortar y devolver Internet: no hay prueba de escritura");
            return;
        }
        RouterEngine e = new RouterEngine(a, c);
        String name = target.hostName.isEmpty() ? target.mac : target.hostName;
        boolean blocked = false;
        try {
            e.run(ops -> {
                NetDevice d = ops.buscar(target.mac);
                ops.bloquearDispositivo(d != null ? d : target);
                return null;
            });
            blocked = true;
            r.ok("Internet cortado a " + name);
            if (caps.read) {
                AccessState s = stateOf(e, target.mac);
                if (s == AccessState.BLOCKED) r.ok("Comprobado en una sesión nueva: sin Internet");
                else { r.fail("En una sesión nueva el estado es " + s + " (se esperaba BLOCKED)"); return; }
            }
            e.run(ops -> {
                NetDevice d = ops.buscar(target.mac);
                ops.desbloquearDispositivo(d != null ? d : target);
                return null;
            });
            blocked = false;
            r.ok("Internet devuelto a " + name);
            if (caps.read) {
                AccessState s = stateOf(e, target.mac);
                if (s == AccessState.ALLOWED) r.ok("Comprobado en una sesión nueva: con Internet");
                else { r.fail("En una sesión nueva el estado es " + s + " (se esperaba ALLOWED)"); return; }
            }
            r.writeOk = true;
        } catch (Exception ex) {
            r.fail(ex.getMessage());
        } finally {
            if (blocked) {
                try {
                    e.run(ops -> {
                        NetDevice d = ops.buscar(target.mac);
                        ops.desbloquearDispositivo(d != null ? d : target);
                        return null;
                    });
                    r.info("Se ha devuelto Internet a " + name + " tras el fallo");
                } catch (Exception ex2) {
                    r.fail("NO se pudo devolver Internet a " + name + ": hazlo desde el panel del router ("
                            + ex2.getMessage() + ")");
                }
            }
        }
    }

    private static AccessState stateOf(RouterEngine e, String mac) throws Exception {
        return e.run(ops -> {
            NetDevice d = ops.buscar(mac);
            return d == null ? AccessState.UNKNOWN : ops.obtenerEstado(d);
        });
    }

    private static final class Caps {
        final boolean block, unblock, read;
        Caps(RouterAdapter a) {
            block = a.capabilities().contains(Capability.BLOCK);
            unblock = a.capabilities().contains(Capability.UNBLOCK);
            read = a.capabilities().contains(Capability.READ_ACCESS);
        }
    }
}
