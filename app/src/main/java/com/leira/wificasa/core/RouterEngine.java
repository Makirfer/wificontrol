package com.leira.wificasa.core;

import java.util.List;

/**
 * Motor de routers: ofrece las operaciones estándar de la app y las delega en el adaptador.
 * Se encarga de abrir y cerrar la sesión, de comprobar que el router admite la operación
 * y de verificar que los cambios se han aplicado de verdad.
 */
public final class RouterEngine {

    public interface Job<T> {
        T run(Ops ops) throws Exception;
    }

    private final RouterAdapter adapter;
    private final RouterCredentials credentials;

    public RouterEngine(RouterAdapter adapter, RouterCredentials credentials) {
        this.adapter = adapter;
        this.credentials = credentials;
    }

    public RouterAdapter adapter() { return adapter; }

    public boolean supports(Capability c) { return adapter.capabilities().contains(c); }

    /** Espera antes de reintentar tras una sesión caducada (milisegundos). */
    static long retryDelayMs = 1500;

    /**
     * Inicia sesión, ejecuta el trabajo y cierra sesión pase lo que pase.
     * Si el router cierra la sesión a mitad ({@link SessionExpiredException}), espera un momento,
     * inicia sesión de nuevo y repite el trabajo una vez. Los trabajos de la app se pueden
     * repetir sin riesgo: cortar algo ya cortado lo deja igual.
     */
    public <T> T run(Job<T> job) throws Exception {
        try {
            return runOnce(job);
        } catch (SessionExpiredException first) {
            if (retryDelayMs > 0) Thread.sleep(retryDelayMs);
            try {
                return runOnce(job);
            } catch (SessionExpiredException second) {
                throw new RouterException(second.getMessage()
                        + " (se ha reintentado una vez). ¿Está abierto el panel del router en otro dispositivo?");
            }
        }
    }

    private <T> T runOnce(Job<T> job) throws Exception {
        RouterSession s = adapter.open(credentials);
        try {
            return job.run(new Ops(s));
        } finally {
            s.close();
        }
    }

    /** Operaciones disponibles dentro de una sesión abierta. */
    public final class Ops {
        private final RouterSession s;

        private Ops(RouterSession s) { this.s = s; }

        public List<NetDevice> listarDispositivos() throws RouterException {
            require(Capability.LIST_DEVICES, "listar los dispositivos");
            return s.listDevices();
        }

        public AccessState obtenerEstado(NetDevice d) throws RouterException {
            if (!supports(Capability.READ_ACCESS)) return d.access = AccessState.UNKNOWN;
            return s.readAccess(d);
        }

        /** Lista los dispositivos y lee el estado de cada uno. */
        public List<NetDevice> listarConEstado() throws RouterException {
            List<NetDevice> list = listarDispositivos();
            for (NetDevice d : list) obtenerEstado(d);
            return list;
        }

        public void bloquearDispositivo(NetDevice d) throws RouterException {
            require(Capability.BLOCK, "cortar Internet a un dispositivo");
            change(d, true);
        }

        public void desbloquearDispositivo(NetDevice d) throws RouterException {
            require(Capability.UNBLOCK, "devolver Internet a un dispositivo");
            change(d, false);
        }

        /** Busca el dispositivo por MAC en la lista actual del router. */
        public NetDevice buscar(String mac) throws RouterException {
            for (NetDevice d : listarDispositivos()) if (d.mac.equalsIgnoreCase(mac)) return d;
            return null;
        }

        private void change(NetDevice d, boolean blocked) throws RouterException {
            s.setBlocked(d, blocked);
            if (supports(Capability.READ_ACCESS)) {
                AccessState after = s.readAccess(d);
                AccessState want = blocked ? AccessState.BLOCKED : AccessState.ALLOWED;
                if (after != want) {
                    String n = d.hostName.isEmpty() ? d.mac : d.hostName;
                    throw new RouterException("El router no ha aplicado el cambio en " + n + ".");
                }
            } else {
                d.access = blocked ? AccessState.BLOCKED : AccessState.ALLOWED;
            }
        }

        private void require(Capability c, String what) throws RouterException {
            if (!supports(c)) {
                throw new RouterException("Este router (" + adapter.displayName() + ") no permite " + what + ".");
            }
        }
    }
}
