package com.trevorplus.client;

import com.trevorplus.config.Colors;
import com.trevorplus.config.Settings;
import com.trevorplus.core.HudLines;
import com.trevorplus.quests.Rarity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Colour editor for the rarity colours (mob box, its label, banners and HUD text all follow them).
 * Pick a rarity on the left, then drag in the saturation/brightness square and the hue bar (every one of
 * the 16.7 million colours is reachable), drag the R/G/B sliders, or type / paste a hex code. Changes show
 * live and are saved when a drag ends, a hex code is complete, or the screen closes.
 */
final class ColorPickerScreen extends Screen {
    private static final int MAX_W = 380, MAX_H = 250, HEADER_H = 22, LIST_W = 92, ROW_H = 18;
    private static final int SQ_W = 110, SQ_H = 96, HUE_W = 10, SLIDER_H = 14;
    private static final int GLFW_BACKSPACE = 259, GLFW_ENTER = 257, GLFW_KP_ENTER = 335, GLFW_ESCAPE = 256;
    private static final int DIM_TEXT = 0xFF6A7380;

    private enum Drag { SQUARE, HUE, RED, GREEN, BLUE }

    private final Screen parent;
    private final Settings settings = TrevorplusClient.settings();
    private Rarity rarity;
    private float hue, sat, val;
    private String hexText = "";
    private boolean hexFocus;
    private Drag dragging;
    private boolean dirty;
    private int lastW = 427, lastH = 240;

    ColorPickerScreen(Screen parent, Rarity start) {
        super(Component.literal("Trevor+ colours"));
        this.parent = parent;
        select(start == null ? Rarity.ELUSIVE : start);
    }

    // ---- state -------------------------------------------------------------------------------

    private int current() { return HudLines.color(rarity) & 0xFFFFFF; }
    private int builtIn(Rarity r) { return HudLines.defaultColor(r) & 0xFFFFFF; }

    private void select(Rarity r) {
        rarity = r;
        float[] hsv = Colors.rgbToHsv(current());
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        hexText = Colors.hex(current()).substring(1);
        hexFocus = false;
    }

    /** Applies a colour. keepHsv: the change came from the square or hue bar, so the HSV state is the truth. */
    private void apply(int rgb, boolean keepHsv) {
        settings.setCustomColor(rarity, rgb == builtIn(rarity) ? -1 : rgb);    // the built-in colour is not stored
        if (!keepHsv) {
            float[] hsv = Colors.rgbToHsv(rgb);
            if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];       // greys and black have no hue: keep the old one
            sat = hsv[1];
            val = hsv[2];
        }
        if (!hexFocus) hexText = Colors.hex(rgb).substring(1);
        dirty = true;
    }

    private void save() {
        if (!dirty) return;
        TrevorplusClient.saveSettings();
        dirty = false;
    }

    @Override
    public void onClose() {
        save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // ---- layout ------------------------------------------------------------------------------

    private int pw() { return Math.min(MAX_W, lastW - 8); }
    private int ph() { return Math.min(MAX_H, lastH - 8); }
    private int px() { return (lastW - pw()) / 2; }
    private int py() { return (lastH - ph()) / 2; }
    private int listY(int i) { return py() + HEADER_H + 6 + i * ROW_H; }
    private int sqX() { return px() + LIST_W + 12; }
    private int sqY() { return py() + HEADER_H + 6; }
    private int hueX() { return sqX() + SQ_W + 6; }
    private int previewX() { return hueX() + HUE_W + 8; }
    private int right() { return px() + pw() - 8; }
    private int sliderY(int i) { return sqY() + SQ_H + 10 + i * SLIDER_H; }
    private int sliderX() { return sqX() + 12; }
    private int sliderW() { return Math.max(40, right() - 30 - sliderX()); }
    private int rowY() { return sliderY(3) + 6; }
    private int hexX() { return sqX() + 26; }
    private static final int HEX_W = 58, BTN_H = 16;
    private int defaultX() { return hexX() + HEX_W + 8; }
    private int doneX() { return right() - 50; }

    // ---- drawing -----------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        lastW = g.guiWidth();
        lastH = g.guiHeight();
        g.fill(0, 0, lastW, lastH, 0x80000000);
        int px = px(), py = py(), pw = pw(), ph = ph();
        g.fill(px, py, px + pw, py + ph, 0xF0101218);
        HudPainter.rect(g, px, py, px + pw, py + ph, 1, 0xFF3A4658);
        g.fill(px, py, px + pw, py + 2, HudLines.ACCENT);
        String title = BuildFlags.NAME.toUpperCase(Locale.ROOT);
        g.text(font, title, px + 10, py + 8, HudLines.ACCENT, true);
        g.text(font, "colours", px + 10 + font.width(title + " "), py + 8, HudLines.LABEL, true);
        g.fill(px + 6, py + HEADER_H - 2, px + pw - 6, py + HEADER_H - 1, 0xFF2A3342);

        // rarity list
        for (Rarity r : Rarity.values()) {
            int y = listY(r.ordinal());
            boolean sel = r == rarity;
            boolean hover = mouseX >= px + 4 && mouseX < px + LIST_W && mouseY >= y && mouseY < y + ROW_H - 2;
            if (sel || hover) g.fill(px + 4, y, px + LIST_W, y + ROW_H - 2, sel ? 0xFF2A3342 : 0xFF1A202B);
            if (sel) g.fill(px + 4, y, px + 6, y + ROW_H - 2, HudLines.ACCENT);
            int c = HudLines.color(r);
            g.fill(px + 10, y + 3, px + 20, y + 13, 0xFF000000);
            g.fill(px + 11, y + 4, px + 19, y + 12, c);
            g.text(font, r.label(), px + 24, y + 5, c, true);
        }
        g.fill(px + LIST_W + 4, py + HEADER_H + 2, px + LIST_W + 5, py + ph - 18, 0xFF2A3342);

        drawSquare(g);
        drawHueBar(g);
        drawSliders(g, font);
        drawPreview(g, font);
        drawBottomRow(g, font, mouseX, mouseY);

        String help = "Drag the square, the bar or the sliders, or type a hex code. Esc goes back.";
        g.text(font, fit(font, help, pw - 20), px + 10, py + ph - 12, DIM_TEXT, true);
    }

    private void drawSquare(GuiGraphicsExtractor g) {
        int x0 = sqX(), y0 = sqY();
        for (int i = 0; i < SQ_W; i++) {      // each column fades from full brightness to black
            int top = Colors.hsvToRgb(hue, i / (float) (SQ_W - 1), 1f);
            g.fillGradient(x0 + i, y0, x0 + i + 1, y0 + SQ_H, 0xFF000000 | top, 0xFF000000);
        }
        HudPainter.rect(g, x0 - 1, y0 - 1, x0 + SQ_W + 1, y0 + SQ_H + 1, 1, 0xFF3A4658);
        int mx = x0 + Math.round(sat * (SQ_W - 1)), my = y0 + Math.round((1 - val) * (SQ_H - 1));
        HudPainter.rect(g, mx - 3, my - 3, mx + 4, my + 4, 1, 0xFF000000);
        HudPainter.rect(g, mx - 2, my - 2, mx + 3, my + 3, 1, 0xFFFFFFFF);
    }

    private void drawHueBar(GuiGraphicsExtractor g) {
        int x = hueX(), y0 = sqY();
        for (int k = 0; k < 6; k++) {
            int ya = y0 + k * SQ_H / 6, yb = y0 + (k + 1) * SQ_H / 6;
            g.fillGradient(x, ya, x + HUE_W, yb, 0xFF000000 | Colors.hsvToRgb(k * 60f, 1, 1),
                    0xFF000000 | Colors.hsvToRgb((k + 1) * 60f, 1, 1));
        }
        HudPainter.rect(g, x - 1, y0 - 1, x + HUE_W + 1, y0 + SQ_H + 1, 1, 0xFF3A4658);
        int my = y0 + Math.round(hue / 360f * (SQ_H - 1));
        g.fill(x - 2, my - 1, x + HUE_W + 2, my + 2, 0xFF000000);
        g.fill(x - 1, my, x + HUE_W + 1, my + 1, 0xFFFFFFFF);
    }

    private void drawSliders(GuiGraphicsExtractor g, Font font) {
        int rgb = current();
        String[] names = {"R", "G", "B"};
        int[] shift = {16, 8, 0};
        int[] labelColor = {0xFFFF6060, 0xFF60FF60, 0xFF6090FF};
        int sx = sliderX(), sw = sliderW();
        for (int c = 0; c < 3; c++) {
            int y = sliderY(c);
            g.text(font, names[c], sqX(), y + 2, labelColor[c], true);
            int base = rgb & ~(0xFF << shift[c]);
            for (int x = 0; x < sw; x += 2) {          // the track shows what each value of this channel looks like
                int v = Math.round(x * 255f / (sw - 1));
                g.fill(sx + x, y + 2, sx + Math.min(sw, x + 2), y + SLIDER_H - 4, 0xFF000000 | base | (v << shift[c]));
            }
            HudPainter.rect(g, sx - 1, y + 1, sx + sw + 1, y + SLIDER_H - 3, 1, 0xFF3A4658);
            int value = (rgb >> shift[c]) & 0xFF;
            int kx = sx + Math.round(value / 255f * (sw - 1));
            g.fill(kx - 2, y, kx + 3, y + SLIDER_H - 2, 0xFF000000);
            g.fill(kx - 1, y + 1, kx + 2, y + SLIDER_H - 3, 0xFFFFFFFF);
            String num = String.valueOf(value);
            g.text(font, num, right() - font.width(num), y + 2, HudLines.VALUE, true);
        }
    }

    private void drawPreview(GuiGraphicsExtractor g, Font font) {
        int x0 = previewX(), x1 = right(), y0 = sqY(), y1 = sqY() + SQ_H;
        if (x1 - x0 < 30) return;
        int color = HudLines.color(rarity);
        g.fill(x0, y0, x1, y1, 0xFF0B0E13);
        HudPainter.rect(g, x0, y0, x1, y1, 1, 0xFF2A3342);
        g.text(font, "Preview", x0 + 4, y0 + 4, DIM_TEXT, true);
        // mob box with its label
        String label = fit(font, rarity.label() + " Cow  42m", x1 - x0 - 8);
        int cx = (x0 + x1) / 2, by = y0 + 34;
        int tw = font.width(label);
        g.fill(cx - tw / 2 - 3, by - 14, cx + tw / 2 + 3, by - 2, 0xC0101218);
        g.text(font, label, cx - tw / 2, by - 12, color, true);
        HudPainter.rect(g, cx - 13, by - 1, cx + 13, by + 25, 4, 0xFF000000);
        HudPainter.rect(g, cx - 12, by, cx + 12, by + 24, 2, color);
        // banner text
        String banner = fit(font, "§l" + rarity.label().toUpperCase(Locale.ROOT) + " QUEST", x1 - x0 - 8);
        g.text(font, banner, cx - font.width(banner) / 2, y1 - 22, color, true);
        String hud = fit(font, "Rarity: " + rarity.label(), x1 - x0 - 8);
        g.text(font, hud, cx - font.width(hud) / 2, y1 - 11, color, true);
    }

    private void drawBottomRow(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        int y = rowY();
        g.text(font, "Hex", sqX(), y + 4, HudLines.LABEL, true);
        boolean valid = Colors.parseHex(hexText) >= 0;
        int border = hexFocus ? (valid ? HudLines.ACCENT : HudLines.BAD) : 0xFF3A4658;
        g.fill(hexX(), y, hexX() + HEX_W, y + BTN_H, 0xFF151A23);
        HudPainter.rect(g, hexX(), y, hexX() + HEX_W, y + BTN_H, 1, border);
        boolean caret = hexFocus && (System.currentTimeMillis() / 500) % 2 == 0;
        g.text(font, "#" + hexText + (caret ? "_" : ""), hexX() + 4, y + 4, valid || hexFocus ? HudLines.VALUE : HudLines.BAD, true);

        boolean custom = settings.customColor(rarity) >= 0;
        button(g, font, defaultX(), y, 50, "Default", custom ? 0xFF3A4658 : 0xFF262B34, custom ? HudLines.VALUE : DIM_TEXT, mouseX, mouseY);
        button(g, font, doneX(), y, 50, "Done", 0xFF24703D, HudLines.VALUE, mouseX, mouseY);
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String text, int bg, int fg, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        g.fill(x, y, x + w, y + BTN_H, hover ? brighten(bg) : bg);
        HudPainter.rect(g, x, y, x + w, y + BTN_H, 1, hover ? 0xFFFFFFFF : 0xFF6A7890);
        g.text(font, text, x + (w - font.width(text)) / 2, y + 4, fg, true);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24), gr = Math.min(255, ((argb >> 8) & 0xFF) + 24), b = Math.min(255, (argb & 0xFF) + 24);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "…") > width) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    // ---- input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int y = rowY();
        boolean onHex = mx >= hexX() && mx < hexX() + HEX_W && my >= y && my < y + BTN_H;
        if (!onHex && hexFocus) commitHex();
        hexFocus = onHex;
        if (onHex) return true;

        for (Rarity r : Rarity.values()) {
            int ly = listY(r.ordinal());
            if (mx >= px() + 4 && mx < px() + LIST_W && my >= ly && my < ly + ROW_H - 2) {
                save();
                select(r);
                return true;
            }
        }
        if (my >= y && my < y + BTN_H) {
            if (mx >= defaultX() && mx < defaultX() + 50) {
                settings.setCustomColor(rarity, -1);
                dirty = true;
                save();
                select(rarity);
                return true;
            }
            if (mx >= doneX() && mx < doneX() + 50) {
                onClose();
                return true;
            }
        }
        Drag d = hit(mx, my);
        if (d != null) {
            dragging = d;
            dragTo(mx, my);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Drag hit(double mx, double my) {
        if (mx >= sqX() && mx < sqX() + SQ_W && my >= sqY() && my < sqY() + SQ_H) return Drag.SQUARE;
        if (mx >= hueX() - 2 && mx < hueX() + HUE_W + 2 && my >= sqY() && my < sqY() + SQ_H) return Drag.HUE;
        for (int c = 0; c < 3; c++) {
            int sy = sliderY(c);
            if (mx >= sliderX() - 3 && mx < sliderX() + sliderW() + 3 && my >= sy && my < sy + SLIDER_H) {
                return c == 0 ? Drag.RED : c == 1 ? Drag.GREEN : Drag.BLUE;
            }
        }
        return null;
    }

    private void dragTo(double mx, double my) {
        switch (dragging) {
            case SQUARE -> {
                sat = (float) clamp01((mx - sqX()) / (SQ_W - 1));
                val = (float) (1 - clamp01((my - sqY()) / (SQ_H - 1)));
                apply(Colors.hsvToRgb(hue, sat, val), true);
            }
            case HUE -> {
                hue = (float) (clamp01((my - sqY()) / (SQ_H - 1)) * 359.99);
                apply(Colors.hsvToRgb(hue, sat, val), true);
            }
            case RED, GREEN, BLUE -> {
                int shift = dragging == Drag.RED ? 16 : dragging == Drag.GREEN ? 8 : 0;
                int v = (int) Math.round(clamp01((mx - sliderX()) / (sliderW() - 1)) * 255);
                int rgb = (current() & ~(0xFF << shift)) | (v << shift);
                apply(rgb, false);
            }
        }
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        dragTo(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!hexFocus) return super.keyPressed(event);
        int key = event.key();
        if (key == GLFW_BACKSPACE) {
            if (!hexText.isEmpty()) hexText = hexText.substring(0, hexText.length() - 1);
        } else if (key == GLFW_ENTER || key == GLFW_KP_ENTER || key == GLFW_ESCAPE) {
            commitHex();
            hexFocus = false;
        } else if (event.isPaste()) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            typeHex(clip == null ? "" : clip, true);
        } else if (event.isSelectAll()) {
            hexText = "";
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!hexFocus) return super.charTyped(event);
        typeHex(event.codepointAsString(), false);
        return true;
    }

    /** Adds hex digits (a paste replaces the text); a complete code applies right away. */
    private void typeHex(String typed, boolean replace) {
        StringBuilder digits = new StringBuilder(replace ? "" : hexText);
        for (char ch : typed.toCharArray()) {
            if (Character.digit(ch, 16) >= 0 && digits.length() < 6) digits.append(Character.toUpperCase(ch));
        }
        hexText = digits.toString();
        int rgb = hexText.length() == 6 ? Colors.parseHex(hexText) : -1;
        if (rgb >= 0) {
            apply(rgb, false);
            save();
        }
    }

    /** Leaving the field: a complete (or 3-digit) code is applied, anything else goes back to the current colour. */
    private void commitHex() {
        int rgb = Colors.parseHex(hexText);
        if (rgb >= 0) {
            apply(rgb, false);
            save();
        }
        hexText = Colors.hex(current()).substring(1);
    }
}
