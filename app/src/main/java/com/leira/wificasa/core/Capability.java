package com.leira.wificasa.core;

/**
 * Operaciones que un adaptador puede ofrecer. La app solo muestra lo que el router
 * realmente admite: no se da por hecho que todos los routers lo permiten todo.
 */
public enum Capability {
    /** listarDispositivos */
    LIST_DEVICES,
    /** obtenerEstado (saber si un dispositivo tiene o no Internet) */
    READ_ACCESS,
    /** bloquearDispositivo */
    BLOCK,
    /** desbloquearDispositivo */
    UNBLOCK
}
