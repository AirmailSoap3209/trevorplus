package com.trevorplus.config;

import java.util.Locale;

/** How the mob box, glow outline and Theodolite blocks behave when something is in the way. */
public enum EspMode {
    /** Drawn on top of everything, walls included. */
    ALWAYS("Always", "always"),
    /** Full strength when in sight, faded when behind a wall. */
    DIM("Dim behind walls", "dim"),
    /** Only drawn while actually in sight. */
    VISIBLE("Only when visible", "visible");

    public final String label;
    public final String word;

    EspMode(String label, String word) {
        this.label = label;
        this.word = word;
    }

    public EspMode next() { return values()[(ordinal() + 1) % values().length]; }
    public EspMode previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    /** Accepts the enum name or the short word ("always", "dim", "visible"); null if neither. */
    public static EspMode parse(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (EspMode m : values()) {
            if (m.word.equals(t) || m.name().toLowerCase(Locale.ROOT).equals(t)) return m;
        }
        return null;
    }
}
