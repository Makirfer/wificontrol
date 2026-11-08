package com.makirfer.wifitimecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.makirfer.wifitimecontrol.core.AccessState;
import com.makirfer.wifitimecontrol.core.AdapterRegistry;
import com.makirfer.wifitimecontrol.core.Capability;
import com.makirfer.wifitimecontrol.core.DeviceType;
import com.makirfer.wifitimecontrol.core.Oui;
import com.makirfer.wifitimecontrol.core.NetDevice;
import com.makirfer.wifitimecontrol.core.RouterAdapter;
import com.makirfer.wifitimecontrol.core.RouterCredentials;
import com.makirfer.wifitimecontrol.core.RouterEngine;
import com.makirfer.wifitimecontrol.core.SessionExpiredException;
import com.makirfer.wifitimecontrol.data.Location;
import com.makirfer.wifitimecontrol.data.LocationStore;
import com.makirfer.wifitimecontrol.data.RecipeStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal. Solo se ocupa de la interfaz: el acceso al router pasa por
 * {@link RouterEngine} y el adaptador de cada ubicación; los datos, por {@link LocationStore}.
 */
public class MainActivity extends Activity {

    private static final int C_BG = Color.parseColor("#F4F1EC");
    private static final int C_CARD = Color.WHITE;
    private static final int C_TEXT = Color.parseColor("#1F1D1A");
    private static final int C_MUTED = Color.parseColor("#6B665E");
    private static final int C_RED = Color.parseColor("#C0392B");
    private static final int C_GREEN = Color.parseColor("#2E7D4F");
    private static final int C_ACCENT = Color.parseColor("#E8590C");
    private static final int C_CHIP = Color.parseColor("#E6E1D8");

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LocationStore store;
    private Location loc;
    private LinearLayout listBox;
    private TextView statusView, hintView, groupInfoView;
    private Button locationBtn, studyOnBtn, studyOffBtn, refreshBtn;
    private List<NetDevice> devices = new ArrayList<>();
    /** MAC de los dispositivos que el router ha listado en la última consulta correcta. */
    private Set<String> present = new java.util.HashSet<>();
    private boolean busy = false;
    private RecipeFlows flows;
    private HorarioUi horarios;
    private PegatinaScanner scanner;
    private AppLock lock;
    private boolean resumedOnce;
    private static final int REQ_WELCOME = 56;
    private Button scheduleBtn, timerBtn;
    private Oui oui;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecipeStore recipeStore = new RecipeStore(this);
        recipeStore.registerAll();
        store = new LocationStore(this);
        scanner = new PegatinaScanner(this);
        flows = new RecipeFlows(this, new RecipeFlows.Host() {
            @Override public PegatinaScanner scanner() { return scanner; }

            @Override
            public RouterCredentials credentials() {
                return loc != null && store.hasPassword(loc) ? store.credentials(loc) : null;
            }

            @Override
            public void useAdapter(String adapterId) {
                Location l = store.get(loc.id);
                l.adapterId = adapterId;
                try {
                    store.save(l, null);
                } catch (Exception ignored) {
                    // sin cambio de contraseña: no puede fallar el cifrado
                }
                switchTo(l);
                refresh();
            }

            @Override public String currentAdapterId() { return loc == null ? "" : loc.adapterId; }

            @Override public String savedHost() { return loc == null ? "" : loc.host; }

            @Override public String savedUser() { return loc == null ? "" : loc.user; }

            @Override public boolean hasPassword() { return loc != null && store.hasPassword(loc); }

            @Override
            public void saveCredentials(String h, String u, String p) throws Exception {
                Location l = store.get(loc.id);
                l.host = h;
                l.user = u;
                store.save(l, p.isEmpty() ? null : p);
                loc = l;
            }
        }, worker, recipeStore);
        horarios = new HorarioUi(this, store, new HorarioUi.Host() {
            @Override public Location location() { return loc; }
            @Override public List<NetDevice> devices() { return devices; }
            @Override public String name(NetDevice d) { return displayName(d); }
            @Override public void applyNow(Set<String> macs, boolean block) { applyMacs(macs, block); }
        });
        // Las alarmas de los horarios se pierden al reinstalar: se vuelven a programar.
        final android.content.Context app = getApplicationContext();
        worker.execute(() -> { HorarioRunner.reprogram(app); AvisoJob.reprogram(app); });
        buildUi();
        lock = new AppLock(this);
        loc = store.current();
        if (loc == null) {
            loc = store.create(t(R.string.loc_default));
            store.setCurrent(loc.id);
        }
        onLocationChanged();
        hideContent(true);
        lock.requireUnlock(() -> {
            hideContent(false);
            if (!getSharedPreferences("bienvenida", MODE_PRIVATE).getBoolean("vista", false)) {
                // Primera vez: fichas de bienvenida antes de configurar el router.
                startActivityForResult(new Intent(this, GuideActivity.class)
                        .putExtra(GuideActivity.EXTRA_WELCOME, true), REQ_WELCOME);
            } else {
                firstRun();
            }
        });
    }

    private void firstRun() {
        if (!store.hasPassword(loc)) showSettings(true); else refresh();
    }

    /** Con la app bloqueada no se enseña la lista de aparatos detrás del PIN. */
    private void hideContent(boolean hide) {
        View c = findViewById(android.R.id.content);
        if (c != null) c.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!resumedOnce) { resumedOnce = true; return; } // el primer desbloqueo lo pide onCreate
        if (lock.enabled()) {
            hideContent(true);
            lock.requireUnlock(() -> hideContent(false));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        lock.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (scanner.onActivityResult(requestCode, resultCode, data)) return;
        if (requestCode == REQ_WELCOME) {
            getSharedPreferences("bienvenida", MODE_PRIVATE).edit().putBoolean("vista", true).apply();
            boolean wantsLock = data != null
                    && GuideActivity.ACTION_LOCK.equals(data.getStringExtra(GuideActivity.RESULT_ACTION));
            if (wantsLock) lock.showSetup(this::firstRun); else firstRun();
            return;
        }
        flows.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ interfaz

    /** Nombre del router para mostrar (el de demostración, traducido). */
    private String adapterName(RouterAdapter a) {
        return com.makirfer.wifitimecontrol.adapters.demo.DemoAdapter.ID.equals(a.id()) ? t(R.string.demo_name) : a.displayName();
    }

    /** Texto traducido (res/values/strings.xml y sus traducciones). */
    private String t(int id, Object... a) { return a.length == 0 ? getString(id) : getString(id, a); }

    private String q(int id, int n, Object... a) { return getResources().getQuantityString(id, n, a); }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button bigButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(color, 14));
        b.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(C_TEXT);
        b.setBackground(rounded(C_CHIP, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(t(R.string.app_name));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        locationBtn = new Button(this);
        locationBtn.setAllCaps(false);
        locationBtn.setTextColor(C_TEXT);
        locationBtn.setBackground(rounded(C_CHIP, 20));
        locationBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        locationBtn.setOnClickListener(v -> { if (!busy) chooseLocation(); });
        header.addView(locationBtn);
        root.addView(header);

        statusView = new TextView(this);
        statusView.setTextColor(C_MUTED);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(4), 0, dp(6));
        root.addView(statusView);

        studyOnBtn = bigButton(t(R.string.pause), C_RED);
        studyOnBtn.setOnClickListener(v -> applyStudy(true));
        root.addView(studyOnBtn);

        timerBtn = new Button(this);
        timerBtn.setAllCaps(false);
        timerBtn.setText(t(R.string.timer_group_button));
        timerBtn.setTextColor(C_RED);
        timerBtn.setBackground(rounded(Color.TRANSPARENT, 10));
        timerBtn.setOnClickListener(v -> { if (!busy) askTimer(null); });
        root.addView(timerBtn);

        studyOffBtn = bigButton(t(R.string.resume), C_GREEN);
        studyOffBtn.setOnClickListener(v -> applyStudy(false));
        root.addView(studyOffBtn);

        groupInfoView = new TextView(this);
        groupInfoView.setTextColor(C_MUTED);
        groupInfoView.setTextSize(13);
        groupInfoView.setGravity(Gravity.CENTER_HORIZONTAL);
        groupInfoView.setPadding(0, dp(6), 0, 0);
        root.addView(groupInfoView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(14), 0, dp(6));
        refreshBtn = smallButton(t(R.string.refresh));
        refreshBtn.setOnClickListener(v -> refresh());
        Button settingsBtn = smallButton(t(R.string.settings));
        settingsBtn.setOnClickListener(v -> { if (!busy) showSettings(false); });
        scheduleBtn = smallButton(t(R.string.schedules));
        scheduleBtn.setOnClickListener(v -> { if (!busy) horarios.showList(); });
        row.addView(refreshBtn);
        row.addView(scheduleBtn);
        row.addView(settingsBtn);
        root.addView(row);

        hintView = new TextView(this);
        hintView.setTextColor(C_MUTED);
        hintView.setTextSize(13);
        hintView.setPadding(dp(2), dp(6), dp(2), dp(4));
        root.addView(hintView);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        // Siempre a mano, sobre todo durante la prueba: escribir qué falla o qué falta.
        TextView feedbackLink = new TextView(this);
        feedbackLink.setText(t(R.string.feedback_button));
        feedbackLink.setTextColor(C_ACCENT);
        feedbackLink.setTextSize(15);
        feedbackLink.setGravity(Gravity.CENTER_HORIZONTAL);
        feedbackLink.setPadding(0, dp(24), 0, dp(8));
        feedbackLink.setOnClickListener(v -> askFeedback(adapterName(adapter())));
        root.addView(feedbackLink);

        setContentView(scroll);
    }

    private void setBusy(boolean b, String msg) {
        busy = b;
        if (msg != null) statusView.setText(msg);
        studyOnBtn.setEnabled(!b);
        studyOffBtn.setEnabled(!b);
        timerBtn.setEnabled(!b);
        refreshBtn.setEnabled(!b);
        locationBtn.setEnabled(!b);
        studyOnBtn.setAlpha(b ? 0.5f : 1f);
        studyOffBtn.setAlpha(b ? 0.5f : 1f);
        for (int i = 0; i < listBox.getChildCount(); i++) {
            listBox.getChildAt(i).setEnabled(!b);
        }
    }

    /** Ajusta la pantalla a la ubicación actual y a lo que su router permite. */
    private void onLocationChanged() {
        locationBtn.setText("📍 " + loc.name + "  ▾");
        boolean canSwitch = canSwitch();
        studyOnBtn.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        studyOffBtn.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        timerBtn.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        groupInfoView.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        scheduleBtn.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        String g = store.groupName(loc);
        studyOnBtn.setText(g.isEmpty() ? t(R.string.pause) : t(R.string.pause_group, g));
        studyOffBtn.setText(g.isEmpty() ? t(R.string.resume) : t(R.string.resume_group, g));
        hintView.setText(isDemo(loc.adapterId) ? t(R.string.demo_banner)
                : canSwitch ? t(R.string.hint_can_switch)
                : t(R.string.hint_list_only));
        // Se muestran enseguida los dispositivos recordados; Actualizar trae el estado real.
        devices = store.knownDevices(loc);
        present = new java.util.HashSet<>();
        statusView.setText(devices.isEmpty() ? adapterName(adapter())
                : t(R.string.last_known));
        renderList();
    }

    // ------------------------------------------------------------------ datos

    private RouterAdapter adapter() { return AdapterRegistry.get(loc.adapterId); }

    private static boolean isDemo(String adapterId) {
        return com.makirfer.wifitimecontrol.adapters.demo.DemoAdapter.ID.equals(adapterId);
    }

    private boolean canSwitch() {
        Set<Capability> c = adapter().capabilities();
        return c.contains(Capability.BLOCK) && c.contains(Capability.UNBLOCK);
    }

    private String alias(NetDevice d) { return store.alias(loc, d.mac); }

    /** Lista de marcas por MAC (assets/oui.txt); se carga la primera vez que hace falta. */
    private Oui oui() {
        if (oui == null) {
            try (java.io.InputStream in = getAssets().open("oui.txt")) {
                oui = new Oui(in);
            } catch (Exception e) {
                try { oui = new Oui(new java.io.ByteArrayInputStream(new byte[0])); } catch (Exception ignored) { }
            }
        }
        return oui;
    }

    /** Tipo elegido a mano o, si no, el deducido del nombre puesto, el del router y la marca. */
    private String typeOf(NetDevice d) {
        String manual = store.deviceType(loc, d.mac);
        return manual.isEmpty() ? autoType(d) : manual;
    }

    private String autoType(NetDevice d) {
        String byAlias = DeviceType.fromName(alias(d));
        if (!DeviceType.UNKNOWN.equals(byAlias)) return byAlias;
        return DeviceType.guess(d.hostName, oui().hint(d.mac), d.mac);
    }

    private String typeName(String type) {
        switch (type) {
            case DeviceType.PHONE: return t(R.string.type_phone);
            case DeviceType.TABLET: return t(R.string.type_tablet);
            case DeviceType.COMPUTER: return t(R.string.type_computer);
            case DeviceType.CONSOLE: return t(R.string.type_console);
            case DeviceType.TV: return t(R.string.type_tv);
            case DeviceType.SPEAKER: return t(R.string.type_speaker);
            case DeviceType.PRINTER: return t(R.string.type_printer);
            case DeviceType.WATCH: return t(R.string.type_watch);
            case DeviceType.CAMERA: return t(R.string.type_camera);
            case DeviceType.SMART: return t(R.string.type_smart);
            case DeviceType.NETWORK: return t(R.string.type_network);
            default: return t(R.string.type_unknown);
        }
    }

    /** «Apple», o «dirección privada» si el aparato oculta su MAC real. */
    private String brandText(NetDevice d) {
        if (DeviceType.privateMac(d.mac)) return t(R.string.private_mac);
        return oui().brand(d.mac);
    }

    private String displayName(NetDevice d) {
        String a = alias(d);
        return a.isEmpty() ? (d.hostName.isEmpty() ? d.mac : d.hostName) : a;
    }

    // ------------------------------------------------------------------ acciones

    /**
     * Ejecuta un trabajo en segundo plano y después lee el estado de todos los dispositivos.
     *
     * Si el trabajo cambia algo en el router ({@code writes}), la lista se lee en una sesión
     * NUEVA: el ZTE responde "SessionTimeout" al listar en la misma sesión después de guardar
     * un cambio. Si solo falla esa lectura final, el cambio ya está hecho y así se indica.
     */
    private void runJob(String workingMsg, boolean writes, RouterEngine.Job<String> job) {
        if (busy) return;
        setBusy(true, workingMsg);
        final Location jobLoc = loc;
        final RouterEngine engine = new RouterEngine(adapter(), store.credentials(jobLoc));
        worker.execute(() -> {
            String result;
            List<NetDevice> fresh = null;
            Exception failure = null;
            try {
                if (writes) {
                    result = engine.run(job);
                    try {
                        fresh = engine.run(ops -> ops.listarConEstado());
                    } catch (Exception e) {
                        result += "\n" + t(R.string.done_no_refresh);
                    }
                } else {
                    final List<NetDevice> holder = new ArrayList<>();
                    result = engine.run(ops -> {
                        String r = job.run(ops);
                        holder.addAll(ops.listarConEstado());
                        return r;
                    });
                    fresh = holder;
                }
            } catch (Exception e) {
                failure = e;
                // Lo más habitual: el móvil no está en el WiFi de esa ubicación y responde otro router.
                result = "⚠ " + e.getMessage() + "\n" + t(R.string.check_wifi, jobLoc.name);
            }
            final String msg = result;
            final List<NetDevice> list = fresh;
            final Exception failed = failure;
            final boolean changed = failed != null && looksChanged(failed) && store.lastOk(jobLoc) > 0
                    && !com.makirfer.wifitimecontrol.adapters.demo.DemoAdapter.ID.equals(jobLoc.adapterId);
            if (failed == null) store.setLastOk(jobLoc, System.currentTimeMillis());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (list != null) store.rememberDevices(jobLoc, list, System.currentTimeMillis());
                if (list != null && jobLoc.id.equals(loc.id)) {
                    present = new java.util.HashSet<>();
                    for (NetDevice d : list) present.add(d.mac);
                    devices = merge(list, store.knownDevices(jobLoc));
                }
                setBusy(false, msg);
                renderList();
                if (changed) {
                    routerChanged(jobLoc, failed);
                } else if (msg != null && msg.startsWith("⚠")) {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * ¿El router ha respondido, pero no como antes? (Si no responde, lo normal es que el móvil no
     * esté en casa: eso no es un cambio del router.)
     */
    private static boolean looksChanged(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.io.IOException) return false;
        }
        String m = String.valueOf(e.getMessage());
        return !m.contains("no permite") && !m.contains("se ha reintentado") && !m.contains("bloqueado el acceso");
    }

    /** Aviso claro cuando una receta que funcionaba deja de hacerlo. */
    private void routerChanged(final Location l, final Exception e) {
        final String router = adapterName(AdapterRegistry.get(l.adapterId));
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.changed_title))
                .setMessage(t(R.string.changed_msg, String.valueOf(e.getMessage())))
                .setPositiveButton(t(R.string.send_report), (d, w) -> Feedback.send(this,
                        t(R.string.report_subject, router),
                        "Error: " + e.getMessage() + "\n\n" + Feedback.deviceInfo(this, router + " (" + l.adapterId + ")")))
                .setNeutralButton(t(R.string.relearn), (d, w) -> flows.showAddRouterMenu())
                .setNegativeButton(t(R.string.close), null)
                .show();
    }

    private void refresh() {
        runJob(t(R.string.connecting, loc.name), false, ops -> t(R.string.updated_at, nowText()));
    }

    private void applyStudy(boolean block) {
        final Set<String> study = store.studySet(loc);
        if (study.isEmpty()) {
            Toast.makeText(this, t(R.string.need_marked), Toast.LENGTH_LONG).show();
            return;
        }
        if (!block) clearTimers(study);
        runJob(block ? t(R.string.cutting_marked) : t(R.string.restoring), true, ops -> {
            int n = 0;
            List<String> errors = new ArrayList<>();
            final String[] firstError = new String[1];
            // Se lista una sola vez: tras guardar un cambio, el ZTE no deja listar en la misma sesión.
            Set<String> listed = new java.util.HashSet<>();
            for (NetDevice d : ops.listarDispositivos()) {
                listed.add(d.mac);
                if (!study.contains(d.mac)) continue;
                try {
                    if (block) ops.bloquearDispositivo(d); else ops.desbloquearDispositivo(d);
                    n++;
                } catch (SessionExpiredException e) {
                    throw e; // el motor inicia sesión de nuevo y repite toda la pausa
                } catch (Exception e) {
                    errors.add(displayName(d));
                    if (firstError[0] == null) firstError[0] = e.getMessage();
                }
            }
            if (!errors.isEmpty()) {
                throw new Exception(t(R.string.could_not_change, String.join(", ", errors), firstError[0]));
            }
            if (n == 0) return t(R.string.none_marked_present);
            int skipped = 0;
            for (String mac : study) if (!listed.contains(mac)) skipped++;
            return q(block ? R.plurals.paused : R.plurals.resumed, n, n, nowText())
                    + (skipped > 0 ? "\n" + q(R.plurals.skipped, skipped, skipped) : "");
        });
    }

    /** Corta o devuelve Internet a varios aparatos (lo usan los horarios al guardarlos). */
    private void applyMacs(final Set<String> macs, final boolean block) {
        if (!block) clearTimers(macs);
        final List<NetDevice> knownList = new ArrayList<>(devices);
        runJob(block ? t(R.string.cutting_marked) : t(R.string.restoring), true, ops -> {
            java.util.Map<String, NetDevice> byMac = new java.util.HashMap<>();
            for (NetDevice d : knownList) byMac.put(d.mac, d);
            for (NetDevice d : ops.listarDispositivos()) byMac.put(d.mac, d);
            int n = 0;
            for (String mac : macs) {
                NetDevice d = byMac.get(mac);
                if (d == null) continue;
                if (block) ops.bloquearDispositivo(d); else ops.desbloquearDispositivo(d);
                n++;
            }
            return q(block ? R.plurals.paused : R.plurals.resumed, n, n, nowText());
        });
    }

    private void toggleDevice(NetDevice target, boolean block) {
        if (!block) clearTimers(java.util.Collections.singleton(target.mac));
        runJob(t(block ? R.string.cutting_one : R.string.restoring_one, displayName(target)), true, ops -> {
            NetDevice d = ops.buscar(target.mac);
            // Si ya no aparece en la lista, se intenta igualmente con los datos que teníamos.
            if (d == null) d = target;
            if (block) ops.bloquearDispositivo(d); else ops.desbloquearDispositivo(d);
            return t(block ? R.string.result_off : R.string.result_on, displayName(d), nowText());
        });
    }

    // ------------------------------------------------------------------ pausa con temporizador

    private void clearTimers(Set<String> macs) {
        store.clearTimers(loc, macs);
        final android.content.Context app = getApplicationContext();
        worker.execute(() -> HorarioRunner.reprogram(app));
    }

    /**
     * «¿Cuánto tiempo?» y pausa: el grupo ({@code d} null) o un aparato. Internet vuelve sola a la
     * hora elegida (la devuelve el móvil, igual que los horarios).
     */
    private void askTimer(final NetDevice d) {
        final Set<String> macs = d == null ? store.studySet(loc) : java.util.Collections.singleton(d.mac);
        if (macs.isEmpty()) {
            Toast.makeText(this, t(R.string.need_marked), Toast.LENGTH_LONG).show();
            return;
        }
        final int[] minutes = {15, 30, 60, 120, -1};
        String[] items = {t(R.string.timer_15), t(R.string.timer_30), t(R.string.timer_60), t(R.string.timer_120),
                t(R.string.timer_tomorrow)};
        new AlertDialog.Builder(this)
                .setTitle(d == null ? t(R.string.timer_title_group) : t(R.string.timer_title_one, displayName(d)))
                .setItems(items, (dlg, w) -> {
                    long at;
                    if (minutes[w] > 0) {
                        at = System.currentTimeMillis() + minutes[w] * 60_000L;
                    } else {
                        java.time.LocalDateTime t8 = java.time.LocalDate.now().plusDays(1).atTime(8, 0);
                        at = t8.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    }
                    final Set<String> fixed = new java.util.HashSet<>(macs);
                    store.setTimer(loc, fixed, at);
                    final android.content.Context app = getApplicationContext();
                    worker.execute(() -> HorarioRunner.reprogram(app));
                    if (!HorarioRunner.exactAllowed(this)) Toast.makeText(this, t(R.string.timer_inexact), Toast.LENGTH_LONG).show();
                    if (d == null) applyStudy(true); else toggleDevice(d, true);
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private String hhmm(long ms) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                ms - System.currentTimeMillis() > 20 * 3600_000L ? "EEE HH:mm" : "HH:mm", java.util.Locale.getDefault());
        return f.format(new java.util.Date(ms));
    }

    /** Lista del router + los recordados que el router ya no muestra. */
    private static List<NetDevice> merge(List<NetDevice> fromRouter, List<NetDevice> known) {
        List<NetDevice> out = new ArrayList<>(fromRouter);
        Set<String> macs = new java.util.HashSet<>();
        for (NetDevice d : fromRouter) macs.add(d.mac);
        for (NetDevice d : known) if (!macs.contains(d.mac)) out.add(d);
        return out;
    }

    private boolean isPresent(NetDevice d) { return present.contains(d.mac); }

    private String lastSeenText(NetDevice d) {
        if (d.lastSeenMs <= 0) return t(R.string.no_data);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault());
        return t(R.string.last_seen, f.format(new java.util.Date(d.lastSeenMs)));
    }

    private String nowText() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return f.format(new java.util.Date());
    }

    // ------------------------------------------------------------------ lista

    private void renderList() {
        listBox.removeAllViews();
        List<NetDevice> sorted = new ArrayList<>(devices);
        final Set<String> study = store.studySet(loc);
        int nStudy = study.size();
        groupInfoView.setText(nStudy == 0 ? t(R.string.group_empty) : q(R.plurals.affects, nStudy, nStudy));
        Collections.sort(sorted, new Comparator<NetDevice>() {
            @Override
            public int compare(NetDevice a, NetDevice b) {
                int sa = study.contains(a.mac) ? 0 : 1, sb = study.contains(b.mac) ? 0 : 1;
                if (sa != sb) return sa - sb;
                int pa = isPresent(a) ? 0 : 1, pb = isPresent(b) ? 0 : 1;
                if (pa != pb) return pa - pb;
                int aa = a.active ? 0 : 1, ab = b.active ? 0 : 1;
                if (aa != ab) return aa - ab;
                return displayName(a).compareToIgnoreCase(displayName(b));
            }
        });
        if (sorted.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(t(R.string.empty_list, loc.name));
            empty.setTextColor(C_MUTED);
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            listBox.addView(empty);
            return;
        }
        boolean canSwitch = canSwitch();
        for (NetDevice d : sorted) listBox.addView(deviceCard(d, study.contains(d.mac), canSwitch));
    }

    private View deviceCard(final NetDevice d, boolean marked, boolean canSwitch) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(10), dp(12));
        GradientDrawable bg = rounded(C_CARD, 14);
        if (marked) bg.setStroke(dp(2), C_ACCENT);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(DeviceType.icon(typeOf(d)) + "  " + (marked ? "⏸ " : "") + displayName(d));
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(C_TEXT);
        texts.addView(name);

        TextView sub = new TextView(this);
        String host = (alias(d).isEmpty() || d.hostName.isEmpty() ? "" : d.hostName + " · ");
        String brand = brandText(d);
        if (!brand.isEmpty()) host += brand + " · ";
        if (isPresent(d)) {
            sub.setText(host + d.ip + " · " + t(d.active ? R.string.connected : R.string.not_connected));
        } else {
            sub.setText(host + (d.ip.isEmpty() ? "" : d.ip + " · ") + lastSeenText(d));
        }
        sub.setTextSize(12);
        sub.setTextColor(C_MUTED);
        texts.addView(sub);

        TextView state = new TextView(this);
        boolean blocked = d.isBlocked();
        if (d.access == AccessState.SCHEDULED) {
            state.setText(t(R.string.state_scheduled));
            state.setTextColor(C_ACCENT);
        } else if (d.access == AccessState.UNKNOWN) {
            state.setText(t(R.string.state_unknown));
            state.setTextColor(C_MUTED);
        } else {
            state.setText(t(blocked ? R.string.state_off : R.string.state_on));
            state.setTextColor(blocked ? C_RED : C_GREEN);
        }
        if (!isPresent(d) && d.access != AccessState.UNKNOWN) {
            state.setText(t(R.string.last_state, state.getText()));
            state.setAlpha(0.7f);
        }
        Long back = store.timers(loc).get(d.mac);
        if (back != null && canSwitch) {
            state.setText(state.getText() + "  ·  " + t(R.string.timer_back, hhmm(back)));
        }
        state.setTextSize(14);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setPadding(0, dp(4), 0, 0);
        // En routers que solo dejan ver aparatos, «Estado desconocido» no aporta nada: se muestra
        // si se le vigila, que es lo que sí se puede hacer.
        boolean viewOnly = !canSwitch && !adapter().capabilities().contains(Capability.READ_ACCESS);
        if (viewOnly) {
            if (store.notifySet(loc).contains(d.mac)) {
                state.setText(t(R.string.notify_watching));
                state.setTextColor(C_ACCENT);
                texts.addView(state);
            }
        } else {
            texts.addView(state);
        }

        card.addView(texts);

        if (canSwitch) {
            Switch sw = new Switch(this);
            sw.setChecked(!blocked);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (busy) {
                        buttonView.setOnCheckedChangeListener(null);
                        buttonView.setChecked(!isChecked);
                        buttonView.setOnCheckedChangeListener(this);
                        return;
                    }
                    toggleDevice(d, !isChecked);
                }
            });
            card.addView(sw);
        }

        card.setOnClickListener(v -> { if (!busy) editDevice(d); });
        return card;
    }

    // ------------------------------------------------------------------ diálogos

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        return box;
    }

    /** Las ventanas largas (Ajustes) se desplazan: si no, en móviles pequeños se cortan por abajo. */
    private View scrolled(LinearLayout box) {
        ScrollView sv = new ScrollView(this);
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_MUTED);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private void editDevice(final NetDevice d) {
        LinearLayout box = dialogBox();

        TextView info = new TextView(this);
        info.setText(t(R.string.device_info, d.hostName.isEmpty() ? "—" : d.hostName, d.ip, d.mac)
                + (isPresent(d) ? "" : "\n" + t(R.string.not_shown_now, lastSeenText(d))));
        info.setTextColor(C_MUTED);
        box.addView(info);

        final EditText name = new EditText(this);
        name.setHint(t(R.string.device_name_hint));
        name.setText(alias(d));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name);

        final String[] chosenType = {store.deviceType(loc, d.mac)};
        final Button typeBtn = new Button(this);
        typeBtn.setAllCaps(false);
        final Runnable showType = () -> {
            String ty = chosenType[0].isEmpty() ? autoType(d) : chosenType[0];
            typeBtn.setText(DeviceType.icon(ty) + "  " + (chosenType[0].isEmpty()
                    ? t(R.string.type_probable, typeName(ty)) : typeName(ty)) + "  ▾");
        };
        showType.run();
        typeBtn.setOnClickListener(v -> {
            String[] items = new String[DeviceType.ALL.length + 1];
            items[0] = t(R.string.type_auto);
            for (int i = 0; i < DeviceType.ALL.length; i++) {
                items[i + 1] = DeviceType.icon(DeviceType.ALL[i]) + "  " + typeName(DeviceType.ALL[i]);
            }
            new AlertDialog.Builder(this).setTitle(t(R.string.type_title))
                    .setItems(items, (dlg, w) -> {
                        chosenType[0] = w == 0 ? "" : DeviceType.ALL[w - 1];
                        showType.run();
                    }).show();
        });
        box.addView(label(t(R.string.type_title)));
        box.addView(typeBtn);

        final CheckBox study = new CheckBox(this);
        String g = store.groupName(loc);
        study.setText(g.isEmpty() ? t(R.string.include_pause) : t(R.string.include_pause_named, g));
        study.setChecked(store.studySet(loc).contains(d.mac));
        if (canSwitch()) box.addView(study);

        final CheckBox notify = new CheckBox(this);
        notify.setText(t(R.string.notify_on_connect));
        notify.setChecked(store.notifySet(loc).contains(d.mac));
        box.addView(notify);

        final AlertDialog[] self = new AlertDialog[1];
        if (canSwitch()) {
            Button timer = new Button(this);
            timer.setAllCaps(false);
            timer.setText(t(R.string.timer_one_button));
            timer.setOnClickListener(v -> {
                if (self[0] != null) self[0].dismiss();
                askTimer(d);
            });
            box.addView(timer);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(t(R.string.device))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.save), (dialog, which) -> {
                    store.saveDevice(loc, d.mac, name.getText().toString().trim(), study.isChecked());
                    store.setDeviceType(loc, d.mac, chosenType[0]);
                    boolean wasOn = store.notifySet(loc).contains(d.mac);
                    store.setNotify(loc, d.mac, notify.isChecked());
                    if (notify.isChecked() != wasOn) {
                        final android.content.Context app = getApplicationContext();
                        worker.execute(() -> AvisoJob.reprogram(app));
                        if (notify.isChecked()) {
                            askNotificationPermission();
                            Toast.makeText(this, t(R.string.notify_info, loc.name), Toast.LENGTH_LONG).show();
                        }
                    }
                    renderList();
                })
                .setNegativeButton(t(R.string.cancel), null);
        if (!isPresent(d)) {
            // Solo se pueden olvidar los que el router no está mostrando ahora.
            b.setNeutralButton(t(R.string.forget), (dialog, which) -> {
                store.forgetDevice(loc, d.mac);
                List<NetDevice> rest = new ArrayList<>();
                for (NetDevice x : devices) if (!x.mac.equals(d.mac)) rest.add(x);
                devices = rest;
                renderList();
            });
        }
        self[0] = b.show();
    }

    /** Android 13+ pide permiso para enseñar notificaciones. */
    private void askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 61);
        }
    }

    /** Elegir ubicación (casa, segunda residencia…) o crear una nueva. */
    private void chooseLocation() {
        final List<Location> all = store.all();
        String[] items = new String[all.size() + 1];
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            items[i] = all.get(i).name;
            if (all.get(i).id.equals(loc.id)) checked = i;
        }
        items[all.size()] = t(R.string.new_location_item);
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.location))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == all.size()) {
                        askNewLocation();
                    } else {
                        switchTo(all.get(which));
                        if (store.hasPassword(loc)) refresh(); else showSettings(true);
                    }
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void askNewLocation() {
        LinearLayout box = dialogBox();
        final EditText name = new EditText(this);
        name.setHint(t(R.string.new_location_hint));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name);
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.new_location))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.create), (dialog, which) -> {
                    String n = name.getText().toString().trim();
                    switchTo(store.create(n.isEmpty() ? t(R.string.loc_numbered, store.all().size() + 1) : n));
                    showSettings(true);
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void switchTo(Location l) {
        loc = l;
        store.setCurrent(l.id);
        onLocationChanged();
    }

    private void showSettings(final boolean firstTime) {
        final Location editing = store.get(loc.id);
        LinearLayout box = dialogBox();

        TextView info = new TextView(this);
        info.setText(t(R.string.settings_info));
        info.setTextColor(C_MUTED);
        box.addView(info);

        box.addView(label(t(R.string.label_location_name)));
        final EditText name = new EditText(this);
        name.setText(editing.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name);

        box.addView(label(t(R.string.label_router_model)));
        final List<RouterAdapter> adapters = AdapterRegistry.all();
        final AlertDialog[] settingsDlg = new AlertDialog[1];
        final Button model = new Button(this);
        model.setAllCaps(false);
        model.setText(adapterName(AdapterRegistry.get(editing.adapterId)) + "  ▾");
        model.setOnClickListener(v -> {
            String[] names = new String[adapters.size() + 1];
            for (int i = 0; i < adapters.size(); i++) names[i] = adapterName(adapters.get(i));
            names[adapters.size()] = t(R.string.router_not_listed);
            new AlertDialog.Builder(this).setTitle(t(R.string.label_router_model))
                    .setItems(names, (dlg, which) -> {
                        if (which == adapters.size()) {
                            if (settingsDlg[0] != null) settingsDlg[0].dismiss();
                            flows.showAddRouterMenu();
                            return;
                        }
                        editing.adapterId = adapters.get(which).id();
                        model.setText(names[which] + "  ▾");
                        if (isDemo(editing.adapterId)) {
                            new AlertDialog.Builder(this).setTitle(t(R.string.demo_name))
                                    .setMessage(t(R.string.demo_explain)).setPositiveButton(t(R.string.ok), null).show();
                        }
                    }).show();
        });
        box.addView(model);

        box.addView(label(t(R.string.label_group)));
        final EditText group = new EditText(this);
        group.setText(store.groupName(editing));
        group.setHint(t(R.string.group_hint));
        group.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(group);

        box.addView(label(t(R.string.label_host)));
        final EditText host = new EditText(this);
        host.setText(editing.host);
        host.setHint(AdapterRegistry.get(editing.adapterId).defaultHost());
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(host);

        box.addView(label(t(R.string.label_user)));
        final EditText user = new EditText(this);
        user.setText(editing.user);
        user.setHint(AdapterRegistry.get(editing.adapterId).defaultUser());
        user.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(user);

        box.addView(label(t(R.string.label_password)));
        final EditText pass = new EditText(this);
        final boolean hasPass = store.hasPassword(editing);
        pass.setHint(hasPass ? t(R.string.password_saved_hint) : t(R.string.password));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass);

        box.addView(label(t(R.string.label_wifi)));
        final EditText wifiName = new EditText(this);
        wifiName.setText(store.wifiName(editing));
        wifiName.setHint(t(R.string.photo_wifi_name));
        wifiName.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(wifiName);
        final EditText wifiKey = new EditText(this);
        final String oldWifiKey = store.wifiKey(editing);
        wifiKey.setText(oldWifiKey);
        wifiKey.setHint(t(R.string.photo_wifi_key));
        wifiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(wifiKey);

        Button photo = new Button(this);
        photo.setAllCaps(false);
        photo.setText(t(R.string.photo_button));
        photo.setOnClickListener(v -> scanner.scan(p -> {
            if (!p.host.isEmpty()) host.setText(p.host);
            if (!p.user.isEmpty()) user.setText(p.user);
            if (!p.password.isEmpty()) {
                pass.setText(p.password);
                pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
            if (!p.wifiName.isEmpty()) wifiName.setText(p.wifiName);
            if (!p.wifiKey.isEmpty()) {
                wifiKey.setText(p.wifiKey);
                wifiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
            Toast.makeText(this, t(R.string.photo_filled), Toast.LENGTH_LONG).show();
        }));
        box.addView(photo, indexAfter(box, model)); // arriba, junto al modelo: se ve sin desplazar

        Button lockBtn = new Button(this);
        lockBtn.setAllCaps(false);
        lockBtn.setText(t(lock.enabled() ? R.string.lock_change : R.string.lock_button));
        lockBtn.setOnClickListener(v -> lock.showSetup(null));
        box.addView(lockBtn);

        final CheckBox notifyNew = new CheckBox(this);
        notifyNew.setText(t(R.string.notify_new_devices));
        notifyNew.setChecked(store.notifyNew(editing));
        box.addView(notifyNew);

        Button feedback = new Button(this);
        feedback.setAllCaps(false);
        feedback.setText(t(R.string.feedback_button));
        feedback.setOnClickListener(v -> askFeedback(adapterName(AdapterRegistry.get(editing.adapterId))));
        box.addView(feedback);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(firstTime ? t(R.string.setup_router) : t(R.string.settings_of, editing.name))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.save), (dialog, which) -> {
                    RouterAdapter a = AdapterRegistry.get(editing.adapterId);
                    String n = name.getText().toString().trim();
                    editing.name = n.isEmpty() ? t(R.string.loc_default) : n;
                    String h = host.getText().toString().trim();
                    editing.host = h.isEmpty() ? a.defaultHost() : h;
                    String u = user.getText().toString().trim();
                    editing.user = u.isEmpty() ? a.defaultUser() : u;
                    String p = pass.getText().toString();
                    // El router de demostración no tiene contraseña: se guarda una cualquiera.
                    if (p.isEmpty() && !hasPass && com.makirfer.wifitimecontrol.adapters.demo.DemoAdapter.ID.equals(editing.adapterId)) p = "demo";
                    try {
                        store.save(editing, p.isEmpty() && hasPass ? null : p);
                    } catch (Exception e) {
                        Toast.makeText(this, t(R.string.password_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.setGroupName(editing, group.getText().toString());
                    try {
                        store.setWifi(editing, wifiName.getText().toString(), wifiKey.getText().toString());
                    } catch (Exception e) {
                        Toast.makeText(this, t(R.string.password_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                    if (notifyNew.isChecked() != store.notifyNew(editing)) {
                        store.setNotifyNew(editing, notifyNew.isChecked());
                        final android.content.Context app = getApplicationContext();
                        worker.execute(() -> AvisoJob.reprogram(app));
                        if (notifyNew.isChecked()) {
                            askNotificationPermission();
                            Toast.makeText(this, t(R.string.notify_new_info, editing.name), Toast.LENGTH_LONG).show();
                        }
                    }
                    switchTo(editing);
                    refresh();
                });
        if (!firstTime) b.setNegativeButton(t(R.string.cancel), null);
        if (store.all().size() > 1) {
            b.setNeutralButton(t(R.string.delete), (dialog, which) -> confirmDelete(editing));
        }
        b.setCancelable(!firstTime);
        settingsDlg[0] = b.show();
    }

    private static int indexAfter(LinearLayout box, View v) {
        for (int i = 0; i < box.getChildCount(); i++) if (box.getChildAt(i) == v) return i + 1;
        return box.getChildCount();
    }

    /** «Enviar comentario»: texto libre + versión de la app, móvil y router (nada más). */
    private void askFeedback(final String router) {
        LinearLayout box = dialogBox();
        TextView info = new TextView(this);
        info.setText(t(R.string.feedback_info));
        info.setTextColor(C_MUTED);
        box.addView(info);
        final EditText text = new EditText(this);
        text.setHint(t(R.string.feedback_hint));
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setMinLines(4);
        text.setGravity(Gravity.TOP | Gravity.START);
        box.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.feedback_title))
                .setView(scrolled(box))
                .setPositiveButton(t(R.string.feedback_send), (d, w) -> Feedback.send(this, t(R.string.feedback_subject),
                        text.getText().toString().trim() + "\n\n— " + Feedback.deviceInfo(this, router)))
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }

    private void confirmDelete(final Location l) {
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.delete_location, l.name))
                .setMessage(t(R.string.delete_location_msg))
                .setPositiveButton(t(R.string.delete), (dialog, which) -> {
                    store.delete(l.id);
                    switchTo(store.current());
                    if (store.hasPassword(loc)) refresh();
                })
                .setNegativeButton(t(R.string.cancel), null)
                .show();
    }
}
