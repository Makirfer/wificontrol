package com.makirfer.wifitimecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CancellationSignal;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Bloqueo opcional de la app con un PIN y, si el móvil lo permite, con la huella o la cara.
 * El PIN no se guarda: solo su huella SHA-256 con sal. Se pide al abrir la app y al volver a ella
 * tras más de un minuto fuera. Los horarios y avisos siguen funcionando con la app bloqueada.
 */
final class AppLock {

    private static final String PREFS = "bloqueo";
    private static final long GRACE_MS = 60_000;

    private final Activity a;
    private final SharedPreferences p;
    private boolean unlocked;
    private long leftAt;
    private AlertDialog lockDialog;

    AppLock(Activity a) {
        this.a = a;
        this.p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean enabled() { return !p.getString("pin", "").isEmpty(); }

    boolean biometric() { return enabled() && p.getBoolean("bio", false); }

    static boolean biometricAvailable(Context c) {
        if (Build.VERSION.SDK_INT < 28) return false;
        if (Build.VERSION.SDK_INT >= 29) {
            android.hardware.biometrics.BiometricManager bm =
                    (android.hardware.biometrics.BiometricManager) c.getSystemService(Context.BIOMETRIC_SERVICE);
            return bm != null && bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
        }
        return c.getPackageManager().hasSystemFeature("android.hardware.fingerprint");
    }

    private String t(int id) { return a.getString(id); }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static String hash(String salt, String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean check(String pin) {
        return hash(p.getString("salt", ""), pin).equals(p.getString("pin", ""));
    }

    // ------------------------------------------------------------------ al abrir / volver

    void onStop() { if (unlocked) leftAt = System.currentTimeMillis(); }

    /** Pide el desbloqueo si hace falta; llama a {@code then} cuando la app queda abierta. */
    void requireUnlock(Runnable then) {
        if (!enabled()) { unlocked = true; then.run(); return; }
        if (unlocked && System.currentTimeMillis() - leftAt < GRACE_MS) { then.run(); return; }
        unlocked = false;
        if (lockDialog != null && lockDialog.isShowing()) return;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText pin = pinField(t(R.string.lock_pin_hint));
        box.addView(pin);
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(t(R.string.lock_title))
                .setView(box)
                .setCancelable(false)
                .setPositiveButton(t(R.string.lock_open), null)
                .setNegativeButton(t(R.string.exit), (d, w) -> a.finish());
        if (biometric() && biometricAvailable(a)) {
            b.setNeutralButton(t(R.string.lock_use_bio), null);
        }
        lockDialog = b.create();
        lockDialog.setOnShowListener(d -> {
            lockDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (check(pin.getText().toString())) {
                    unlock(then);
                } else {
                    pin.setText("");
                    Toast.makeText(a, t(R.string.lock_wrong), Toast.LENGTH_SHORT).show();
                }
            });
            android.widget.Button bio = lockDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (bio != null) bio.setOnClickListener(v -> askBiometric(then));
        });
        lockDialog.show();
        if (biometric() && biometricAvailable(a)) askBiometric(then);
    }

    private void unlock(Runnable then) {
        unlocked = true;
        if (lockDialog != null) lockDialog.dismiss();
        then.run();
    }

    private void askBiometric(Runnable then) {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(a)
                    .setTitle(t(R.string.lock_title))
                    .setSubtitle(t(R.string.app_name))
                    .setNegativeButton(t(R.string.lock_use_pin), a.getMainExecutor(), (d, w) -> { })
                    .build();
            prompt.authenticate(new CancellationSignal(), a.getMainExecutor(),
                    new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                            unlock(then);
                        }
                    });
        } catch (Exception ignored) {
            // sin huella disponible: queda el PIN
        }
    }

    // ------------------------------------------------------------------ configurar

    /** Ventana para activar, cambiar o quitar el bloqueo. */
    void showSetup(final Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView info = new TextView(a);
        info.setText(t(R.string.lock_info));
        box.addView(info);
        final EditText pin1 = pinField(t(R.string.lock_new_pin));
        final EditText pin2 = pinField(t(R.string.lock_repeat_pin));
        box.addView(pin1);
        box.addView(pin2);
        final CheckBox bio = new CheckBox(a);
        bio.setText(t(R.string.lock_bio_option));
        bio.setChecked(p.getBoolean("bio", true));
        if (biometricAvailable(a)) box.addView(bio);
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(t(R.string.lock_setup_title))
                .setView(box)
                .setPositiveButton(t(R.string.save), (d, w) -> {
                    String x = pin1.getText().toString();
                    if (x.length() < 4 || !x.equals(pin2.getText().toString())) {
                        Toast.makeText(a, t(R.string.lock_pin_mismatch), Toast.LENGTH_LONG).show();
                        showSetup(done);
                        return;
                    }
                    byte[] s = new byte[16];
                    new SecureRandom().nextBytes(s);
                    StringBuilder salt = new StringBuilder();
                    for (byte c : s) salt.append(String.format("%02x", c));
                    p.edit().putString("salt", salt.toString()).putString("pin", hash(salt.toString(), x))
                            .putBoolean("bio", biometricAvailable(a) && bio.isChecked()).apply();
                    unlocked = true;
                    Toast.makeText(a, t(R.string.lock_saved), Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                })
                .setNegativeButton(t(R.string.cancel), (d, w) -> { if (done != null) done.run(); });
        if (enabled()) {
            b.setNeutralButton(t(R.string.lock_remove), (d, w) -> {
                p.edit().clear().apply();
                Toast.makeText(a, t(R.string.lock_removed), Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            });
        }
        b.setCancelable(false).show();
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(22);
        return e;
    }
}
