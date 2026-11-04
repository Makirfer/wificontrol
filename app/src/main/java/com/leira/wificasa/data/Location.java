package com.leira.wificasa.data;

/** Una ubicación (casa, segunda residencia…) con su router. La contraseña se guarda aparte, cifrada. */
public final class Location {
    public final String id;
    public String name;
    public String adapterId;
    public String host;
    public String user;

    public Location(String id, String name, String adapterId, String host, String user) {
        this.id = id;
        this.name = name;
        this.adapterId = adapterId;
        this.host = host;
        this.user = user;
    }
}
