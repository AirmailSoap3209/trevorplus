package com.trevorplus.config;

import java.util.Locale;

/** How Trevor's "[YES]" quest prompt gets accepted. The warp key and the chat [YES] always work too. */
public enum AcceptMode {
    /** Only by hand: the warp key or clicking [YES] in chat. */
    MANUAL("Warp key / chat", "manual"),
    /** An on-screen button appears; a left click (in game, or on the button with chat open) accepts. */
    CLICK("Click to accept", "click"),
    /** Sent automatically right after the prompt appears. */
    AUTO("Automatic", "auto");

    public final String label;
    public final String word;

    AcceptMode(String label, String word) {
        this.label = label;
        this.word = word;
    }

    public AcceptMode next() { return values()[(ordinal() + 1) % values().length]; }
    public AcceptMode previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    /** Accepts the enum name or the short word; null if neither. */
    public static AcceptMode parse(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (AcceptMode m : values()) {
            if (m.word.equals(t) || m.name().toLowerCase(Locale.ROOT).equals(t)) return m;
        }
        return null;
    }
}
