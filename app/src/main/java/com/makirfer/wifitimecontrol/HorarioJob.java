package com.makirfer.wifitimecontrol;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Reintento de un horario que no se pudo aplicar (p. ej. el móvil no estaba en casa). */
public class HorarioJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            HorarioRunner.runAll(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // si el sistema lo corta, que lo repita
    }
}
