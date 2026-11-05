package com.leira.wificasa;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.leira.wificasa.core.AdapterRegistry;
import com.leira.wificasa.core.Capability;
import com.leira.wificasa.core.Horario;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.core.RouterAdapter;
import com.leira.wificasa.core.RouterEngine;
import com.leira.wificasa.data.Location;
import com.leira.wificasa.data.LocationStore;
import com.leira.wificasa.data.RecipeStore;

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
 * se respeta hasta el siguiente cambio.
 */
final class HorarioRunner {

    static final String ACTION_ALARM = "com.leira.wificasa.HORARIO";
    private static final int JOB_RETRY = 71;
    private static final long RETRY_MS = 5 * 60 * 1000L;

    private HorarioRunner() {}

    private static long ms(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    /** Programa la alarma del próximo cambio de cualquier ubicación (o la quita si no hay horarios). */
    static void reprogram(Context ctx) {
        LocationStore store = new LocationStore(ctx);
        LocalDateTime now = LocalDateTime.now(), next = null;
        for (Location l : store.all()) {
            LocalDateTime n = Horario.nextChange(store.schedules(l), now);
            if (n != null && (next == null || n.isBefore(next))) next = n;
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, HorarioReceiver.class).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (next == null) { am.cancel(pi); return; }
        long at = ms(next) + 5000; // unos segundos después del minuto exacto
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
