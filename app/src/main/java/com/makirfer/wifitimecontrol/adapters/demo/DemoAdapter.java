package com.makirfer.wifitimecontrol.adapters.demo;

import com.makirfer.wifitimecontrol.core.AccessState;
import com.makirfer.wifitimecontrol.core.Capability;
import com.makirfer.wifitimecontrol.core.NetDevice;
import com.makirfer.wifitimecontrol.core.RouterAdapter;
import com.makirfer.wifitimecontrol.core.RouterCredentials;
import com.makirfer.wifitimecontrol.core.RouterException;
import com.makirfer.wifitimecontrol.core.RouterSession;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Router de demostración: aparatos inventados en memoria, sin red. Sirve para probar la app sin
 * tocar un router real, para hacer capturas y vídeos sin datos personales, y para que el equipo
 * de revisión de Google Play pueda usarla.
 */
public final class DemoAdapter implements RouterAdapter {

    public static final String ID = "demo";

    private static final String[][] DEVICES = {
            {"02:00:00:00:10:01", "Consola", "192.168.1.20", "1"},
            {"02:00:00:00:10:02", "Tablet", "192.168.1.21", "1"},
            {"02:00:00:00:10:03", "Portatil-cole", "192.168.1.22", "1"},
            {"02:00:00:00:10:04", "Smart-TV", "192.168.1.23", "1"},
            {"02:00:00:00:10:05", "Movil-Ana", "192.168.1.24", "1"},
            {"02:00:00:00:10:06", "Altavoz", "192.168.1.25", "0"},
    };
    /** Estado simulado (mientras la app esté abierta). */
    private static final Map<String, Boolean> BLOCKED = new LinkedHashMap<>();

    @Override public String id() { return ID; }

    @Override public String displayName() { return "🎬 Demo"; }

    @Override public String defaultHost() { return "demo"; }

    @Override public String defaultUser() { return "demo"; }

    @Override public Set<Capability> capabilities() {
        return EnumSet.of(Capability.LIST_DEVICES, Capability.READ_ACCESS, Capability.BLOCK, Capability.UNBLOCK);
    }

    @Override
    public RouterSession open(RouterCredentials credentials) {
        return new RouterSession() {
            @Override
            public List<NetDevice> listDevices() {
                pause();
                List<NetDevice> out = new ArrayList<>();
                for (String[] r : DEVICES) {
                    NetDevice d = new NetDevice();
                    d.mac = r[0];
                    d.hostName = r[1];
                    d.ip = r[2];
                    d.active = "1".equals(r[3]);
                    d.access = state(d.mac);
                    d.lastSeenMs = System.currentTimeMillis();
                    out.add(d);
                }
                return out;
            }

            @Override
            public AccessState readAccess(NetDevice d) {
                d.access = state(d.mac);
                return d.access;
            }

            @Override
            public void setBlocked(NetDevice d, boolean blocked) throws RouterException {
                pause();
                synchronized (BLOCKED) { BLOCKED.put(d.mac, blocked); }
                d.access = blocked ? AccessState.BLOCKED : AccessState.ALLOWED;
            }

            @Override public void close() { }
        };
    }

    private static AccessState state(String mac) {
        synchronized (BLOCKED) {
            return Boolean.TRUE.equals(BLOCKED.get(mac)) ? AccessState.BLOCKED : AccessState.ALLOWED;
        }
    }

    /** Un poco de espera, como un router de verdad. */
    private static void pause() {
        try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
