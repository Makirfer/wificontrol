package com.makirfer.wifitimecontrol.core;

import java.util.HashMap;
import java.util.Map;

/** Dispositivo de la red tal como lo ve la app, sea cual sea el router. */
public final class NetDevice {
    /** MAC en minúsculas con dos puntos: es el identificador estable del dispositivo. */
    public String mac = "";
    public String hostName = "";
    public String ip = "";
    public boolean active;
    public AccessState access = AccessState.UNKNOWN;
    /** Última vez que el router incluyó este dispositivo en su lista (ms desde 1970; 0 = no se sabe). */
    public long lastSeenMs;
    /** Datos propios del adaptador que hacen falta para operar (p. ej. DevType en ZTE). */
    public final Map<String, String> extra = new HashMap<>();

    public boolean isBlocked() { return access == AccessState.BLOCKED; }

    public String extra(String key, String def) {
        String v = extra.get(key);
        return v == null || v.isEmpty() ? def : v;
    }

    /** Copia mínima para operar sobre un dispositivo que ya no aparece en la lista del router. */
    public static NetDevice ofMac(String mac) {
        NetDevice d = new NetDevice();
        d.mac = mac == null ? "" : mac.toLowerCase();
        return d;
    }
}
