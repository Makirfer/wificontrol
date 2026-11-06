package com.makirfer.wifitimecontrol.core;

/** Estado de acceso a Internet de un dispositivo, común a todos los routers. */
public enum AccessState {
    ALLOWED,
    BLOCKED,
    /** El router tiene un horario programado para este dispositivo. */
    SCHEDULED,
    /** No consultado o el router no lo informa. */
    UNKNOWN
}
