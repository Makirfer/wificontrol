package com.makirfer.wifitimecontrol.core.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector JSON mínimo (Java puro, sin dependencias) para las recetas.
 * Devuelve Map (con orden), List, String, Double, Boolean o null.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) throws RecipeException {
        Json j = new Json(text);
        j.ws();
        Object v = j.value(0);
        j.ws();
        if (j.i != j.s.length()) throw j.err("contenido inesperado después del JSON");
        return v;
    }

    /** Escribe un valor (Map, List, String, Number, Boolean o null) como JSON compacto. */
    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { quote(sb, (String) v); return; }
        if (v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
            return;
        }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object o : (List<?>) v) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
            return;
        }
        quote(sb, v.toString());
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }

    private RecipeException err(String msg) {
        return new RecipeException("JSON no válido (posición " + i + "): " + msg);
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value(int depth) throws RecipeException {
        if (depth > 64) throw err("demasiado anidado");
        if (i >= s.length()) throw err("fin inesperado");
        char c = s.charAt(i);
        if (c == '{') return object(depth);
        if (c == '[') return array(depth);
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw err("valor no reconocido");
    }

    private Map<String, Object> object(int depth) throws RecipeException {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw err("se esperaba una clave");
            String k = string();
            if (m.containsKey(k)) throw err("clave repetida \"" + k + "\"");
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("se esperaba ':'");
            i++;
            ws();
            m.put(k, value(depth + 1));
            ws();
            if (i >= s.length()) throw err("fin inesperado");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("se esperaba ',' o '}'");
        }
    }

    private List<Object> array(int depth) throws RecipeException {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            if (i >= s.length()) throw err("fin inesperado");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("se esperaba ',' o ']'");
        }
    }

    private String string() throws RecipeException {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw err("escape \\u no válido");
                        }
                        i += 4;
                        break;
                    default: throw err("escape no válido");
                }
            } else {
                sb.append(c);
            }
        }
        throw err("texto sin cerrar");
    }

    private Double number() throws RecipeException {
        int start = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw err("número no válido");
        }
    }
}
