package com.makirfer.wifitimecontrol.core;

import com.makirfer.wifitimecontrol.adapters.zte.ZteF6640pAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adaptadores disponibles en la app. Las recetas importadas se añadirán aquí en fases posteriores. */
public final class AdapterRegistry {

    public static final String DEFAULT_ID = ZteF6640pAdapter.ID;

    private static final Map<String, RouterAdapter> ADAPTERS = new LinkedHashMap<>();

    static {
        register(new ZteF6640pAdapter());
    }

    private AdapterRegistry() { }

    public static synchronized void register(RouterAdapter a) {
        ADAPTERS.put(a.id(), a);
    }

    public static synchronized RouterAdapter get(String id) {
        RouterAdapter a = ADAPTERS.get(id);
        return a != null ? a : ADAPTERS.get(DEFAULT_ID);
    }

    public static synchronized List<RouterAdapter> all() {
        return Collections.unmodifiableList(new ArrayList<>(ADAPTERS.values()));
    }
}
