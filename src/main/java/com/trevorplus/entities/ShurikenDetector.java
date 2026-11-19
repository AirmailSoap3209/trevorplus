package com.trevorplus.entities;

import java.util.List;

/**
 * Finds the blue Shuriken icon on a name tag. The glue flattens the name into coloured text
 * segments; this class only looks at those. With a learned marker (see /trevorplus shuriken learn)
 * it is an exact text match; without one it accepts any symbol drawn in a blue colour.
 */
public final class ShurikenDetector {
    private ShurikenDetector() {}

    /** rgb is -1 when the segment has no colour of its own. */
    public record Segment(String text, int rgb) {}

    public static boolean hasMarker(List<Segment> segs, String learned) {
        if (learned != null && !learned.isBlank()) {
            for (Segment s : segs) if (s.text().contains(learned)) return true;
            return false;
        }
        return learn(segs) != null;
    }

    /** The symbol(s) of the first blue segment that contains one, or null. */
    public static String learn(List<Segment> segs) {
        for (Segment s : segs) {
            if (!isBluish(s.rgb())) continue;
            StringBuilder sb = new StringBuilder();
            s.text().codePoints().filter(ShurikenDetector::isSymbol).forEach(sb::appendCodePoint);
            if (sb.length() > 0) return sb.toString();
        }
        // fallback: the icon is whatever symbol follows the health heart (the name tag is split into
        // several coloured pieces, e.g. "4,993" "/" "5,000" "heart" then the icon)
        int anchor = -1;
        for (int i = 0; i < segs.size(); i++) {
            if (segs.get(i).text().indexOf('\u2764') >= 0 || segs.get(i).text().indexOf('\u2665') >= 0) anchor = i;
        }
        if (anchor >= 0) {
            for (int i = anchor; i < segs.size(); i++) {
                String t = segs.get(i).text();
                if (i == anchor) t = t.substring(Math.max(t.lastIndexOf('\u2764'), t.lastIndexOf('\u2665')) + 1);
                StringBuilder sb = new StringBuilder();
                t.codePoints().filter(cp -> isSymbol(cp) && cp != 0x2764 && cp != 0x2665).forEach(sb::appendCodePoint);
                if (sb.length() > 0) return sb.toString();
            }
            return null;
        }
        // no heart at all: fall back to the text after a "1,234/5,000" health figure
        boolean afterHealth = false;
        for (Segment s : segs) {
            String t = s.text();
            if (!afterHealth) {
                java.util.regex.Matcher m = HEALTH_END.matcher(t);
                if (!m.find()) continue;
                t = t.substring(m.end());
                afterHealth = true;
            }
            StringBuilder sb = new StringBuilder();
            t.codePoints().filter(cp -> isSymbol(cp) && cp != 0x2764 && cp != 0x2665).forEach(sb::appendCodePoint);
            if (sb.length() > 0) return sb.toString();
        }
        return null;
    }

    private static final java.util.regex.Pattern HEALTH_END = java.util.regex.Pattern.compile("[\\d,]+\\s*/\\s*[\\d,]+");

    static boolean isBluish(int rgb) {
        if (rgb < 0) return false;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return b >= 0xAA && r <= 0x70 && g <= 0x70;      // dark blue 0000AA, blue 5555FF and similar
    }

    static boolean isSymbol(int cp) {
        return cp > 127 && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp);
    }
}
