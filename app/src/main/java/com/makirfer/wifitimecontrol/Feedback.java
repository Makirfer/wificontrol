package com.makirfer.wifitimecontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

/**
 * Enviar comentarios e informes de error con un toque, por la app que elija el usuario
 * (WhatsApp, correo…). Nunca se incluyen contraseñas ni la lista de aparatos de casa: solo la
 * versión de la app, el modelo del móvil y el router.
 */
final class Feedback {

    /** Correo de la app. Mientras esté vacío, el usuario elige a quién mandarlo. */
    static final String EMAIL = "";

    private Feedback() { }

    static String version(Activity a) {
        try {
            android.content.pm.PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** «WiFi Time Control 3.3.0 · Android 14 · samsung SM-A546B · Router: ZTE ZXHN F6640P». */
    static String deviceInfo(Activity a, String router) {
        return a.getString(R.string.app_name) + " " + version(a) + " · Android " + Build.VERSION.RELEASE
                + " · " + Build.MANUFACTURER + " " + Build.MODEL
                + (router == null || router.isEmpty() ? "" : " · Router: " + router);
    }

    static void send(Activity a, String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        if (!EMAIL.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{EMAIL});
        try {
            a.startActivity(Intent.createChooser(i, a.getString(R.string.send_report)));
        } catch (Exception e) {
            android.widget.Toast.makeText(a, String.valueOf(e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
