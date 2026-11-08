package com.makirfer.wifitimecontrol;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.makirfer.wifitimecontrol.core.AdapterRegistry;
import com.makirfer.wifitimecontrol.core.Capability;
import com.makirfer.wifitimecontrol.core.Horario;
import com.makirfer.wifitimecontrol.core.NetDevice;
import com.makirfer.wifitimecontrol.core.RouterAdapter;
import com.makirfer.wifitimecontrol.core.RouterEngine;
import com.makirfer.wifitimecontrol.data.Location;
import com.makirfer.wifitimecontrol.data.LocationStore;
import com.makirfer.wifitimecontrol.data.RecipeStore;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Horarios ejecutados por el móvil: a la hora de cada cambio, el móvil entra en el router y
 * corta o devuelve Internet a los aparatos del horario. Solo funciona si a esa hora el móvil
 * está conectado al WiFi de esa ubicación; si no, lo reintenta cada pocos minutos hasta el
 * siguiente cambio (así, si llegas a casa a las 22:00, el corte de las 21:00 se aplica entonces).
 *
 * Solo actúa en los cambios de franja: si entre medias cortas o devuelves Internet a mano,
 * se respeta hasta el siguiente cambio. También devuelve Internet cuando acaba una pausa con
 * temporizador («30 minutos», «hasta mañana a las 8»).
 */
final class HorarioRunner {

    static final String ACTION_ALARM = "com.makirfer.wifitimecontrol.HORARIO";
    private static final int JOB_RETRY = 71;
    private static final long RETRY_MS = 5 * 60 * 1000L;

    private HorarioRunner() {}

    private static long ms(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    /**
     * Programa la alarma del próximo cambio de cualquier ubicación: un cambio de horario o el fin de
     * una pausa con temporizador. La quita si no queda ninguno.
     */
    static void reprogram(Context ctx) {
        LocationStore store = new LocationStore(ctx);
        LocalDateTime now = LocalDateTime.now();
        long next = Long.MAX_VALUE;
        for (Location l : store.all()) {
            LocalDateTime n = Horario.nextChange(store.schedules(l), now);
            if (n != null) next = Math.min(next, ms(n) + 5000); // unos segundos después del minuto exacto
            for (long t : store.timers(l).values()) next = Math.min(next, Math.max(t, System.currentTimeMillis() + 1000));
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, HorarioReceiver.class).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (next == Long.MAX_VALUE) { am.cancel(pi); return; }
        long at = next;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); // puede llegar unos minutos tarde
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    /** ¿Puede el móvil avisar a la hora exacta? (Android 12+ lo deja decidir al usuario.) */
    static boolean exactAllowed(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am == null || Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    /** Reintento cuando el móvil esté en un WiFi (el router solo responde desde dentro de casa). */
    static void scheduleRetry(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(JOB_RETRY, new ComponentName(ctx, HorarioJob.class))
                .setMinimumLatency(RETRY_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build());
    }

    /**
     * Aplica los horarios pendientes de todas las ubicaciones. Devuelve true si alguno no se pudo
     * aplicar y hay que reintentar. Se ejecuta fuera del hilo principal.
     */
    static boolean applyPending(Context ctx) {
        new RecipeStore(ctx).registerAll(); // las recetas importadas, igual que al abrir la app
        LocationStore store = new LocationStore(ctx);
        boolean retry = false;
        LocalDateTime now = LocalDateTime.now();
        String hora = Horario.hhmm(now.getHour() * 60 + now.getMinute());
        for (Location l : store.all()) {
            if (!applyTimers(ctx, store, l)) retry = true;
            List<Horario> rules = store.schedules(l);
            LocalDateTime last = Horario.lastChange(rules, now);
            if (last == null || store.scheduleApplied(l) >= ms(last)) continue; // nada nuevo que aplicar
            RouterAdapter adapter = AdapterRegistry.get(l.adapterId);
            if (!adapter.capabilities().contains(Capability.BLOCK) || !store.hasPassword(l)) continue;
            final Map<String, Boolean> want = Horario.desired(rules, now);
            final Map<String, NetDevice> known = new HashMap<>();
            for (NetDevice d : store.knownDevices(l)) known.put(d.mac, d);
            try {
                final List<NetDevice>[] listed = new List[1];
                int changed = new RouterEngine(adapter, store.credentials(l)).run(ops -> {
                    // Se lista una sola vez: tras guardar un cambio, el ZTE no deja listar en la misma sesión.
                    listed[0] = ops.listarDispositivos();
                    for (NetDevice d : listed[0]) known.put(d.mac, d);
                    int n = 0;
                    for (Map.Entry<String, Boolean> w : want.entrySet()) {
                        NetDevice d = known.get(w.getKey());
                        if (d == null) continue; // nunca visto en este router: no hay con qué cortarlo
                        if (w.getValue()) ops.bloquearDispositivo(d); else ops.desbloquearDispositivo(d);
                        n++;
                    }
                    return n;
                });
                if (listed[0] != null) store.rememberDevices(l, listed[0], System.currentTimeMillis());
                store.setScheduleApplied(l, System.currentTimeMillis());
                store.setScheduleResult(l, ctx.getString(R.string.sched_result_ok, hora, changed));
            } catch (Exception e) {
                retry = true;
                store.setScheduleResult(l, ctx.getString(R.string.sched_result_fail, hora, String.valueOf(e.getMessage())));
            }
        }
        return retry;
    }

    /**
     * Devuelve Internet a los aparatos cuya pausa con temporizador ha terminado. Devuelve false si
     * no se ha podido (p. ej. el móvil no está en casa) y hay que reintentar.
     */
    private static boolean applyTimers(Context ctx, LocationStore store, Location l) {
        long nowMs = System.currentTimeMillis();
        final java.util.Set<String> due = new java.util.HashSet<>();
        for (Map.Entry<String, Long> t : store.timers(l).entrySet()) if (t.getValue() <= nowMs) due.add(t.getKey());
        if (due.isEmpty()) return true;
        RouterAdapter adapter = AdapterRegistry.get(l.adapterId);
        if (!adapter.capabilities().contains(Capability.UNBLOCK) || !store.hasPassword(l)) {
            store.clearTimers(l, due);
            return true;
        }
        final Map<String, NetDevice> known = new HashMap<>();
        for (NetDevice d : store.knownDevices(l)) known.put(d.mac, d);
        LocalDateTime now = LocalDateTime.now();
        String hora = Horario.hhmm(now.getHour() * 60 + now.getMinute());
        try {
            final List<NetDevice>[] listed = new List[1];
            int n = new RouterEngine(adapter, store.credentials(l)).run(ops -> {
                listed[0] = ops.listarDispositivos();
                for (NetDevice d : listed[0]) known.put(d.mac, d);
                int c = 0;
                for (String mac : due) {
                    NetDevice d = known.get(mac);
                    if (d == null) d = NetDevice.ofMac(mac);
                    ops.desbloquearDispositivo(d);
                    c++;
                }
                return c;
            });
            if (listed[0] != null) store.rememberDevices(l, listed[0], System.currentTimeMillis());
            store.clearTimers(l, due);
            store.setScheduleResult(l, ctx.getString(R.string.timer_result_ok, hora, n));
            return true;
        } catch (Exception e) {
            store.setScheduleResult(l, ctx.getString(R.string.timer_result_fail, hora, String.valueOf(e.getMessage())));
            return false;
        }
    }

    /** Trabajo completo: aplicar, reprogramar y, si hace falta, reintentar más tarde. */
    static void runAll(Context ctx) {
        boolean retry;
        try {
            retry = applyPending(ctx);
        } catch (Exception e) {
            retry = true;
        }
        reprogram(ctx);
        if (retry) scheduleRetry(ctx);
    }
}
