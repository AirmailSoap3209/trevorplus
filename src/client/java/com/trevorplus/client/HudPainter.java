package com.trevorplus.client;

import com.trevorplus.core.HudLines;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws a list of coloured HUD rows as a dark panel with a coloured accent bar. */
final class HudPainter {
    private static final int PAD = 5;
    private static final int DIVIDER_H = 5;

    record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private HudPainter() {}

    private static int rowWidth(Font font, HudLines.Line line) {
        int w = 0;
        for (HudLines.Seg s : line.segs()) w += font.width(s.text());
        return w;
    }

    private static int rowHeight(Font font, HudLines.Line line) {
        return line.kind() == HudLines.KIND_DIVIDER ? DIVIDER_H
                : font.lineHeight + (line.kind() == HudLines.KIND_TITLE ? 3 : 2);
    }

    /** Outer box of the panel when its content starts at (x, y). */
    static Box measure(Font font, List<HudLines.Line> lines, int x, int y) {
        int w = 0, h = 0;
        for (HudLines.Line l : lines) {
            w = Math.max(w, rowWidth(font, l));
            h += rowHeight(font, l);
        }
        return new Box(x - PAD - 2, y - PAD, w + PAD * 2 + 2 + 4, h + PAD * 2 - 1);
    }

    static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int t, int color) {
        g.fill(x1, y1, x2, y1 + t, color);
        g.fill(x1, y2 - t, x2, y2, color);
        g.fill(x1, y1 + t, x1 + t, y2 - t, color);
        g.fill(x2 - t, y1 + t, x2, y2 - t, color);
    }

    static void draw(GuiGraphicsExtractor g, Font font, List<HudLines.Line> lines, int x, int y, int accent, boolean dim) {
        Box b = measure(font, lines, x, y);
        int bg = dim ? 0x70101218 : 0xC0101218;
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bg);
        rect(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 1, 0xFF2B3442);
        g.fill(b.x(), b.y(), b.x() + 2, b.y() + b.h(), dim ? (accent & 0x00FFFFFF) | 0x80000000 : accent);
        int cy = y;
        for (HudLines.Line l : lines) {
            if (l.kind() == HudLines.KIND_DIVIDER) {
                g.fill(x, cy + 2, b.x() + b.w() - PAD, cy + 3, 0x40FFFFFF);
            } else {
                int cx = x;
                for (HudLines.Seg s : l.segs()) {
                    g.text(font, s.text(), cx, cy, s.argb(), true);
                    cx += font.width(s.text());
                }
            }
            cy += rowHeight(font, l);
        }
    }
}
