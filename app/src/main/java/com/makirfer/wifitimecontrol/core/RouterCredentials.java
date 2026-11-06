package com.makirfer.wifitimecontrol.core;

/** Dirección y credenciales de un router. Solo viven en memoria y en el almacenamiento cifrado del móvil. */
public final class RouterCredentials {
    public final String host;
    public final String user;
    public final String password;

    public RouterCredentials(String host, String user, String password) {
        this.host = host == null ? "" : host.trim();
        this.user = user == null ? "" : user.trim();
        this.password = password == null ? "" : password;
    }

    /** "192.168.1.1" → "http://192.168.1.1", sin barra final. */
    public String baseUrl() {
        String h = host;
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        return h;
    }

    @Override public String toString() {
        // Nunca imprimir la contraseña en registros.
        return "RouterCredentials{" + host + ", " + user + ", ****}";
    }
}
