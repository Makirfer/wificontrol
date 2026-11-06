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
    private Button scheduleBtn;

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
        loc = store.current();
        if (loc == null) {
            loc = store.create(t(R.string.loc_default));
            store.setCurrent(loc.id);
            onLocationChanged();
            showSettings(true);
        } else {
            onLocationChanged();
            if (!store.hasPassword(loc)) showSettings(true); else refresh();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (scanner.onActivityResult(requestCode, resultCode, data)) return;
        flows.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ interfaz

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

        setContentView(scroll);
    }

    private void setBusy(boolean b, String msg) {
        busy = b;
        if (msg != null) statusView.setText(msg);
        studyOnBtn.setEnabled(!b);
        studyOffBtn.setEnabled(!b);
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
        groupInfoView.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        scheduleBtn.setVisibility(canSwitch ? View.VISIBLE : View.GONE);
        String g = store.groupName(loc);
        studyOnBtn.setText(g.isEmpty() ? t(R.string.pause) : t(R.string.pause_group, g));
        studyOffBtn.setText(g.isEmpty() ? t(R.string.resume) : t(R.string.resume_group, g));
        hintView.setText(canSwitch
                ? t(R.string.hint_can_switch)
                : t(R.string.hint_list_only));
        // Se muestran enseguida los dispositivos recordados; Actualizar trae el estado real.
        devices = store.knownDevices(loc);
        present = new java.util.HashSet<>();
        statusView.setText(devices.isEmpty() ? adapter().displayName()
                : t(R.string.last_known));
        renderList();
    }

    // ------------------------------------------------------------------ datos

    private RouterAdapter adapter() { return AdapterRegistry.get(loc.adapterId); }

    private boolean canSwitch() {
        Set<Capability> c = adapter().capabilities();
        return c.contains(Capability.BLOCK) && c.contains(Capability.UNBLOCK);
    }

    private String alias(NetDevice d) { return store.alias(loc, d.mac); }

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
                // Lo más habitual: el móvil no está en el WiFi de esa ubicación y responde otro router.
                result = "⚠ " + e.getMessage() + "\n" + t(R.string.check_wifi, jobLoc.name);
            }
            final String msg = result;
            final List<NetDevice> list = fresh;
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
                if (msg != null && msg.startsWith("⚠")) {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
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
        runJob(t(block ? R.string.cutting_one : R.string.restoring_one, displayName(target)), true, ops -> {
            NetDevice d = ops.buscar(target.mac);
            // Si ya no aparece en la lista, se intenta igualmente con los datos que teníamos.
            if (d == null) d = target;
            if (block) ops.bloquearDispositivo(d); else ops.desbloquearDispositivo(d);
            return t(block ? R.string.result_off : R.string.result_on, displayName(d), nowText());
        });
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
        name.setText((marked ? "⏸ " : "") + displayName(d));
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(C_TEXT);
        texts.addView(name);

        TextView sub = new TextView(this);
        String host = alias(d).isEmpty() ? "" : d.hostName + " · ";
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

        final CheckBox study = new CheckBox(this);
        String g = store.groupName(loc);
        study.setText(g.isEmpty() ? t(R.string.include_pause) : t(R.string.include_pause_named, g));
        study.setChecked(store.studySet(loc).contains(d.mac));
        if (canSwitch()) box.addView(study);

        final CheckBox notify = new CheckBox(this);
        notify.setText(t(R.string.notify_on_connect));
        notify.setChecked(store.notifySet(loc).contains(d.mac));
        box.addView(notify);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(t(R.string.device))
                .setView(box)
                .setPositiveButton(t(R.string.save), (dialog, which) -> {
                    store.saveDevice(loc, d.mac, name.getText().toString().trim(), study.isChecked());
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
        b.show();
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
                .setView(box)
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
        model.setText(AdapterRegistry.get(editing.adapterId).displayName() + "  ▾");
        model.setOnClickListener(v -> {
            String[] names = new String[adapters.size() + 1];
            for (int i = 0; i < adapters.size(); i++) names[i] = adapters.get(i).displayName();
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
        }));
        box.addView(photo);

        final CheckBox notifyNew = new CheckBox(this);
        notifyNew.setText(t(R.string.notify_new_devices));
        notifyNew.setChecked(store.notifyNew(editing));
        box.addView(notifyNew);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(firstTime ? t(R.string.setup_router) : t(R.string.settings_of, editing.name))
                .setView(box)
                .setPositiveButton(t(R.string.save), (dialog, which) -> {
                    RouterAdapter a = AdapterRegistry.get(editing.adapterId);
                    String n = name.getText().toString().trim();
                    editing.name = n.isEmpty() ? t(R.string.loc_default) : n;
                    String h = host.getText().toString().trim();
                    editing.host = h.isEmpty() ? a.defaultHost() : h;
                    String u = user.getText().toString().trim();
                    editing.user = u.isEmpty() ? a.defaultUser() : u;
                    String p = pass.getText().toString();
                    try {
                        store.save(editing, p.isEmpty() && hasPass ? null : p);
                    } catch (Exception e) {
                        Toast.makeText(this, t(R.string.password_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.setGroupName(editing, group.getText().toString());
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
