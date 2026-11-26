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

    public AcceptMode next() { return next(true); }
    public AcceptMode previous() { return previous(true); }

    /** Next mode, skipping Automatic when the variant does not allow it (Trevor+ Lite). */
    public AcceptMode next(boolean allowAuto) { return step(1, allowAuto); }
    public AcceptMode previous(boolean allowAuto) { return step(-1, allowAuto); }

    private AcceptMode step(int d, boolean allowAuto) {
        int n = values().length;
        AcceptMode m = this;
        do {
            m = values()[((m.ordinal() + d) % n + n) % n];
        } while (m == AUTO && !allowAuto);
        return m;
    }

    /** This mode, or Manual if it is Automatic and that is not allowed (a setting carried over from Trevor+). */
    public AcceptMode allowing(boolean allowAuto) { return this == AUTO && !allowAuto ? MANUAL : this; }

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
