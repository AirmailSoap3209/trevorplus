package com.trevorplus.quests;

import java.util.List;

/**
 * Finds the zone you are standing in from the sidebar. The location line is a map-pin style
 * symbol followed by the zone name; the symbol differs between places (and the game may change it),
 * so besides the known pins any line that starts with a non-letter symbol followed by words counts.
 */
public final class ZoneReader {
    private ZoneReader() {}

    /** Returns the zone name, or null when the sidebar has no location line. */
    public static String current(List<String> lines) {
        if (lines == null) return null;
        // 1. known location pins
        for (String raw : lines) {
            String s = strip(raw);
            int i = firstPin(s);
            if (i < 0) continue;
            String z = tidy(s.substring(i));
            if (!z.isEmpty()) return z;
        }
        // 2. any line that begins with a symbol and then a name (skips times, dates, scores)
        for (String raw : lines) {
            String s = strip(raw).trim();
            if (s.isEmpty()) continue;
            int cp = s.codePointAt(0);
            if (Character.isLetterOrDigit(cp) || cp < 128) continue;       // plain text / ASCII punctuation
            String rest = tidy(s.substring(Character.charCount(cp)));
            if (rest.isEmpty() || !Character.isLetter(rest.codePointAt(0))) continue;
            if (rest.indexOf(':') >= 0) continue;                           // "Purse: ..." style lines
            return rest;
        }
        return null;
    }

    private static int firstPin(String s) {
        int best = -1, len = 1;
        for (String pin : new String[] {"\u23e3", "\u0444", "\ud83d\udccd"}) {
            int i = s.indexOf(pin);
            if (i >= 0 && (best < 0 || i < best)) { best = i; len = pin.length(); }
        }
        return best < 0 ? -1 : best + len;
    }

    private static String strip(String raw) {
        return raw.replace('\u00a0', ' ').replaceAll("\u00a7.", "");
    }

    /** Trims and removes anything that is not part of the name (leading/trailing symbols, spaces). */
    private static String tidy(String s) {
        String t = s.trim();
        int a = 0;
        while (a < t.length() && !Character.isLetterOrDigit(t.codePointAt(a))) a += Character.charCount(t.codePointAt(a));
        return t.substring(a).trim();
    }

    /** True when both names refer to the same zone (case-insensitive, ignores a leading "The"). */
    public static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        String t = s.trim().toLowerCase();
        return t.startsWith("the ") ? t.substring(4) : t;
    }
}
