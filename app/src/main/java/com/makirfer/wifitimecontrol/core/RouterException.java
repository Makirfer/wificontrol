package com.makirfer.wifitimecontrol.core;

/** Error al hablar con un router. El mensaje está pensado para mostrarse al usuario. */
public class RouterException extends Exception {
    public RouterException(String msg) { super(msg); }
    public RouterException(String msg, Throwable cause) { super(msg, cause); }
}
