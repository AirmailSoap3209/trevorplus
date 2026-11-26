package com.trevorplus.client;

import com.trevorplus.config.AcceptMode;
import com.trevorplus.config.EspMode;
import com.trevorplus.config.Settings;
import com.trevorplus.core.HudLines;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * First-launch screen: a short introduction, the handful of settings worth choosing up front, and
 * shortcuts to the HUD editor and the full menu. Shown once, the first time a world is joined with
 * no settings file; reopen it with /trevorplus welcome. Closing it in any way marks it as seen.
 */
final class WelcomeScreen extends Screen {
    private static final int MAX_W = 320, ROW_H = 14, HELP_LINES = 3, BTN_H = 18;

    private static final String INTRO = "A pelt-grinding companion for Trevor the Trapper. Take a quest and the HUD starts "
            + "on its own: timer, pelts per hour, mob finder, Theodolite aid and rarity alerts. Set up a few things first:";
    private static final String DEFAULT_HELP = "Hover a row to see what it does. Click to change it. Everything here can "
            + "be changed later in the full settings menu.";

    private record Row(String label, String help, Supplier<String> value, Supplier<Integer> color, Runnable click) {}
    private record Button(String label, int color, Runnable click) {}

    private final List<Row> rows = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private int lastW = 427, lastH = 240;

    WelcomeScreen() {
        super(Component.literal("Welcome to Trevor+"));
        Settings s = TrevorplusClient.settings();
        String detected = detectedName();
        if (s.playerName.isBlank() && detected != null) {
            s.playerName = detected;
            save();
        }

        rows.add(new Row("Your username", "Used to recognise your own Pelt Dye in the server-wide broadcast. Filled in from "
                + "your account. Click to use your current account name again.",
                () -> s.playerName.isBlank() ? "not set" : s.playerName,
                () -> s.playerName.isBlank() ? HudLines.BAD : HudLines.GOOD,
                () -> { String n = detectedName(); if (n != null) { s.playerName = n; save(); } }));
        rows.add(new Row("Vincent multiplier", "Your active Vincent dye multiplier (1x when it is off), used for dye odds. "
                + "Click to cycle. Change it whenever Vincent's event changes.",
                () -> s.multiplier + "x", () -> HudLines.GOLD,
                () -> { TrevorplusClient.engine().setMultiplier(s.multiplier % 3 + 1); save(); }));
        if (BuildFlags.ESP) rows.add(new Row("Behind walls", "How the mob box and Theodolite blocks show when a wall is in the way: always, "
                + "faded, or only while in sight. Click to cycle.",
                () -> s.esp().label, () -> s.esp() == EspMode.ALWAYS ? HudLines.WARN : HudLines.GOOD,
                () -> { s.espMode = s.esp().next().name(); save(); }));
        rows.add(new Row("Sound cues", "Short sounds for rarity alerts, Pelt Dyes, the mob being found and Trevor being ready.",
                () -> s.soundEnabled ? "ON" : "OFF", () -> s.soundEnabled ? HudLines.GOOD : HudLines.BAD,
                () -> { s.soundEnabled = !s.soundEnabled; save(); }));
        rows.add(new Row("Accept quests", "Warp key / chat: press the warp key or click [YES]. Click to accept: a button "
                + "appears, one left click takes the quest. Automatic: sent for you (check the server's rules first).",
                () -> s.accept().label, () -> s.accept() == AcceptMode.AUTO ? HudLines.WARN : HudLines.GOOD,
                () -> { s.acceptMode = s.accept().next().name(); save(); }));
        rows.add(new Row("Smart warp key", "One press accepts Trevor's prompt, otherwise warps to the quest area, otherwise "
                + "to the Trapper. Rebind it under Options > Controls > Key Binds > Trevor+.",
                TrevorplusClient::navKeyName, () -> HudLines.GOLD, () -> { }));

        Minecraft mc = Minecraft.getInstance();
        buttons.add(new Button("Arrange HUDs", 0xFF3A4658, () -> mc.gui.setScreen(new TrevorUiScreen(true))));
        buttons.add(new Button("All settings", 0xFF3A4658, () -> mc.gui.setScreen(new TrevorUiScreen(false))));
        buttons.add(new Button("Start grinding", 0xFF24703D, () -> mc.gui.setScreen(null)));
    }

    private static String detectedName() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        String n = p.getName().getString();
        return n == null || n.isBlank() ? null : n;
    }

    private static void save() { TrevorplusClient.saveSettings(); }

    @Override
    public void removed() {
        Settings s = TrevorplusClient.settings();
        if (!s.welcomeShown) {
            s.welcomeShown = true;
            save();
        }
        super.removed();
    }

    // ---- layout ------------------------------------------------------------------------------

    private int panelW() { return Math.min(MAX_W, lastW - 8); }
    private int panelX() { return (lastW - panelW()) / 2; }
    private int introLines(Font font) { return wrap(font, INTRO, panelW() - 20, 4).size(); }
    private int rowsTop(Font font) { return 24 + introLines(font) * 10 + 6; }
    private int helpTop(Font font) { return rowsTop(font) + rows.size() * ROW_H + 4; }
    private int buttonsTop(Font font) { return helpTop(font) + HELP_LINES * 10 + 6 + 8; }
    private int panelH(Font font) { return buttonsTop(font) + BTN_H + 8 + 10 + 6; }
    private int panelY(Font font) { return Math.max(2, (lastH - panelH(font)) / 2); }

    private int buttonW() { return (panelW() - 12 - 2 * 6) / buttons.size(); }
    private int buttonX(int i) { return panelX() + 6 + i * (buttonW() + 6); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        lastW = g.guiWidth();
        lastH = g.guiHeight();
        g.fill(0, 0, lastW, lastH, 0x80000000);

        int pw = panelW(), px = panelX(), py = panelY(font), ph = panelH(font);
        g.fill(px, py, px + pw, py + ph, 0xF0101218);
        HudPainter.rect(g, px, py, px + pw, py + ph, 1, 0xFF3A4658);
        g.fill(px, py, px + pw, py + 2, HudLines.ACCENT);
        String title = BuildFlags.NAME.toUpperCase(java.util.Locale.ROOT);
        g.text(font, title, px + 10, py + 8, HudLines.ACCENT, true);
        g.text(font, "welcome", px + 10 + font.width(title + " "), py + 8, HudLines.LABEL, true);

        int ly = py + 24;
        for (String line : wrap(font, INTRO, pw - 20, 4)) {
            g.text(font, line, px + 10, ly, HudLines.VALUE, true);
            ly += 10;
        }

        Row hovered = null;
        int rt = py + rowsTop(font);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = rt + i * ROW_H;
            boolean hover = mouseX >= px + 6 && mouseX < px + pw - 6 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            if (hover) hovered = r;
            g.fill(px + 6, ry, px + pw - 6, ry + ROW_H - 2, hover ? 0xFF2A3342 : 0xFF1A202B);
            g.text(font, r.label(), px + 12, ry + 2, HudLines.VALUE, true);
            String v = r.value().get();
            g.text(font, v, px + pw - 12 - font.width(v), ry + 2, r.color().get(), true);
        }

        int ht = py + helpTop(font);
        g.fill(px + 6, ht, px + pw - 6, ht + HELP_LINES * 10 + 6, 0xFF151A23);
        String help = hovered != null ? hovered.help() : DEFAULT_HELP;
        int hy = ht + 4;
        for (String line : wrap(font, help, pw - 28, HELP_LINES)) {
            g.text(font, line, px + 12, hy, hovered != null ? HudLines.VALUE : HudLines.LABEL, true);
            hy += 10;
        }

        int bt = py + buttonsTop(font);
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            int bx = buttonX(i), bw = buttonW();
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= bt && mouseY < bt + BTN_H;
            g.fill(bx, bt, bx + bw, bt + BTN_H, hover ? brighten(b.color()) : b.color());
            HudPainter.rect(g, bx, bt, bx + bw, bt + BTN_H, 1, hover ? 0xFFFFFFFF : 0xFF6A7890);
            g.text(font, b.label(), bx + (bw - font.width(b.label())) / 2, bt + (BTN_H - font.lineHeight) / 2 + 1, HudLines.VALUE, true);
        }

        String footer = "Menu any time: /tp or /trevorplus.  This screen: /trevorplus welcome";
        g.text(font, footer, px + (pw - font.width(footer)) / 2, bt + BTN_H + 8, HudLines.LABEL, true);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24), gr = Math.min(255, ((argb >> 8) & 0xFF) + 24), b = Math.min(255, (argb & 0xFF) + 24);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static List<String> wrap(Font font, String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(next) > width && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.size() > maxLines ? out.subList(0, maxLines) : out;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Font font = Minecraft.getInstance().font;
        double mx = event.x(), my = event.y();
        int pw = panelW(), px = panelX(), py = panelY(font);
        int rt = py + rowsTop(font);
        for (int i = 0; i < rows.size(); i++) {
            int ry = rt + i * ROW_H;
            if (mx >= px + 6 && mx < px + pw - 6 && my >= ry && my < ry + ROW_H - 2) {
                rows.get(i).click().run();
                return true;
            }
        }
        int bt = py + buttonsTop(font);
        for (int i = 0; i < buttons.size(); i++) {
            int bx = buttonX(i);
            if (mx >= bx && mx < bx + buttonW() && my >= bt && my < bt + BTN_H) {
                buttons.get(i).click().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
