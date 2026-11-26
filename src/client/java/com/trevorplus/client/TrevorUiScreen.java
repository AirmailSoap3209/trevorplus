package com.trevorplus.client;

import com.trevorplus.config.Settings;
import com.trevorplus.core.HudLines;
import com.trevorplus.quests.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Settings screen with a built-in editor for every on-screen element.
 * Settings: click a row to change it (right click steps numbers and choices backwards); hovering a row
 * shows what it does. Move HUDs: every element (quest HUD, creature HUD, zone panel, direction line,
 * alert banner) can be dragged; positions save on release. Boxes can also be dragged from the settings view.
 */
final class TrevorUiScreen extends Screen {
    private static final int PANEL_W = 310, ROW_H = 14, TOP = 24;

    private record Row(String label, String help, Supplier<String> value, Supplier<Integer> color, Runnable left, Runnable right) {}

    /** One draggable thing on screen. place() receives the wanted top-left of its box. */
    private interface Movable {
        String name();
        HudPainter.Box box(Font font, int W, int H);
        void draw(GuiGraphicsExtractor g, Font font);
        void place(int left, int top, HudPainter.Box current);
        boolean visible();
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Movable> movables = new ArrayList<>();
    private boolean edit;
    private int page;
    private Movable dragging;
    private double dragOffX, dragOffY;
    private int lastW = 427, lastH = 240;      // refreshed on every draw from the graphics object

    TrevorUiScreen() { this(false); }

    /** @param startInEditor open straight into the drag-and-drop HUD editor */
    TrevorUiScreen(boolean startInEditor) {
        super(Component.literal("Trevor+"));
        edit = startInEditor;
        Settings s = TrevorplusClient.settings();

        movables.add(panel("Quest HUD", TrevorplusClient::mainLines, TrevorplusClient::accent,
                () -> s.hudX, () -> s.hudY, (x, y) -> { s.hudX = x; s.hudY = y; }, () -> s.hudVisible));
        movables.add(panel("Creature HUD", TrevorplusClient::spawnLines, () -> HudLines.ACCENT,
                () -> s.hud2X, () -> s.hud2Y, (x, y) -> { s.hud2X = x; s.hud2Y = y; }, () -> s.hud2Visible));
        movables.add(panel("Zone panel", TrevorplusClient::zoneLines, TrevorplusClient::zoneColor,
                () -> s.zoneX, () -> s.zoneY, (x, y) -> { s.zoneX = x; s.zoneY = y; }, () -> s.zoneVisible));
        movables.add(centred("Direction line", "Elusive Cow  42m  \u2191  (above)",
                (g, f) -> TrevorplusClient.drawFinderBar(g, f, "Elusive Cow  42m  \u2191  (above)", HudLines.color(Rarity.ELUSIVE)),
                (f, w, h) -> TrevorplusClient.finderBox(f, "Elusive Cow  42m  \u2191  (above)", w),
                (cx, top) -> { s.finderX = cx; s.finderY = top; }, () -> s.highlightEnabled));
        movables.add(centred("Alert banner", "ELUSIVE QUEST",
                (g, f) -> TrevorplusClient.drawAlert(g, f, "ELUSIVE QUEST", HudLines.color(Rarity.ELUSIVE), 255, true),
                (f, w, h) -> TrevorplusClient.alertBox(f, "ELUSIVE QUEST", w, h),
                (cx, top) -> { s.alertX = cx; s.alertY = top; }, () -> s.alertsEnabled));

        rows.add(action("Move HUDs", "drag",
                "Drag any on-screen element to reposition it: the two HUDs, the zone panel, the direction line and the alert banner.",
                () -> edit = true));
        rows.add(toggle("Quest HUD", "Main panel: quest timer, rarity, efficiency, pelts per hour and Theodolite info.",
                () -> s.hudVisible, v -> s.hudVisible = v));
        rows.add(toggle("Creature HUD", "How many of each rarity you have gotten, and the creatures since the last Elusive.",
                () -> s.hud2Visible, v -> s.hud2Visible = v));
        rows.add(toggle("Zone panel", "Always-on panel with the zone Trevor's mob is in and the zone you are standing in.",
                () -> s.zoneVisible, v -> s.zoneVisible = v));
        rows.add(toggle("Mob highlight", "Boxes the quest mob on screen once it is loaded and shows a direction line at the top.",
                () -> s.highlightEnabled, v -> s.highlightEnabled = v));
        rows.add(toggle("Theodolite blocks", "Highlights the blocks at the height your Theodolite hints point to, visible through walls.",
                () -> s.blocksEnabled, v -> s.blocksEnabled = v));
        rows.add(toggle("Blocks: ring filter", "Only highlight blocks near the hint rings instead of every block at that height.",
                () -> s.blocksRingFilter, v -> s.blocksRingFilter = v));
        rows.add(toggle("Dotted rings", "Draws the Theodolite hint rings as dotted lines on screen and marks where they cross.",
                () -> s.ringsEnabled, v -> s.ringsEnabled = v));
        rows.add(toggle("Rarity alerts", "Flashes a banner on screen when a high-rarity quest starts or a Pelt Dye drops.",
                () -> s.alertsEnabled, v -> s.alertsEnabled = v));
        rows.add(new Row("Alert from rarity", "Lowest rarity that triggers the banner. Click to raise, right click to lower.",
                () -> s.alertMinRarity().label(), () -> HudLines.color(s.alertMinRarity()),
                () -> stepAlert(s, 1), () -> stepAlert(s, -1)));
        rows.add(toggle("Auto-hide away", "Hides every panel, banner, highlight and sound, and switches off the warp key and auto-accept, once you have been outside the Farming Islands for the time below.",
                () -> s.autoHide, v -> s.autoHide = v));
        rows.add(new Row("Auto-hide after", "How long you can be outside the Farming Islands before everything hides. Click to add 10 s, right click to subtract.",
                () -> fmtSecs(s.autoHideSeconds), () -> HudLines.VALUE,
                () -> { s.autoHideSeconds = s.autoHideSeconds >= 600 ? 600 : s.autoHideSeconds + 10; save(); },
                () -> { s.autoHideSeconds = Math.max(10, s.autoHideSeconds - 10); save(); }));
        rows.add(toggle("Chat: mob found", "Print a chat line when the quest mob is found, with its distance.",
                () -> s.msgFound, v -> s.msgFound = v));
        rows.add(toggle("Chat: quest accepted", "Print a chat line when the warp key or auto-accept accepts a quest for you.",
                () -> s.msgAccepted, v -> s.msgAccepted = v));
        rows.add(toggle("Chat: warp info", "Print a chat line saying where the warp key is taking you.",
                () -> s.msgWarp, v -> s.msgWarp = v));
        rows.add(toggle("Chat: dye recorded", "Print a chat line when your Pelt Dye is recorded.",
                () -> s.msgDye, v -> s.msgDye = v));
        rows.add(toggle("Sound cues", "Plays short sounds for rarity alerts, Pelt Dye, the mob being found and Trevor being ready.",
                () -> s.soundEnabled, v -> s.soundEnabled = v));
        rows.add(toggle("Mob-found ping", "Banner and sound the moment the quest mob loads in range.",
                () -> s.mobFoundPing, v -> s.mobFoundPing = v));
        rows.add(toggle("Trevor-ready ping", "Banner and sound when the quest cooldown is over and you can take the next quest.",
                () -> s.readyPing, v -> s.readyPing = v));
        rows.add(toggle("Shuriken reminder", "Reminds you to apply a Shuriken when the quest mob is near and its name tag shows none.",
                () -> s.shurikenReminder, v -> s.shurikenReminder = v));
        rows.add(toggle("Auto-accept quests", "Sends Trevor's [YES] command for you right after he offers a quest. Off by default.",
                () -> s.autoAccept, v -> s.autoAccept = v));
        rows.add(new Row("Vincent multiplier", "Your active Vincent dye multiplier (1x, 2x or 3x), used when counting pelts. Click to cycle.",
                () -> s.multiplier + "x", () -> HudLines.GOLD,
                () -> { TrevorplusClient.engine().setMultiplier(s.multiplier % 3 + 1); save(); },
                () -> { TrevorplusClient.engine().setMultiplier((s.multiplier + 1) % 3 + 1); save(); }));
        rows.add(toggle("Time self-deaths", "Count quests where the mob died on its own in your kill-time averages.",
                () -> s.timeSelfDeaths, v -> s.timeSelfDeaths = v));
        rows.add(new Row("Marker FOV", "Field of view for the on-screen mob box when the game's own setting can't be read. Shows auto when it can.", () -> {
            int game = TrevorplusClient.gameFov();
            return game > 0 ? "auto (" + game + ")" : String.valueOf(s.fovDegrees);
        }, () -> TrevorplusClient.gameFov() > 0 ? HudLines.LABEL : HudLines.VALUE,
                () -> { s.fovDegrees = Math.min(130, s.fovDegrees + 5); save(); },
                () -> { s.fovDegrees = Math.max(30, s.fovDegrees - 5); save(); }));
        rows.add(action("Reset positions", "click", "Puts every HUD, the direction line and the alert banner back to their default places.", () -> {
            s.hudX = 6; s.hudY = 6; s.hud2X = 6; s.hud2Y = 130; s.zoneX = 6; s.zoneY = 200;
            s.finderX = -1; s.finderY = -1; s.alertX = -1; s.alertY = -1;
            save();
        }));
        rows.add(action("Reset session stats", "click", "Starts a fresh session for the stat panels. Your saved quest history is kept.",
                () -> TrevorplusClient.engine().resetSession()));
        rows.add(action("Welcome screen", "open", "Shows the first-launch screen again: quick setup and a short tour.",
                () -> Minecraft.getInstance().gui.setScreen(new WelcomeScreen())));
    }

    // ---- movable builders --------------------------------------------------------------------

    private static Movable panel(String name, Supplier<List<HudLines.Line>> lines, Supplier<Integer> accent,
                                 Supplier<Integer> getX, Supplier<Integer> getY,
                                 java.util.function.BiConsumer<Integer, Integer> set, Supplier<Boolean> visible) {
        return new Movable() {
            public String name() { return name; }
            public HudPainter.Box box(Font f, int W, int H) { return HudPainter.measure(f, lines.get(), getX.get(), getY.get()); }
            public void draw(GuiGraphicsExtractor g, Font f) {
                HudPainter.draw(g, f, lines.get(), getX.get(), getY.get(), accent.get(), !visible.get());
            }
            public void place(int left, int top, HudPainter.Box cur) { set.accept(left + 7, top + 5); }
            public boolean visible() { return visible.get(); }
        };
    }

    private interface BoxFn { HudPainter.Box get(Font f, int W, int H); }

    private static Movable centred(String name, String sample, java.util.function.BiConsumer<GuiGraphicsExtractor, Font> draw,
                                   BoxFn box, java.util.function.BiConsumer<Integer, Integer> set, Supplier<Boolean> visible) {
        return new Movable() {
            public String name() { return name; }
            public HudPainter.Box box(Font f, int W, int H) { return box.get(f, W, H); }
            public void draw(GuiGraphicsExtractor g, Font f) { draw.accept(g, f); }
            public void place(int left, int top, HudPainter.Box cur) { set.accept(left + cur.w() / 2, top); }
            public boolean visible() { return visible.get(); }
        };
    }

    private static String fmtSecs(int s) {
        return s % 60 == 0 ? (s / 60) + " min" : s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }

    private static void stepAlert(Settings s, int d) {
        int n = Rarity.values().length;
        s.alertMin = Rarity.values()[((s.alertMinRarity().ordinal() + d) % n + n) % n].name();
        save();
    }

    private static Row action(String label, String hint, String help, Runnable run) {
        return new Row(label, help, () -> hint, () -> HudLines.WARN, run, () -> { });
    }

    private static Row toggle(String label, String help, Supplier<Boolean> get, Consumer<Boolean> set) {
        Runnable flip = () -> { set.accept(!get.get()); save(); };
        return new Row(label, help, () -> get.get() ? "ON" : "OFF", () -> get.get() ? HudLines.GOOD : HudLines.BAD, flip, flip);
    }

    private static void save() { TrevorplusClient.saveSettings(); }

    private static final int HELP_LINES = 3;
    /** Rows that fit on screen at the current GUI scale (the rest go on further pages). */
    private int perPage() {
        int fixed = TOP + 8 + HELP_LINES * 10 + 22 + 8;
        return Math.max(5, Math.min(rows.size(), (lastH - fixed) / ROW_H));
    }
    private int pages() { return Math.max(1, (rows.size() + perPage() - 1) / perPage()); }
    private int firstRow() { return Math.min(page, pages() - 1) * perPage(); }
    private int shownRows() { return Math.min(perPage(), rows.size() - firstRow()); }
    private int panelH() { return TOP + perPage() * ROW_H + 8 + HELP_LINES * 10 + 22; }
    private boolean overPager(double mx, double my) {
        int px = panelX(), py = panelY();
        return pages() > 1 && mx >= px + PANEL_W - 90 && mx < px + PANEL_W - 4 && my >= py + 4 && my < py + 20;
    }
    private int panelX() { return (lastW - PANEL_W) / 2; }
    private int panelY() { return Math.max(2, (lastH - panelH()) / 2); }

    // "Done" button of the editor
    private int doneX() { return lastW / 2 - 40; }
    private int doneY() { return lastH - 40; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        lastW = g.guiWidth();
        lastH = g.guiHeight();
        g.fill(0, 0, lastW, lastH, edit ? 0x40000000 : 0x60000000);

        // every element is drawn here (dimmed when switched off) so it can be grabbed
        for (Movable m : movables) m.draw(g, font);
        if (edit) {
            for (Movable m : movables) outline(g, font, m, m == dragging);
            String hint = "Drag any box to move it";
            g.text(font, hint, (lastW - font.width(hint)) / 2, lastH / 2 - 12, HudLines.VALUE, true);
            String sub = "Release to save. Press Done when finished.";
            g.text(font, sub, (lastW - font.width(sub)) / 2, lastH / 2, HudLines.LABEL, true);
            boolean hover = mouseX >= doneX() && mouseX < doneX() + 80 && mouseY >= doneY() && mouseY < doneY() + 20;
            g.fill(doneX(), doneY(), doneX() + 80, doneY() + 20, hover ? 0xFF2F8F4F : 0xFF24703D);
            HudPainter.rect(g, doneX(), doneY(), doneX() + 80, doneY() + 20, 1, 0xFF6CFF8A);
            g.text(font, "Done", doneX() + 40 - font.width("Done") / 2, doneY() + 6, HudLines.VALUE, true);
            return;
        }

        int px = panelX(), py = panelY(), ph = panelH();
        g.fill(px, py, px + PANEL_W, py + ph, 0xF0101218);
        HudPainter.rect(g, px, py, px + PANEL_W, py + ph, 1, 0xFF3A4658);
        g.fill(px, py, px + PANEL_W, py + 2, HudLines.ACCENT);
        g.text(font, "TREVOR+", px + 10, py + 8, HudLines.ACCENT, true);
        g.text(font, "settings", px + 10 + font.width("TREVOR+ "), py + 8, HudLines.LABEL, true);
        if (pages() > 1) {
            String pg = "\u25c0  " + (Math.min(page, pages() - 1) + 1) + " / " + pages() + "  \u25b6";
            g.text(font, pg, px + PANEL_W - 10 - font.width(pg), py + 8, HudLines.GOLD, true);
        }
        Row hovered = null;
        for (int i = 0; i < shownRows(); i++) {
            Row r = rows.get(firstRow() + i);
            int ry = py + TOP + i * ROW_H;
            boolean hover = mouseX >= px + 6 && mouseX < px + PANEL_W - 6 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            if (hover) hovered = r;
            g.fill(px + 6, ry, px + PANEL_W - 6, ry + ROW_H - 2, hover ? 0xFF2A3342 : 0xFF1A202B);
            g.text(font, r.label(), px + 12, ry + 2, HudLines.VALUE, true);
            String v = r.value().get();
            g.text(font, v, px + PANEL_W - 12 - font.width(v), ry + 2, r.color().get(), true);
        }
        // description of the hovered row
        int dy = py + TOP + perPage() * ROW_H + 6;
        g.fill(px + 6, dy, px + PANEL_W - 6, dy + HELP_LINES * 10 + 6, 0xFF151A23);
        String help = hovered != null ? hovered.help() : "Hover a setting to see what it does. Click to change, right click steps backwards.";
        int ly = dy + 4;
        for (String line : wrap(font, help, PANEL_W - 28, HELP_LINES)) {
            g.text(font, line, px + 12, ly, hovered != null ? HudLines.VALUE : HudLines.LABEL, true);
            ly += 10;
        }
        g.text(font, (pages() > 1 ? "Arrows (top right) change page. " : "") + "Esc closes.", px + 10, dy + HELP_LINES * 10 + 11, HudLines.LABEL, true);
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

    private void outline(GuiGraphicsExtractor g, Font font, Movable m, boolean active) {
        HudPainter.Box b = m.box(font, lastW, lastH);
        int color = active ? 0xFFFFFFFF : 0xFFFFD35A;
        HudPainter.rect(g, b.x() - 2, b.y() - 2, b.x() + b.w() + 2, b.y() + b.h() + 2, 1, color);
        g.text(font, m.name() + "  (" + b.x() + ", " + b.y() + ")", b.x(), b.y() - 12, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (edit) {
            if (mx >= doneX() && mx < doneX() + 80 && my >= doneY() && my < doneY() + 20) {
                edit = false;
                return true;
            }
            return startDrag(mx, my) || super.mouseClicked(event, doubleClick);
        }
        int px = panelX(), py = panelY();
        if (mx >= px && mx < px + PANEL_W && my >= py && my < py + panelH()) {
            if (overPager(mx, my)) {
                boolean back = mx < px + PANEL_W - 47;
                page = ((Math.min(page, pages() - 1) + (back ? -1 : 1)) % pages() + pages()) % pages();
                return true;
            }
            for (int i = 0; i < shownRows(); i++) {
                int ry = py + TOP + i * ROW_H;
                if (mx >= px + 6 && mx < px + PANEL_W - 6 && my >= ry && my < ry + ROW_H - 2) {
                    Row r = rows.get(firstRow() + i);
                    if (event.button() == 1) r.right().run(); else r.left().run();
                    return true;
                }
            }
            return true;
        }
        return startDrag(mx, my) || super.mouseClicked(event, doubleClick);
    }

    private boolean startDrag(double mx, double my) {
        Font font = Minecraft.getInstance().font;
        for (int i = movables.size() - 1; i >= 0; i--) {        // topmost first
            Movable m = movables.get(i);
            HudPainter.Box b = m.box(font, lastW, lastH);
            if (b.contains(mx, my)) {
                dragging = m;
                dragOffX = mx - b.x();
                dragOffY = my - b.y();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        Font font = Minecraft.getInstance().font;
        HudPainter.Box cur = dragging.box(font, lastW, lastH);
        int left = (int) Math.max(0, Math.min(lastW - cur.w(), event.x() - dragOffX));
        int top = (int) Math.max(12, Math.min(lastH - cur.h(), event.y() - dragOffY));
        dragging.place(left, top, cur);
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
}
