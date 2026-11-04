package com.leira.wificasa.core;

import java.util.regex.Pattern;

/**
 * El router ha cerrado o invalidado la sesión de administrador a mitad de una operación
 * (p. ej. "SessionTimeout" en el ZTE). {@link RouterEngine} lo trata iniciando sesión de nuevo
 * y repitiendo la operación una vez.
 */
public class SessionExpiredException extends RouterException {

    private static final Pattern SESSION_TEXT =
            Pattern.compile("(?i)session|sesi[oó]n caducada|sesi[oó]n no v[aá]lida|timeout");

    public SessionExpiredException(String msg) { super(msg); }

    /** ¿Este texto de error del router indica una sesión caducada o invalidada? */
    public static boolean looksLikeSession(String routerText) {
        return routerText != null && SESSION_TEXT.matcher(routerText).find();
    }
}
