package com.trevorplus.config;

import java.util.Locale;

/** Colour helpers for the colour picker: RGB <-> HSV and #RRGGBB text. Colours are 0xRRGGBB ints. */
public final class Colors {
    private Colors() {}

    public static int r(int rgb) { return (rgb >> 16) & 0xFF; }
    public static int g(int rgb) { return (rgb >> 8) & 0xFF; }
    public static int b(int rgb) { return rgb & 0xFF; }

    public static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** "#FFAA00" (always upper case, always six digits). */
    public static String hex(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF); }

    /** Parses "#FFAA00", "FFAA00", "#fa0" or "fa0"; -1 if it is not a colour. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() != 6 || !t.matches("[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(t, 16);
    }

    /** Hue 0..360 (exclusive), saturation and value 0..1. */
    public static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 360f) + 360f) % 360f / 60f;
        int i = (int) Math.floor(hh);
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** {hue 0..360, saturation 0..1, value 0..1}; hue is 0 for greys. */
    public static float[] rgbToHsv(int rgb) {
        float r = r(rgb) / 255f, g = g(rgb) / 255f, b = b(rgb) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * (((b - r) / d) + 2);
        else h = 60 * (((r - g) / d) + 4);
        if (h < 0) h += 360;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }
}
