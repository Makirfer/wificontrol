package com.makirfer.wifitimecontrol.core;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Horario de una ubicación: a qué aparatos se corta Internet, qué días y en qué franja.
 * La franja puede cruzar la medianoche (21:00–08:00) y, si empieza y acaba a la misma hora,
 * dura el día entero. No depende de Android, así que se prueba en el ordenador.
 */
public final class Horario {

    private static final char SEP = '\u001F';

    public String id = "";
    public String name = "";
    public Set<String> macs = new LinkedHashSet<>();
    /** bit 0 = lunes … bit 6 = domingo. */
    public int days = 0b0011111;
    /** Minutos desde las 00:00. */
    public int start = 21 * 60, end = 8 * 60;
    public boolean enabled = true;

    public boolean hasDay(int dayIndex) { return (days & (1 << ((dayIndex % 7 + 7) % 7))) != 0; }

    /** ¿Está cortado Internet a esta hora según esta regla? */
    public boolean activeAt(LocalDateTime t) {
        if (!enabled || macs.isEmpty() || days == 0) return false;
        int d = t.getDayOfWeek().getValue() - 1; // lunes = 0
        int m = t.getHour() * 60 + t.getMinute();
        if (start == end) return hasDay(d);
        if (start < end) return hasDay(d) && m >= start && m < end;
        return (hasDay(d) && m >= start) || (hasDay(d - 1) && m < end); // cruza la medianoche
    }

    /** Cómo deben estar ahora los aparatos con horario: true = sin Internet. */
    public static Map<String, Boolean> desired(List<Horario> rules, LocalDateTime t) {
        Map<String, Boolean> out = new HashMap<>();
        for (Horario r : rules) {
            if (!r.enabled) continue;
            boolean on = r.activeAt(t);
            for (String mac : r.macs) out.put(mac, out.getOrDefault(mac, false) || on);
        }
        return out;
    }

    /** Próximo minuto (después de {@code t}) en que cambia algún aparato, o null si nunca. */
    public static LocalDateTime nextChange(List<Horario> rules, LocalDateTime t) {
        LocalDateTime x = t.withSecond(0).withNano(0);
        Map<String, Boolean> before = desired(rules, x);
        for (int i = 1; i <= 8 * 24 * 60; i++) {
            LocalDateTime y = x.plusMinutes(i);
            Map<String, Boolean> now = desired(rules, y);
            if (!now.equals(before)) return y;
        }
        return null;
    }

    /** Último minuto (como mucho {@code t}) en que cambió algún aparato, o null si no hay cambios. */
    public static LocalDateTime lastChange(List<Horario> rules, LocalDateTime t) {
        LocalDateTime x = t.withSecond(0).withNano(0);
        Map<String, Boolean> after = desired(rules, x);
        for (int i = 1; i <= 8 * 24 * 60; i++) {
            LocalDateTime y = x.minusMinutes(i);
            Map<String, Boolean> prev = desired(rules, y);
            if (!prev.equals(after)) return y.plusMinutes(1);
            after = prev;
        }
        return null;
    }

    public static String hhmm(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    // ------------------------------------------------------------------ guardar

    public String encode() {
        return clean(name) + SEP + String.join(",", macs) + SEP + days + SEP + start + SEP + end + SEP + (enabled ? 1 : 0);
    }

    public static Horario decode(String id, String s) {
        String[] f = s.split(String.valueOf(SEP), -1);
        if (f.length < 6) return null;
        Horario h = new Horario();
        h.id = id;
        h.name = f[0];
        h.macs = new LinkedHashSet<>();
        for (String m : f[1].split(",")) if (!m.trim().isEmpty()) h.macs.add(m.trim());
        try {
            h.days = Integer.parseInt(f[2]);
            h.start = Integer.parseInt(f[3]);
            h.end = Integer.parseInt(f[4]);
        } catch (NumberFormatException e) {
            return null;
        }
        h.enabled = "1".equals(f[5]);
        return h;
    }

    private static String clean(String s) { return s == null ? "" : s.replace(SEP, ' '); }
}
