package com.leira.wificasa.core;

import java.util.List;

/**
 * Sesión abierta con un router. Cada adaptador implementa solo lo que su router admite;
 * lo que no admita debe lanzar {@link UnsupportedOperationException} y no declararlo
 * en {@link RouterAdapter#capabilities()}.
 *
 * No es segura para varios hilos: usar desde un único hilo de trabajo.
 */
public interface RouterSession extends AutoCloseable {

    List<NetDevice> listDevices() throws RouterException;

    /** Lee el estado de acceso y lo guarda también en {@code d.access}. */
    AccessState readAccess(NetDevice d) throws RouterException;

    /** Envía el cambio. La comprobación posterior la hace {@link RouterEngine}. */
    void setBlocked(NetDevice d, boolean blocked) throws RouterException;

    /** Cierra la sesión en el router. No lanza errores: si falla, la sesión caduca sola. */
    @Override void close();
}
