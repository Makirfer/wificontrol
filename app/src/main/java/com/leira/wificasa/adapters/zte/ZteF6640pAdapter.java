package com.leira.wificasa.adapters.zte;

import com.leira.wificasa.core.AccessState;
import com.leira.wificasa.core.Capability;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterAdapter;
import com.leira.wificasa.core.RouterCredentials;
import com.leira.wificasa.core.RouterException;
import com.leira.wificasa.core.RouterSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Adaptador nativo para el ZTE Livebox 6s (ZXHN F6640P) de Parlem/Orange. Primera implementación. */
public final class ZteF6640pAdapter implements RouterAdapter {

    public static final String ID = "zte-zxhn-f6640p";
    static final String EXTRA_DEV_TYPE = "zte.devType";

    private static final Set<Capability> CAPS = Collections.unmodifiableSet(EnumSet.of(
            Capability.LIST_DEVICES, Capability.READ_ACCESS, Capability.BLOCK, Capability.UNBLOCK));

    @Override public String id() { return ID; }
    @Override public String displayName() { return "ZTE Livebox 6s (ZXHN F6640P)"; }
    @Override public String defaultHost() { return "192.168.1.1"; }
    @Override public String defaultUser() { return "admin"; }
    @Override public Set<Capability> capabilities() { return CAPS; }

    @Override
    public RouterSession open(RouterCredentials c) throws RouterException {
        ZteClient client = new ZteClient(c.host.isEmpty() ? defaultHost() : c.host);
        try {
            client.login(c.user.isEmpty() ? defaultUser() : c.user, c.password);
        } catch (RouterException e) {
            client.logout(); // como en la v1: liberar la sesión SID aunque el login falle
            throw e;
        }
        return new Session(client);
    }

    private static final class Session implements RouterSession {
        private final ZteClient client;

        Session(ZteClient client) { this.client = client; }

        @Override
        public List<NetDevice> listDevices() throws RouterException {
            List<NetDevice> out = new ArrayList<>();
            for (ZteClient.Device z : client.listDevices()) out.add(toCommon(z));
            return out;
        }

        @Override
        public AccessState readAccess(NetDevice d) throws RouterException {
            ZteClient.AccessRule r = client.getRule(toZte(d));
            d.access = toState(r.mode);
            return d.access;
        }

        @Override
        public void setBlocked(NetDevice d, boolean blocked) throws RouterException {
            client.setBlocked(toZte(d), blocked);
        }

        @Override
        public void close() { client.logout(); }
    }

    static AccessState toState(int mode) {
        switch (mode) {
            case ZteClient.MODE_ALLOW: return AccessState.ALLOWED;
            case ZteClient.MODE_BLOCK: return AccessState.BLOCKED;
            case ZteClient.MODE_SCHEDULE: return AccessState.SCHEDULED;
            default: return AccessState.UNKNOWN;
        }
    }

    private static NetDevice toCommon(ZteClient.Device z) {
        NetDevice d = new NetDevice();
        d.mac = z.mac;
        d.hostName = z.hostName;
        d.ip = z.ip;
        d.active = z.active;
        d.extra.put(EXTRA_DEV_TYPE, z.devType);
        return d;
    }

    private static ZteClient.Device toZte(NetDevice d) {
        ZteClient.Device z = new ZteClient.Device();
        z.mac = d.mac;
        z.hostName = d.hostName;
        z.ip = d.ip;
        z.active = d.active;
        z.devType = d.extra(EXTRA_DEV_TYPE, "1");
        return z;
    }
}
