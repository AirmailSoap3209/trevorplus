package com.trevorplus.core;

/** Small display helpers. */
public final class Fmt {
    private Fmt() {}

    /** m:ss, or h:mm:ss from one hour up. Negative values show as 0:00. */
    public static String duration(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
