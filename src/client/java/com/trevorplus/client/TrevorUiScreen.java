package com.trevorplus.client;

import com.trevorplus.config.AcceptMode;
import com.trevorplus.config.EspMode;
import com.trevorplus.config.Settings;
import com.trevorplus.core.HudLines;
import com.trevorplus.quests.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Settings screen, sorted into categories (sidebar on the left, the category's settings on the right),
 * plus a built-in editor for every on-screen element.
 * Settings: click a row to change it (right click steps choices backwards); hovering a row shows what it
 * does; the mouse wheel scrolls a long category. Rows that depend on another setting are indented and
 * greyed out while that setting is off. Move HUDs: every element can be dragged; positions save on release.
 */
final class TrevorUiScreen extends Screen {
    private static final int MAX_W = 430, MAX_H = 300, SIDE_W = 100, ROW_H = 16, HEADER_H = 22, CAT_H = 18;
    private static final int HELP_LINES = 3;
    private static final int DIM_TEXT = 0xFF6A7380;

    private enum Kind { TOGGLE, CHOICE, ACTION, HEADER }

    private record Row(Kind kind, String label, String help, Supplier<String> value, Supplier<Integer> color,
                       Runnable left, Runnable right, int indent, BooleanSupplier enabled) {
        /** Same row, indented under the row above and only usable while cond holds. */
        Row under(BooleanSupplier cond) { return new Row(kind, label, help, value, color, left, right, 1, cond); }
    }

    private record Category(String name, String blurb, List<Row> rows) {}

    /** One draggable thing on screen. place() receives the wanted top-left of its box. */
    private interface Movable {
        String name();
        HudPainter.Box box(Font font, int W, int H);
        void draw(GuiGraphicsExtractor g, Font font);
        void place(int left, int top, HudPainter.Box current);
        boolean visible();
    }

    /** Remembered between openings, so the menu comes back where it was left. */
    private static int lastCategory;

    private final List<Category> categories = new ArrayList<>();
    private final List<Movable> movables = new ArrayList<>();
    private final String version;
    private boolean edit;
    private int category, scroll;
    private Movable dragging;
    private double dragOffX, dragOffY;
    private int lastW = 427, lastH = 240;      // refreshed on every draw from the graphics object
    /** Screen to return to on close (Mod Menu's list); null closes to the game. */
    private final Screen parent;

    TrevorUiScreen() { this(false, null); }

    TrevorUiScreen(boolean startInEditor) { this(startInEditor, null); }

    /** @param startInEditor open straight into the drag-and-drop HUD editor */
    TrevorUiScreen(boolean startInEditor, Screen parent) {
        super(Component.literal("Trevor+"));
        edit = startInEditor;
        this.parent = parent;
        version = FabricLoader.getInstance().getModContainer(BuildFlags.MOD_ID)
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString().split("\\+")[0]).orElse("");
        Settings s = TrevorplusClient.settings();
        buildMovables(s);
        buildCategories(s);
        category = Math.min(lastCategory, categories.size() - 1);
    }

    // ---- content -----------------------------------------------------------------------------

    private void buildMovables(Settings s) {
        movables.add(panel("Quest HUD", TrevorplusClient::mainLines, TrevorplusClient::accent,
                () -> s.hudX, () -> s.hudY, (x, y) -> { s.hudX = x; s.hudY = y; }, () -> s.hudVisible));
        movables.add(panel("Creature HUD", TrevorplusClient::spawnLines, () -> HudLines.ACCENT,
                () -> s.hud2X, () -> s.hud2Y, (x, y) -> { s.hud2X = x; s.hud2Y = y; }, () -> s.hud2Visible));
        movables.add(panel("Zone panel", TrevorplusClient::zoneLines, TrevorplusClient::zoneColor,
                () -> s.zoneX, () -> s.zoneY, (x, y) -> { s.zoneX = x; s.zoneY = y; }, () -> s.zoneVisible));
        if (BuildFlags.ESP) {
            String sample = "Elusive Cow  42m  ↑  (above)";
            movables.add(centred("Direction line",
                    (g, f) -> TrevorplusClient.drawFinderBar(g, f, sample, HudLines.color(Rarity.ELUSIVE)),
                    (f, w, h) -> TrevorplusClient.finderBox(f, sample, w),
                    (cx, top) -> { s.finderX = cx; s.finderY = top; }, () -> s.highlightEnabled));
        }
        movables.add(centred("Alert banner",
                (g, f) -> TrevorplusClient.drawAlert(g, f, "ELUSIVE QUEST", HudLines.color(Rarity.ELUSIVE), 255, true),
                (f, w, h) -> TrevorplusClient.alertBox(f, "ELUSIVE QUEST", w, h),
                (cx, top) -> { s.alertX = cx; s.alertY = top; }, () -> s.alertsEnabled));
        movables.add(centred("Accept button",
                (g, f) -> TrevorplusClient.drawAcceptButton(g, f, 0.6),
                TrevorplusClient::acceptBox,
                (cx, top) -> { s.acceptX = cx; s.acceptY = top; }, () -> s.accept() == AcceptMode.CLICK));
    }

    private void buildCategories(Settings s) {
        List<Row> general = new ArrayList<>();
        general.add(new Row(Kind.CHOICE, "Accept quests", "Warp key / chat: press the warp key or click [YES] in chat. "
                + "Click to accept: a button appears when Trevor offers a quest, one left click takes it. Automatic: sent for you.",
                () -> s.accept().label, () -> s.accept() == AcceptMode.AUTO ? HudLines.WARN : HudLines.VALUE,
                () -> { s.acceptMode = s.accept().next().name(); save(); },
                () -> { s.acceptMode = s.accept().previous().name(); save(); }, 0, () -> true));
        general.add(choice("Vincent multiplier", "Your active Vincent dye multiplier (1x when it is off), used for dye odds.",
                () -> s.multiplier + "x", () -> HudLines.GOLD,
                () -> { TrevorplusClient.engine().setMultiplier(s.multiplier % 3 + 1); save(); },
                () -> { TrevorplusClient.engine().setMultiplier((s.multiplier + 1) % 3 + 1); save(); }));
        general.add(toggle("Time self-deaths", "Count quests where the mob died on its own in your kill-time averages.",
                () -> s.timeSelfDeaths, v -> s.timeSelfDeaths = v));
        general.add(header("Away from the Farming Islands"));
        general.add(toggle("Auto-hide", "Hides every panel, banner, highlight and sound, and switches off the warp key and "
                + "auto-accept, once you have been away from the Farming Islands for a while.",
                () -> s.autoHide, v -> s.autoHide = v));
        general.add(choice("Hide after", "How long you can be away before everything hides. Click adds 10 s, right click subtracts.",
                () -> fmtSecs(s.autoHideSeconds), () -> HudLines.VALUE,
                () -> { s.autoHideSeconds = Math.min(600, s.autoHideSeconds + 10); save(); },
                () -> { s.autoHideSeconds = Math.max(10, s.autoHideSeconds - 10); save(); }).under(() -> s.autoHide));
        general.add(header("Other"));
        general.add(toggle("Check for updates", "Looks for a newer Trevor+ on CurseForge when the game starts and tells you "
                + "in chat. Sends no personal data. Takes effect on the next start.",
                () -> s.updateCheck, v -> s.updateCheck = v));
        general.add(action("Welcome screen", "open", "Shows the first-launch screen again: quick setup and a short tour.",
                () -> Minecraft.getInstance().gui.setScreen(new WelcomeScreen())));
        general.add(action("Reset session stats", "reset", "Starts a fresh session for the stat panels. Your saved quest history is kept.",
                () -> TrevorplusClient.engine().resetSession()));
        categories.add(new Category("General", "Quests, Vincent, and staying out of the way.", general));

        List<Row> hud = new ArrayList<>();
        hud.add(action("Move HUDs", "drag", "Drag any on-screen element to a new place: both HUDs, the zone panel, "
                + "the direction line, the alert banner and the accept button.", () -> edit = true));
        hud.add(header("Panels"));
        hud.add(toggle("Quest HUD", "Main panel: quest timer, rarity, efficiency, pelts per hour and Theodolite info.",
                () -> s.hudVisible, v -> s.hudVisible = v));
        hud.add(toggle("Creature HUD", "How many of each rarity you have gotten, and the creatures since the last Elusive.",
                () -> s.hud2Visible, v -> s.hud2Visible = v));
        hud.add(toggle("Zone panel", "The zone Trevor's mob is in and the zone you are standing in.",
                () -> s.zoneVisible, v -> s.zoneVisible = v));
        hud.add(header("Layout"));
        hud.add(action("Reset positions", "reset", "Puts every panel, the direction line, the alert banner and the accept "
                + "button back in their default places.", () -> {
            s.hudX = 6; s.hudY = 6; s.hud2X = 6; s.hud2Y = 130; s.zoneX = 6; s.zoneY = 200;
            s.finderX = -1; s.finderY = -1; s.alertX = -1; s.alertY = -1; s.acceptX = -1; s.acceptY = -1;
            save();
        }));
        categories.add(new Category("HUD", "Which panels show, and where they sit.", hud));

        List<Row> finder = new ArrayList<>();
        Row fov = choice("Marker FOV", "Field of view used to place on-screen markers when the game's own setting can't be "
                + "read. Shows auto when it can.", () -> {
                    int game = TrevorplusClient.gameFov();
                    return game > 0 ? "auto (" + game + ")" : String.valueOf(s.fovDegrees);
                }, () -> TrevorplusClient.gameFov() > 0 ? HudLines.LABEL : HudLines.VALUE,
                () -> { s.fovDegrees = Math.min(130, s.fovDegrees + 5); save(); },
                () -> { s.fovDegrees = Math.max(30, s.fovDegrees - 5); save(); });
        if (BuildFlags.ESP) {
        finder.add(toggle("Mob highlight", "Boxes the quest mob on screen once it is loaded and shows a direction line at the top.",
                () -> s.highlightEnabled, v -> s.highlightEnabled = v));
        finder.add(choice("Behind walls", "How the mob box, glow outline and Theodolite blocks show when something is in "
                + "the way: always, faded, or only while in sight. The direction line always shows.",
                () -> s.esp().label, () -> s.esp() == EspMode.ALWAYS ? HudLines.WARN : HudLines.GOOD,
                () -> { s.espMode = s.esp().next().name(); save(); },
                () -> { s.espMode = s.esp().previous().name(); save(); }));
        finder.add(fov.under(() -> s.highlightEnabled));
        finder.add(header("Shuriken reminder"));
        }
        finder.add(toggle("Shuriken reminder", "Reminds you to apply a Shuriken when the quest mob is near and its name "
                + "tag shows none. Choose the rarities below.", () -> s.shurikenReminder, v -> s.shurikenReminder = v));
        for (Rarity r : Rarity.values()) {
            finder.add(toggle(r.label(), "Remind for " + r.label() + " mobs.",
                    () -> s.shurikenFor(r), v -> s.setShurikenFor(r, v)).under(() -> s.shurikenReminder));
        }
        if (BuildFlags.ESP) categories.add(new Category("Mob finder", "Finding the quest mob, and the Shuriken reminder.", finder));
        else categories.add(new Category("Shuriken", "Reminds you to apply a Shuriken to the quest mob.", finder));

        List<Row> theo = new ArrayList<>();
        theo.add(toggle("Highlight blocks", "Highlights the blocks at the height your Theodolite hints point to. "
                + (BuildFlags.ESP ? "'Behind walls' in Mob finder decides how they show through walls."
                        : "Only blocks you can see are highlighted; walls hide them."),
                () -> s.blocksEnabled, v -> s.blocksEnabled = v));
        theo.add(toggle("Only on the rings", "Only highlight blocks near the hint rings instead of every block at that height.",
                () -> s.blocksRingFilter, v -> s.blocksRingFilter = v).under(() -> s.blocksEnabled));
        theo.add(toggle("Dotted rings", "Draws the hint rings as dotted lines on screen and marks where they cross.",
                () -> s.ringsEnabled, v -> s.ringsEnabled = v));
        if (!BuildFlags.ESP) theo.add(fov);
        categories.add(new Category("Theodolite", "Help with Trevor's distance and angle hints.", theo));

        List<Row> alerts = new ArrayList<>();
        alerts.add(toggle("Rarity alerts", "Flashes a banner when a high-rarity quest starts or a Pelt Dye drops.",
                () -> s.alertsEnabled, v -> s.alertsEnabled = v));
        alerts.add(choice("From rarity", "Lowest rarity that triggers the banner. Click to raise, right click to lower.",
                () -> s.alertMinRarity().label(), () -> HudLines.color(s.alertMinRarity()),
                () -> stepAlert(s, 1), () -> stepAlert(s, -1)).under(() -> s.alertsEnabled));
        alerts.add(header("Sounds and pings"));
        alerts.add(toggle("Sound cues", "Short sounds for rarity alerts, Pelt Dyes, the mob being found and Trevor being ready.",
                () -> s.soundEnabled, v -> s.soundEnabled = v));
        alerts.add(toggle("Mob-found ping", "Banner and sound the moment the quest mob loads in range.",
                () -> s.mobFoundPing, v -> s.mobFoundPing = v));
        alerts.add(toggle("Trevor-ready ping", "Banner and sound when the cooldown is over and you can take the next quest.",
                () -> s.readyPing, v -> s.readyPing = v));
        categories.add(new Category("Alerts", "Banners and sounds for the moments that matter.", alerts));

        List<Row> chat = new ArrayList<>();
        chat.add(toggle("Mob found", "A chat line when the quest mob is found, with its distance.",
                () -> s.msgFound, v -> s.msgFound = v));
        chat.add(toggle("Quest accepted", "A chat line when the warp key, the accept button or auto-accept takes a quest for you.",
                () -> s.msgAccepted, v -> s.msgAccepted = v));
        chat.add(toggle("Warp info", "A chat line saying where the warp key is taking you.",
                () -> s.msgWarp, v -> s.msgWarp = v));
        chat.add(toggle("Dye recorded", "A chat line when your Pelt Dye is recorded.",
                () -> s.msgDye, v -> s.msgDye = v));
        categories.add(new Category("Chat", "The chat lines Trevor+ prints for you.", chat));
    }

    // ---- row builders ------------------------------------------------------------------------

    private static Row toggle(String label, String help, Supplier<Boolean> get, Consumer<Boolean> set) {
        Runnable flip = () -> { set.accept(!get.get()); save(); };
        return new Row(Kind.TOGGLE, label, help, () -> get.get() ? "ON" : "OFF",
                () -> get.get() ? HudLines.GOOD : HudLines.BAD, flip, flip, 0, () -> true);
    }

    private static Row choice(String label, String help, Supplier<String> value, Supplier<Integer> color, Runnable next, Runnable prev) {
        return new Row(Kind.CHOICE, label, help, value, color, next, prev, 0, () -> true);
    }

    private static Row action(String label, String hint, String help, Runnable run) {
        return new Row(Kind.ACTION, label, help, () -> hint, () -> HudLines.WARN, run, () -> { }, 0, () -> true);
    }

    private static Row header(String title) {
        return new Row(Kind.HEADER, title, null, () -> "", () -> HudLines.GOLD, () -> { }, () -> { }, 0, () -> true);
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

    private static Movable centred(String name, java.util.function.BiConsumer<GuiGraphicsExtractor, Font> draw,
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

    private static void save() { TrevorplusClient.saveSettings(); }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // ---- layout ------------------------------------------------------------------------------

    private int pw() { return Math.min(MAX_W, lastW - 8); }
    private int ph() { return Math.min(MAX_H, lastH - 8); }
    private int px() { return (lastW - pw()) / 2; }
    private int py() { return (lastH - ph()) / 2; }
    private int contentX() { return px() + SIDE_W + 8; }
    private int contentR() { return px() + pw() - 8; }
    private int rowsTop() { return py() + HEADER_H + 26; }
    private int helpTop() { return py() + ph() - 14 - (HELP_LINES * 10 + 8); }
    private int visibleRows() { return Math.max(1, (helpTop() - 4 - rowsTop()) / ROW_H); }
    private List<Row> rows() { return categories.get(category).rows(); }
    private int maxScroll() { return Math.max(0, rows().size() - visibleRows()); }
    private int catY(int i) { return py() + HEADER_H + 4 + i * CAT_H; }

    // "Done" button of the editor
    private int doneX() { return lastW / 2 - 40; }
    private int doneY() { return lastH - 40; }

    // ---- drawing -----------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        lastW = g.guiWidth();
        lastH = g.guiHeight();
        g.fill(0, 0, lastW, lastH, edit ? 0x40000000 : 0x60000000);

        // every element is drawn here (dimmed when switched off) so it can be grabbed
        for (Movable m : movables) m.draw(g, font);
        if (edit) {
            drawEditor(g, font, mouseX, mouseY);
            return;
        }

        int px = px(), py = py(), pw = pw(), ph = ph();
        g.fill(px, py, px + pw, py + ph, 0xF0101218);
        HudPainter.rect(g, px, py, px + pw, py + ph, 1, 0xFF3A4658);
        g.fill(px, py, px + pw, py + 2, HudLines.ACCENT);
        String title = BuildFlags.NAME.toUpperCase(java.util.Locale.ROOT);
        g.text(font, title, px + 10, py + 8, HudLines.ACCENT, true);
        g.text(font, "settings", px + 10 + font.width(title + " "), py + 8, HudLines.LABEL, true);
        g.text(font, version, px + pw - 10 - font.width(version), py + 8, DIM_TEXT, true);
        g.fill(px + 6, py + HEADER_H - 2, px + pw - 6, py + HEADER_H - 1, 0xFF2A3342);

        // sidebar
        for (int i = 0; i < categories.size(); i++) {
            int y = catY(i);
            boolean sel = i == category;
            boolean hover = mouseX >= px + 4 && mouseX < px + SIDE_W && mouseY >= y && mouseY < y + CAT_H - 2;
            if (sel || hover) g.fill(px + 4, y, px + SIDE_W, y + CAT_H - 2, sel ? 0xFF2A3342 : 0xFF1A202B);
            if (sel) g.fill(px + 4, y, px + 6, y + CAT_H - 2, HudLines.ACCENT);
            g.text(font, categories.get(i).name(), px + 11, y + 4, sel ? HudLines.VALUE : HudLines.LABEL, true);
        }
        g.fill(px + SIDE_W + 3, py + HEADER_H + 2, px + SIDE_W + 4, helpTop() - 4, 0xFF2A3342);

        // category title and rows
        Category cat = categories.get(category);
        int x0 = contentX(), x1 = contentR();
        g.text(font, "§l" + cat.name(), x0, py + HEADER_H + 4, HudLines.ACCENT, true);
        g.text(font, fit(font, cat.blurb(), x1 - x0), x0, py + HEADER_H + 15, HudLines.LABEL, true);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        Row hovered = null;
        List<Row> rows = cat.rows();
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = rowsTop() + i * ROW_H;
            if (r.kind() == Kind.HEADER) {
                g.text(font, r.label(), x0 + 2, ry + 5, HudLines.GOLD, true);
                int lx = x0 + 6 + font.width(r.label());
                g.fill(lx, ry + 9, x1, ry + 10, 0xFF2A3342);
                continue;
            }
            boolean on = r.enabled().getAsBoolean();
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            if (hover) hovered = r;
            g.fill(x0, ry, x1, ry + ROW_H - 2, hover && on ? 0xFF2A3342 : 0xFF1A202B);
            int lx = x0 + 6 + r.indent() * 10;
            if (r.indent() > 0) g.text(font, "›", x0 + 6, ry + 4, DIM_TEXT, true);
            g.text(font, r.label(), lx, ry + 4, on ? HudLines.VALUE : DIM_TEXT, true);
            drawValue(g, font, r, on, x1 - 4, ry);
        }
        if (maxScroll() > 0) {      // scrollbar
            int top = rowsTop(), h = visibleRows() * ROW_H - 2;
            int thumb = Math.max(10, h * visibleRows() / rows.size());
            int ty = top + (h - thumb) * scroll / maxScroll();
            g.fill(x1 + 3, top, x1 + 5, top + h, 0xFF1A202B);
            g.fill(x1 + 3, ty, x1 + 5, ty + thumb, 0xFF6A7890);
        }

        // help box
        int ht = helpTop();
        g.fill(px + 6, ht, px + pw - 6, ht + HELP_LINES * 10 + 8, 0xFF151A23);
        String help = hovered != null ? hovered.help()
                : "Hover a setting to see what it does. Click to change it, right click goes back a step.";
        int ly = ht + 5;
        for (String line : wrap(font, help, pw - 28, HELP_LINES)) {
            g.text(font, line, px + 12, ly, hovered != null ? HudLines.VALUE : HudLines.LABEL, true);
            ly += 10;
        }
        g.text(font, "Esc closes", px + 10, py + ph - 11, DIM_TEXT, true);
        if (maxScroll() > 0) {
            String more = "Scroll for more";
            g.text(font, more, px + pw - 10 - font.width(more), py + ph - 11, DIM_TEXT, true);
        }
    }

    private void drawValue(GuiGraphicsExtractor g, Font font, Row r, boolean on, int right, int ry) {
        String v = r.value().get();
        int color = on ? r.color().get() : DIM_TEXT;
        switch (r.kind()) {
            case TOGGLE -> {
                boolean yes = "ON".equals(v);
                int w = Math.max(font.width("OFF"), font.width("ON")) + 10;
                int bg = !on ? 0xFF262B34 : yes ? 0xFF1F4D2E : 0xFF4A2226;
                g.fill(right - w, ry + 2, right, ry + ROW_H - 4, bg);
                g.text(font, v, right - w + (w - font.width(v)) / 2, ry + 4, color, true);
            }
            case CHOICE -> {
                String t = "‹ " + v + " ›";
                g.text(font, t, right - font.width(t), ry + 4, color, true);
            }
            case ACTION -> {
                String t = v + " ›";
                g.text(font, t, right - font.width(t), ry + 4, color, true);
            }
            default -> { }
        }
    }

    private void drawEditor(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        for (Movable m : movables) outline(g, font, m, m == dragging);
        String hint = "Drag any box to move it";
        g.text(font, hint, (lastW - font.width(hint)) / 2, lastH / 2 - 12, HudLines.VALUE, true);
        String sub = "Release to save. Press Done when finished.";
        g.text(font, sub, (lastW - font.width(sub)) / 2, lastH / 2, HudLines.LABEL, true);
        boolean hover = mouseX >= doneX() && mouseX < doneX() + 80 && mouseY >= doneY() && mouseY < doneY() + 20;
        g.fill(doneX(), doneY(), doneX() + 80, doneY() + 20, hover ? 0xFF2F8F4F : 0xFF24703D);
        HudPainter.rect(g, doneX(), doneY(), doneX() + 80, doneY() + 20, 1, 0xFF6CFF8A);
        g.text(font, "Done", doneX() + 40 - font.width("Done") / 2, doneY() + 6, HudLines.VALUE, true);
    }

    /** Cuts text to fit a width, with an ellipsis. */
    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "…") > width) t = t.substring(0, t.length() - 1);
        return t + "…";
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
        int color = active ? 0xFFFFFFFF : m.visible() ? 0xFFFFD35A : 0xFF8A7A50;
        HudPainter.rect(g, b.x() - 2, b.y() - 2, b.x() + b.w() + 2, b.y() + b.h() + 2, 1, color);
        g.text(font, m.name() + (m.visible() ? "" : " (off)") + "  (" + b.x() + ", " + b.y() + ")", b.x(), b.y() - 12, color, true);
    }

    // ---- input -------------------------------------------------------------------------------

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
        int px = px(), py = py();
        if (mx < px || mx >= px + pw() || my < py || my >= py + ph()) return startDrag(mx, my) || super.mouseClicked(event, doubleClick);

        for (int i = 0; i < categories.size(); i++) {
            int y = catY(i);
            if (mx >= px + 4 && mx < px + SIDE_W && my >= y && my < y + CAT_H - 2) {
                if (category != i) scroll = 0;
                category = i;
                lastCategory = i;
                return true;
            }
        }
        List<Row> rows = rows();
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            int ry = rowsTop() + i * ROW_H;
            if (mx >= contentX() && mx < contentR() && my >= ry && my < ry + ROW_H - 2) {
                Row r = rows.get(scroll + i);
                if (r.kind() != Kind.HEADER && r.enabled().getAsBoolean()) {
                    if (event.button() == 1) r.right().run(); else r.left().run();
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (edit || sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
        return true;
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
