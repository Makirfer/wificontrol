import com.leira.wificasa.core.Horario;
import java.time.LocalDateTime;
import java.util.*;

/** Horarios: franjas normales, que cruzan la medianoche, día entero y cálculo de cambios. */
public class PruebaHorario {
    static int f = 0;
    static void ok(boolean c, String w) { System.out.println((c ? "  OK   " : "  FALLO ") + w); if (!c) f++; }
    static LocalDateTime t(String s) { return LocalDateTime.parse(s); }

    public static void main(String[] a) {
        Horario noche = new Horario();           // lunes a viernes, 21:00–08:00
        noche.id = "n"; noche.macs.add("AA");
        // 2026-11-02 es lunes
        ok(!noche.activeAt(t("2026-11-02T20:59")), "lunes 20:59 con Internet");
        ok(noche.activeAt(t("2026-11-02T21:00")), "lunes 21:00 sin Internet");
        ok(noche.activeAt(t("2026-11-03T07:59")), "martes 07:59 sigue cortado (viene del lunes)");
        ok(!noche.activeAt(t("2026-11-03T08:00")), "martes 08:00 vuelve Internet");
        ok(noche.activeAt(t("2026-11-07T07:00")), "sábado 07:00 cortado (viene del viernes)");
        ok(!noche.activeAt(t("2026-11-07T21:30")), "sábado 21:30 con Internet (no es día del horario)");
        ok(!noche.activeAt(t("2026-11-09T07:00")), "lunes 07:00 con Internet (el domingo no hay horario)");

        Horario tarde = new Horario();            // sábado y domingo 16:00–18:00
        tarde.id = "t"; tarde.macs.add("BB"); tarde.days = 0b1100000; tarde.start = 16 * 60; tarde.end = 18 * 60;
        ok(tarde.activeAt(t("2026-11-08T17:00")) && !tarde.activeAt(t("2026-11-06T17:00")), "fin de semana 16–18");

        Horario dia = new Horario();              // miércoles todo el día
        dia.id = "d"; dia.macs.add("CC"); dia.days = 0b0000100; dia.start = dia.end = 0;
        ok(dia.activeAt(t("2026-11-04T00:00")) && dia.activeAt(t("2026-11-04T23:59"))
                && !dia.activeAt(t("2026-11-05T00:00")), "misma hora de inicio y fin = día entero");

        Horario off = new Horario();
        off.id = "o"; off.macs.add("AA"); off.enabled = false; off.days = 0b1111111; off.start = 0; off.end = 0;
        List<Horario> rules = Arrays.asList(noche, tarde, dia, off);

        Map<String, Boolean> w = Horario.desired(rules, t("2026-11-02T22:00"));
        ok(w.get("AA") && !w.get("BB") && !w.get("CC"), "estado deseado: solo AA cortado el lunes 22:00 (el desactivado no cuenta)");

        ok(t("2026-11-02T21:00").equals(Horario.nextChange(rules, t("2026-11-02T15:30"))), "próximo cambio lunes 21:00");
        ok(t("2026-11-03T08:00").equals(Horario.nextChange(rules, t("2026-11-02T21:00"))), "después, martes 08:00");
        ok(t("2026-11-02T21:00").equals(Horario.lastChange(rules, t("2026-11-02T23:10"))), "último cambio lunes 21:00");
        ok(t("2026-11-04T00:00").equals(Horario.lastChange(rules, t("2026-11-04T05:00"))), "miércoles 05:00: último cambio 00:00 (empieza el día entero)");

        Horario r = Horario.decode("x", noche.encode());
        ok(r != null && r.macs.equals(noche.macs) && r.start == 21 * 60 && r.end == 8 * 60 && r.days == noche.days && r.enabled,
                "se guarda y se lee igual");
        ok(Horario.nextChange(Collections.<Horario>emptyList(), t("2026-11-02T10:00")) == null, "sin horarios, sin alarma");

        System.out.println(f == 0 ? "TODO CORRECTO" : f + " FALLOS");
        System.exit(f == 0 ? 0 : 1);
    }
}
