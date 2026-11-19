package com.trevorplus.quests;

import java.util.List;

/**
 * Reads the sidebar lines (top to bottom, plain text) and finds the Trevor tracker entry:
 * "Tracker Mob Location:" with the area either on the same line or on the next one.
 */
public final class TrackerBoard {
    private static final String KEY = "Tracker Mob Location:";

    public record Result(boolean active, String area) {}

    private TrackerBoard() {}

    public static Result parse(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String l = clean(lines.get(i));
            if (!l.startsWith(KEY)) continue;
            String rest = clean(l.substring(KEY.length()));
            if (rest.isEmpty() && i + 1 < lines.size()) rest = clean(lines.get(i + 1));
            return new Result(true, rest.isEmpty() ? null : rest);
        }
        return new Result(false, null);
    }

    static String clean(String s) {
        String t = s.replace(' ', ' ').replaceAll("§.", "").trim();
        return t.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
    }
}
