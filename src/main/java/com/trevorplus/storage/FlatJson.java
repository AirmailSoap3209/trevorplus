package com.trevorplus.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reader/writer for flat JSON objects (string, number, boolean, null values, no nesting).
 * Keeps the core free of dependencies; output is standard JSON so other tools can read it.
 */
public final class FlatJson {
    private FlatJson() {}

    public static String encode(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            Object v = e.getValue();
            if (v instanceof String s) {
                quote(sb, s);
            } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
                sb.append(v);
            } else if (v instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
                else sb.append(d);
            } else {
                quote(sb, v.toString());
            }
        }
        return sb.append('}').toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Throws IllegalArgumentException on anything malformed, including a truncated line. */
    public static Map<String, Object> decode(String text) {
        Parser p = new Parser(text);
        Map<String, Object> out = new LinkedHashMap<>();
        p.skipWs();
        p.expect('{');
        p.skipWs();
        if (p.peek() == '}') {
            p.pos++;
            p.skipWs();
            p.end();
            return out;
        }
        while (true) {
            p.skipWs();
            String key = p.string();
            p.skipWs();
            p.expect(':');
            p.skipWs();
            out.put(key, p.value());
            p.skipWs();
            char c = p.next();
            if (c == '}') break;
            if (c != ',') throw new IllegalArgumentException("expected , or } at " + (p.pos - 1));
        }
        p.skipWs();
        p.end();
        return out;
    }

    public static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    public static long lng(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        if (v instanceof Long l) return l;
        if (v instanceof Double d) return d.longValue();
        return def;
    }

    public static double dbl(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Double d) return d;
        if (v instanceof Long l) return l.doubleValue();
        return def;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c + " at " + (pos - 1));
        }

        void end() {
            if (pos != s.length()) throw new IllegalArgumentException("trailing data at " + pos);
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw new IllegalArgumentException("bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + e);
                }
            }
        }

        Object value() {
            char c = peek();
            if (c == '"') return string();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String tok = s.substring(start, pos);
            if (tok.isEmpty()) throw new IllegalArgumentException("bad value at " + start);
            if (tok.matches("-?\\d+")) return Long.parseLong(tok);
            return Double.parseDouble(tok);
        }
    }
}
