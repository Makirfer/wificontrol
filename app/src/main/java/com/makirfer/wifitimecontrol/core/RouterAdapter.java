package com.makirfer.wifitimecontrol.core;

import java.util.Set;

/** Traduce las operaciones comunes de la app a los métodos concretos de una familia de routers. */
public interface RouterAdapter {

    /** Identificador estable, p. ej. "zte-zxhn-f6640p". Se guarda en el perfil de cada ubicación. */
    String id();

    /** Nombre para mostrar, p. ej. "ZTE Livebox 6s (ZXHN F6640P)". */
    String displayName();

    String defaultHost();

    String defaultUser();

    Set<Capability> capabilities();

    /** Inicia sesión en el router. */
    RouterSession open(RouterCredentials credentials) throws RouterException;
}
