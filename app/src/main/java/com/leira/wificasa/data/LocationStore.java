package com.leira.wificasa.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.leira.wificasa.core.AccessState;
import com.leira.wificasa.core.AdapterRegistry;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterAdapter;
import com.leira.wificasa.core.RouterCredentials;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Perfiles de ubicación guardados en el móvil. Cada ubicación tiene su router, su contraseña
 * (cifrada con {@link SecretBox}), sus nombres de dispositivos y su grupo de pausa.
 *
 * Al abrir por primera vez la versión 2, migra la configuración de la versión 1 a una
 * ubicación "Casa" sin que el usuario tenga que volver a escribir nada.
 */
public final class LocationStore {

    private static final String PREFS = "wificasa";
    private static final int SCHEMA = 2;

    private final SharedPreferences p;

    public LocationStore(Context ctx) {
        p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrate();
    }

    // ------------------------------------------------------------------ ubicaciones

    public List<Location> all() {
        List<Location> out = new ArrayList<>();
        for (String id : order()) {
            Location l = get(id);
            if (l != null) out.add(l);
        }
        return out;
    }

    public Location get(String id) {
        if (id == null || !p.contains(k(id, "name"))) return null;
        return new Location(id,
                p.getString(k(id, "name"), "Casa"),
                p.getString(k(id, "adapter"), AdapterRegistry.DEFAULT_ID),
                p.getString(k(id, "host"), ""),
                p.getString(k(id, "user"), ""));
    }

    /** Ubicación seleccionada, o null si todavía no hay ninguna. */
    public Location current() {
        Location l = get(p.getString("current", null));
        if (l != null) return l;
        List<String> ids = order();
        return ids.isEmpty() ? null : get(ids.get(0));
    }

    public void setCurrent(String id) { p.edit().putString("current", id).apply(); }

    public Location create(String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        RouterAdapter a = AdapterRegistry.get(AdapterRegistry.DEFAULT_ID);
        Location l = new Location(id, name, a.id(), a.defaultHost(), a.defaultUser());
        List<String> ids = order();
        ids.add(id);
        SharedPreferences.Editor e = p.edit().putString("loc_order", join(ids));
        write(e, l);
        e.apply();
        return l;
    }

    /** Guarda la ubicación. Si {@code newPassword} es null, conserva la contraseña actual. */
    public void save(Location l, String newPassword) throws Exception {
        SharedPreferences.Editor e = p.edit();
        write(e, l);
        if (newPassword != null) e.putString(k(l.id, "pass"), SecretBox.encrypt(newPassword));
        e.apply();
    }

    public void delete(String id) {
        List<String> ids = order();
        ids.remove(id);
        SharedPreferences.Editor e = p.edit().putString("loc_order", join(ids));
        String prefix = "loc." + id + ".";
        for (String key : p.getAll().keySet()) if (key.startsWith(prefix)) e.remove(key);
        if (id.equals(p.getString("current", null))) e.remove("current");
        e.apply();
    }

    public boolean hasPassword(Location l) { return !p.getString(k(l.id, "pass"), "").isEmpty(); }

    /** Credenciales descifradas para conectar. Contraseña vacía si no se pudo descifrar. */
    public RouterCredentials credentials(Location l) {
        String pass;
        try {
            pass = SecretBox.decrypt(p.getString(k(l.id, "pass"), ""));
        } catch (Exception e) {
            pass = ""; // p. ej. clave del Keystore borrada: se pedirá de nuevo
        }
        RouterAdapter a = AdapterRegistry.get(l.adapterId);
        return new RouterCredentials(l.host.isEmpty() ? a.defaultHost() : l.host,
                l.user.isEmpty() ? a.defaultUser() : l.user, pass);
    }

    // ------------------------------------------------------------------ datos por dispositivo

    public String alias(Location l, String mac) { return p.getString(k(l.id, "alias." + mac), ""); }

    public Set<String> studySet(Location l) {
        return new HashSet<>(p.getStringSet(k(l.id, "study"), new HashSet<String>()));
    }

    /** Nombre opcional del grupo de pausa ("Deberes", "Hora de cenar"…); vacío = sin nombre. */
    public String groupName(Location l) { return p.getString(k(l.id, "group"), ""); }

    public void setGroupName(Location l, String name) {
        p.edit().putString(k(l.id, "group"), name == null ? "" : name.trim()).apply();
    }

    public void saveDevice(Location l, String mac, String alias, boolean inStudy) {
        Set<String> s = studySet(l);
        if (inStudy) s.add(mac); else s.remove(mac);
        p.edit().putString(k(l.id, "alias." + mac), alias).putStringSet(k(l.id, "study"), s).apply();
    }

    // ------------------------------------------------------------------ dispositivos recordados

    private static final String SEP = "\u001F", EXTRA_SEP = "\u001E";

    /**
     * Guarda (o actualiza) los dispositivos que el router acaba de listar, con la hora actual.
     * Los que ya se conocían y no aparecen ahora se conservan tal cual.
     */
    public void rememberDevices(Location l, List<NetDevice> list, long now) {
        SharedPreferences.Editor e = p.edit();
        for (NetDevice d : list) {
            if (d.mac.isEmpty()) continue;
            StringBuilder extra = new StringBuilder();
            for (Map.Entry<String, String> x : d.extra.entrySet()) {
                if (extra.length() > 0) extra.append(EXTRA_SEP);
                extra.append(clean(x.getKey())).append('=').append(clean(x.getValue()));
            }
            e.putString(k(l.id, "known." + d.mac), clean(d.hostName) + SEP + clean(d.ip) + SEP
                    + d.access.name() + SEP + now + SEP + extra);
        }
        e.apply();
    }

    /** Todos los dispositivos que el router ha mostrado alguna vez en esta ubicación (estado: el último conocido). */
    public List<NetDevice> knownDevices(Location l) {
        List<NetDevice> out = new ArrayList<>();
        String prefix = k(l.id, "known.");
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            if (!en.getKey().startsWith(prefix) || !(en.getValue() instanceof String)) continue;
            String[] f = ((String) en.getValue()).split(SEP, -1);
            if (f.length < 4) continue;
            NetDevice d = new NetDevice();
            d.mac = en.getKey().substring(prefix.length());
            d.hostName = f[0];
            d.ip = f[1];
            try { d.access = AccessState.valueOf(f[2]); } catch (IllegalArgumentException ex) { d.access = AccessState.UNKNOWN; }
            try { d.lastSeenMs = Long.parseLong(f[3]); } catch (NumberFormatException ex) { d.lastSeenMs = 0; }
            if (f.length > 4 && !f[4].isEmpty()) {
                for (String kv : f[4].split(EXTRA_SEP)) {
                    int i = kv.indexOf('=');
                    if (i > 0) d.extra.put(kv.substring(0, i), kv.substring(i + 1));
                }
            }
            d.active = false;
            out.add(d);
        }
        return out;
    }

    /** Deja de recordar un dispositivo (también su nombre y su marca de pausa). */
    public void forgetDevice(Location l, String mac) {
        Set<String> s = studySet(l);
        s.remove(mac);
        p.edit().remove(k(l.id, "known." + mac)).remove(k(l.id, "alias." + mac))
                .putStringSet(k(l.id, "study"), s).apply();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(SEP, " ").replace(EXTRA_SEP, " ").replace("=", "-");
    }

    // ------------------------------------------------------------------ interno

    private static String k(String id, String field) { return "loc." + id + "." + field; }

    private void write(SharedPreferences.Editor e, Location l) {
        e.putString(k(l.id, "name"), l.name)
                .putString(k(l.id, "adapter"), l.adapterId)
                .putString(k(l.id, "host"), l.host)
                .putString(k(l.id, "user"), l.user);
    }

    private List<String> order() {
        List<String> out = new ArrayList<>();
        for (String s : p.getString("loc_order", "").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private static String join(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String s : ids) { if (sb.length() > 0) sb.append(','); sb.append(s); }
        return sb.toString();
    }

    /** v1 (host, user, pass, alias_MAC y study, en claro) → v2 (ubicación "Casa", contraseña cifrada). */
    private void migrate() {
        if (p.getInt("schema", 0) >= SCHEMA) return;
        boolean hadV1 = p.contains("pass") || p.contains("host");
        if (!hadV1) {
            p.edit().putInt("schema", SCHEMA).apply();
            return;
        }
        String id = "casa";
        Location l = new Location(id, "Casa", AdapterRegistry.DEFAULT_ID,
                p.getString("host", "192.168.1.1"), p.getString("user", "admin"));
        String encrypted;
        try {
            encrypted = SecretBox.encrypt(p.getString("pass", ""));
        } catch (Exception ex) {
            return; // se reintentará en el próximo arranque; la v1 sigue intacta
        }
        SharedPreferences.Editor e = p.edit();
        write(e, l);
        e.putString(k(id, "pass"), encrypted);
        e.putStringSet(k(id, "study"), new HashSet<>(p.getStringSet("study", new HashSet<String>())));
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            if (en.getKey().startsWith("alias_") && en.getValue() instanceof String) {
                e.putString(k(id, "alias." + en.getKey().substring(6)), (String) en.getValue());
                e.remove(en.getKey());
            }
        }
        e.remove("host").remove("user").remove("pass").remove("study");
        e.putString("loc_order", id).putString("current", id).putInt("schema", SCHEMA);
        e.apply();
    }
}
