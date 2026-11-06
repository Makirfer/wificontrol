package com.makirfer.wifitimecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Despierta a la hora de cada cambio de horario, y también al encender el móvil o al actualizar
 * la app (las alarmas se borran en ambos casos y hay que volver a programarlas).
 */
public class HorarioReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context ctx = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                HorarioRunner.runAll(ctx);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
