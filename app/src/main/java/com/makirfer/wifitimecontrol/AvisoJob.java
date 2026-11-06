package com.makirfer.wifitimecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.makirfer.wifitimecontrol.core.AdapterRegistry;
import com.makirfer.wifitimecontrol.core.NetDevice;
import com.makirfer.wifitimecontrol.core.RouterAdapter;
import com.makirfer.wifitimecontrol.core.RouterEngine;
import com.makirfer.wifitimecontrol.data.Location;
import com.makirfer.wifitimecontrol.data.LocationStore;
import com.makirfer.wifitimecontrol.data.RecipeStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Avisos de conexión: cada 15 minutos (lo mínimo que Android permite en segundo plano) el móvil
 * consulta el router y avisa de los aparatos vigilados que se han conectado desde la última vez.
 * Solo funciona con el móvil en el WiFi de esa ubicación; fuera, la consulta falla sin avisar.
 */
public class AvisoJob extends JobService {

    private static final int JOB_ID = 72;
    private static final String CHANNEL = "conexiones";

    /** Programa (o quita) la comprobación periódica según haya aparatos vigilados. */
    static void reprogram(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        LocationStore store = new LocationStore(ctx);
        boolean any = false;
        for (Location l : store.all()) if (!store.notifySet(l).isEmpty() || store.notifyNew(l)) any = true;
        if (!any) { js.cancel(JOB_ID); return; }
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(ctx, AvisoJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                check(getApplicationContext());
            } catch (Exception ignored) {
                // se volverá a intentar en la próxima comprobación
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }

    static void check(Context ctx) {
        new RecipeStore(ctx).registerAll();
        LocationStore store = new LocationStore(ctx);
        for (Location l : store.all()) {
            Set<String> watch = store.notifySet(l);
            boolean watchNew = store.notifyNew(l);
            if ((watch.isEmpty() && !watchNew) || !store.hasPassword(l)) continue;
            RouterAdapter adapter = AdapterRegistry.get(l.adapterId);
            List<NetDevice> list;
            try {
                list = new RouterEngine(adapter, store.credentials(l)).run(ops -> ops.listarDispositivos());
            } catch (Exception e) {
                continue; // fuera de casa o router ocupado: sin avisos esta vez
            }
            if (watchNew) {
                Set<String> before = new HashSet<>();
                for (NetDevice d : store.knownDevices(l)) before.add(d.mac);
                // Con la lista vacía (primera consulta) no se avisa: todos serían «nuevos».
                if (!before.isEmpty()) {
                    for (NetDevice d : list) {
                        if (!d.mac.isEmpty() && !before.contains(d.mac)) notifyNew(ctx, l, d);
                    }
                }
            }
            store.rememberDevices(l, list, System.currentTimeMillis());
            Set<String> seen = store.notifySeen(l), checked = store.notifyChecked(l), nowOn = new HashSet<>();
            for (NetDevice d : list) {
                if (!watch.contains(d.mac) || !d.active) continue;
                nowOn.add(d.mac);
                // Solo se avisa si antes estaba desconectado (y no la primera vez que se comprueba).
                if (checked.contains(d.mac) && !seen.contains(d.mac)) notify(ctx, l, d, store.alias(l, d.mac));
            }
            checked.retainAll(watch);
            checked.addAll(watch);
            store.setNotifyState(l, nowOn, checked);
        }
    }

    private static void notifyNew(Context ctx, Location l, NetDevice d) {
        String name = !d.hostName.isEmpty() ? d.hostName + " · " + d.mac : d.mac;
        post(ctx, l, (l.id + "new" + d.mac).hashCode(), ctx.getString(R.string.notif_new_title, l.name),
                ctx.getString(R.string.notif_new_text, name));
    }

    private static void notify(Context ctx, Location l, NetDevice d, String alias) {
        String name = !alias.isEmpty() ? alias : !d.hostName.isEmpty() ? d.hostName : d.mac;
        String hora = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        post(ctx, l, (l.id + d.mac).hashCode(), ctx.getString(R.string.notif_title, name),
                ctx.getString(R.string.notif_text, l.name, hora));
    }

    private static void post(Context ctx, Location l, int id, String title, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(id, n);
    }
}
