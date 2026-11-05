package com.leira.wificasa;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.leira.wificasa.core.Horario;
import com.leira.wificasa.core.NetDevice;
import com.leira.wificasa.data.Location;
import com.leira.wificasa.data.LocationStore;

import java.text.DateFormatSymbols;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pantallas de los horarios: lista de horarios de la ubicación y editor de cada uno. */
final class HorarioUi {

    /** Lo que los horarios necesitan de la pantalla principal. */
    interface Host {
        Location location();
        /** Aparatos conocidos de la ubicación (los del router y los recordados). */
        List<NetDevice> devices();
        String name(NetDevice d);
        /** Corta (o devuelve) Internet ahora mismo a estos aparatos. */
        void applyNow(Set<String> macs, boolean block);
    }

    private static final int C_MUTED = Color.parseColor("#6B665E");
    private static final int C_CHIP = Color.parseColor("#E6E1D8");
    private static final int C_ACCENT = Color.parseColor("#E8590C");

    private final Activity a;
    private final LocationStore store;
    private final Host host;

    HorarioUi(Activity a, LocationStore store, Host host) {
        this.a = a;
        this.store = store;
        this.host = host;
    }

    private String t(int id, Object... x) { return x.length == 0 ? a.getString(id) : a.getString(id, x); }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    /** Nombres cortos de los días, de lunes a domingo, en el idioma del móvil. */
    private static String[] dayNames() {
        String[] w = DateFormatSymbols.getInstance().getShortWeekdays(); // índice Calendar.SUNDAY=1…
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) out[i] = w[i == 6 ? Calendar.SUNDAY : Calendar.MONDAY + i].replace(".", "");
        return out;
    }

    String summary(Horario h) {
        String[] dn = dayNames();
        List<String> ds = new ArrayList<>();
        for (int i = 0; i < 7; i++) if (h.hasDay(i)) ds.add(dn[i]);
        String days = h.days == 0b1111111 ? t(R.string.sched_every_day)
                : h.days == 0b0011111 ? t(R.string.sched_weekdays)
                : h.days == 0b1100000 ? t(R.string.sched_weekend) : String.join(" ", ds);
        String time = h.start == h.end ? t(R.string.sched_all_day) : Horario.hhmm(h.start) + "–" + Horario.hhmm(h.end);
        String n = a.getResources().getQuantityString(R.plurals.sched_devices, h.macs.size(), h.macs.size());
        return (h.name.isEmpty() ? "" : h.name + " · ") + days + " " + time + " · " + n
                + (h.enabled ? "" : " · " + t(R.string.sched_off));
    }

    // ------------------------------------------------------------------ lista

    void showList() {
        final Location loc = host.location();
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView info = new TextView(a);
        info.setText(t(R.string.sched_info));
        info.setTextColor(C_MUTED);
        box.addView(info);

        String res = store.scheduleResult(loc);
        if (!res.isEmpty()) {
            TextView last = new TextView(a);
            last.setText(res);
            last.setTextColor(C_MUTED);
            last.setPadding(0, dp(8), 0, 0);
            box.addView(last);
        }

        if (!HorarioRunner.exactAllowed(a) && Build.VERSION.SDK_INT >= 31) {
            Button exact = chip(t(R.string.sched_allow_exact));
            exact.setOnClickListener(v -> {
                try {
                    a.startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                            Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {
                    // ajuste no disponible en este móvil
                }
            });
            box.addView(exact);
        }

        for (final Horario h : store.schedules(loc)) {
            Button row = chip((h.enabled ? "⏰ " : "⏸ ") + summary(h));
            row.setOnClickListener(v -> { dlg[0].dismiss(); edit(h); });
            box.addView(row);
        }
        Button add = chip(t(R.string.sched_new));
        add.setTextColor(C_ACCENT);
        add.setOnClickListener(v -> { dlg[0].dismiss(); edit(new Horario()); });
        box.addView(add);

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(a)
                .setTitle(t(R.string.sched_title, loc.name))
                .setView(sv)
                .setPositiveButton(t(R.string.close), null)
                .show();
    }

    private Button chip(String text) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_CHIP);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------------ editor

    private void edit(final Horario h) {
        final Location loc = host.location();
        final boolean isNew = h.id.isEmpty();
        final Set<String> macs = new LinkedHashSet<>(h.macs);
        final int[] time = {h.start, h.end};

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText name = new EditText(a);
        name.setHint(t(R.string.sched_name_hint));
        name.setText(h.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name);

        final Button devices = new Button(a);
        devices.setAllCaps(false);
        devices.setText(devicesText(macs));
        devices.setOnClickListener(v -> pickDevices(macs, devices));
        box.addView(devices);

        LinearLayout daysRow = new LinearLayout(a);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        final CheckBox[] dayBoxes = new CheckBox[7];
        String[] dn = dayNames();
        for (int i = 0; i < 7; i++) {
            CheckBox c = new CheckBox(a);
            c.setText(dn[i].substring(0, Math.min(2, dn[i].length())));
            c.setChecked(h.hasDay(i));
            c.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            dayBoxes[i] = c;
            daysRow.addView(c);
        }
        TextView daysLabel = new TextView(a);
        daysLabel.setText(t(R.string.sched_days));
        daysLabel.setTextColor(C_MUTED);
        daysLabel.setPadding(0, dp(10), 0, 0);
        box.addView(daysLabel);
        box.addView(daysRow);

        LinearLayout times = new LinearLayout(a);
        times.setOrientation(LinearLayout.HORIZONTAL);
        final Button from = new Button(a), to = new Button(a);
        from.setAllCaps(false);
        to.setAllCaps(false);
        from.setText(t(R.string.sched_from, Horario.hhmm(time[0])));
        to.setText(t(R.string.sched_to, Horario.hhmm(time[1])));
        from.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        to.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        boolean is24 = android.text.format.DateFormat.is24HourFormat(a);
        from.setOnClickListener(v -> new TimePickerDialog(a, (tp, hh, mm) -> {
            time[0] = hh * 60 + mm;
            from.setText(t(R.string.sched_from, Horario.hhmm(time[0])));
        }, time[0] / 60, time[0] % 60, is24).show());
        to.setOnClickListener(v -> new TimePickerDialog(a, (tp, hh, mm) -> {
            time[1] = hh * 60 + mm;
            to.setText(t(R.string.sched_to, Horario.hhmm(time[1])));
        }, time[1] / 60, time[1] % 60, is24).show());
        times.addView(from);
        times.addView(to);
        box.addView(times);

        TextView note = new TextView(a);
        note.setText(t(R.string.sched_time_note));
        note.setTextColor(C_MUTED);
        note.setTextSize(12);
        box.addView(note);

        final CheckBox enabled = new CheckBox(a);
        enabled.setText(t(R.string.sched_enabled));
        enabled.setChecked(h.enabled);
        box.addView(enabled);

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(t(isNew ? R.string.sched_new_title : R.string.sched_edit_title))
                .setView(sv)
                .setPositiveButton(t(R.string.save), (d, w) -> {
                    int days = 0;
                    for (int i = 0; i < 7; i++) if (dayBoxes[i].isChecked()) days |= 1 << i;
                    if (macs.isEmpty() || days == 0) {
                        Toast.makeText(a, t(R.string.sched_need_devices_days), Toast.LENGTH_LONG).show();
                        h.name = name.getText().toString().trim();
                        h.macs = macs;
                        h.days = days == 0 ? h.days : days;
                        h.start = time[0];
                        h.end = time[1];
                        edit(h);
                        return;
                    }
                    Set<String> before = new HashSet<>(h.macs);
                    boolean wasActive = !isNew && h.activeAt(LocalDateTime.now());
                    h.name = name.getText().toString().trim();
                    h.macs = macs;
                    h.days = days;
                    h.start = time[0];
                    h.end = time[1];
                    h.enabled = enabled.isChecked();
                    store.saveSchedule(loc, h);
                    afterChange(loc, h.activeAt(LocalDateTime.now()) ? h.macs : new HashSet<String>(),
                            wasActive ? before : new HashSet<String>());
                    Toast.makeText(a, t(R.string.sched_saved), Toast.LENGTH_LONG).show();
                    showList();
                })
                .setNegativeButton(t(R.string.cancel), (d, w) -> showList());
        if (!isNew) {
            b.setNeutralButton(t(R.string.delete), (d, w) -> {
                boolean wasActive = h.activeAt(LocalDateTime.now());
                store.deleteSchedule(loc, h.id);
                afterChange(loc, new HashSet<String>(), wasActive ? h.macs : new HashSet<String>());
                showList();
            });
        }
        b.show();
    }

    /**
     * Tras guardar o borrar: lo que debe estar cortado ahora se corta ya, y lo que el horario
     * tenía cortado y ya no debe estarlo se devuelve. El resto espera al siguiente cambio.
     */
    private void afterChange(Location loc, Set<String> blockNow, Set<String> wereBlocked) {
        store.setScheduleApplied(loc, System.currentTimeMillis());
        Map<String, Boolean> want = Horario.desired(store.schedules(loc), LocalDateTime.now());
        Set<String> unblock = new HashSet<>();
        for (String m : wereBlocked) if (!Boolean.TRUE.equals(want.get(m)) && !blockNow.contains(m)) unblock.add(m);
        if (!blockNow.isEmpty()) host.applyNow(blockNow, true);
        else if (!unblock.isEmpty()) host.applyNow(unblock, false);
        final android.content.Context ctx = a.getApplicationContext();
        new Thread(() -> HorarioRunner.reprogram(ctx)).start();
    }

    private String devicesText(Set<String> macs) {
        if (macs.isEmpty()) return t(R.string.sched_pick_devices);
        List<String> names = new ArrayList<>();
        for (NetDevice d : host.devices()) if (macs.contains(d.mac)) names.add(host.name(d));
        return "📱 " + (names.isEmpty() ? String.valueOf(macs.size()) : String.join(", ", names)) + "  ▾";
    }

    private void pickDevices(final Set<String> macs, final Button label) {
        final List<NetDevice> list = host.devices();
        if (list.isEmpty()) {
            Toast.makeText(a, t(R.string.sched_no_devices), Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[list.size()];
        boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            items[i] = host.name(list.get(i));
            checked[i] = macs.contains(list.get(i).mac);
        }
        new AlertDialog.Builder(a)
                .setTitle(t(R.string.sched_devices_title))
                .setMultiChoiceItems(items, checked, (d, w, on) -> {
                    if (on) macs.add(list.get(w).mac); else macs.remove(list.get(w).mac);
                })
                .setPositiveButton(t(R.string.ok), (d, w) -> label.setText(devicesText(macs)))
                .show();
    }
}
