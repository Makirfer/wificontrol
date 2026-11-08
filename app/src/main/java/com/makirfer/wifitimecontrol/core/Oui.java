package com.makirfer.wifitimecontrol.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Marca del aparato a partir de los 6 primeros dígitos de su MAC, con la lista pública del IEEE
 * que va dentro de la app (assets/oui.txt). Sin conexión. Las MAC privadas no tienen marca.
 */
public final class Oui {

    /** {marca, pista de tipo (puede estar vacía)}. */
    private final Map<String, String[]> table = new HashMap<>();

    public Oui(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                if (f.length >= 2) table.put(f[0], new String[]{f[1], f.length > 2 ? f[2] : ""});
            }
        }
    }

    private String[] find(String mac) {
        if (mac == null) return null;
        String hex = mac.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.ROOT);
        if (hex.length() < 6 || DeviceType.privateMac(mac)) return null;
        return table.get(hex.substring(0, 6));
    }

    /** Marca («Apple», «Nintendo»…) o "" si no se conoce. */
    public String brand(String mac) {
        String[] e = find(mac);
        return e == null ? "" : e[0];
    }

    /** Tipo que sugiere la marca (Nintendo → consola) o "" si la marca hace de todo. */
    public String hint(String mac) {
        String[] e = find(mac);
        return e == null ? "" : e[1];
    }

    public int size() { return table.size(); }
}
