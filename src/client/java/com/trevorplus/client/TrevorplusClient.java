package com.trevorplus.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.trevorplus.Trevorplus;
import com.trevorplus.alerts.AlertQueue;
import com.trevorplus.alerts.ShurikenReminder;
import com.trevorplus.config.AcceptMode;
import com.trevorplus.config.EspMode;
import com.trevorplus.config.Settings;
import com.trevorplus.core.Clock;
import com.trevorplus.core.EngineListener;
import com.trevorplus.core.HudLines;
import com.trevorplus.navigation.Bearing;
import com.trevorplus.navigation.Projection;
import com.trevorplus.core.TrapperEngine;
import com.trevorplus.dye.DyeLuck;
import com.trevorplus.entities.NametagInfo;
import com.trevorplus.entities.NametagParser;
import com.trevorplus.entities.ShurikenDetector;
import com.trevorplus.quests.QuestTracker;
import com.trevorplus.theodolite.HintTracker;
import com.trevorplus.quests.Rarity;
import com.trevorplus.events.TrevorParser;
import com.trevorplus.navigation.Navigator;
import com.trevorplus.quests.Area;
import com.trevorplus.quests.TrackerBoard;
import com.trevorplus.storage.HistoryStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Minecraft glue. Everything here only reads what the client already shows (chat lines,
 * the sidebar, visible nametags). The only thing it ever sends is ONE command per press of
 * the navigation key (or /trevorplus nav). The logic itself lives in the tested core.
 */
public class TrevorplusClient implements ClientModInitializer {
    private static final NametagParser NAMETAGS = new NametagParser();
    private static final TrevorParser PARSER = new TrevorParser();

    private static TrapperEngine engine;
    private static Settings settings;
    private static HistoryStore store;
    private static Path dir;
    private static KeyMapping navKey;
    private static KeyMapping uiKey;
    private static boolean openUi;
    private static boolean openWelcome;
    private static int worldTicks;
    private static final int WELCOME_DELAY_TICKS = 40;
    private static Object lastLevel;
    private static final com.trevorplus.quests.Presence presence = new com.trevorplus.quests.Presence();
    private static volatile boolean overlaysOn = true;
    private static final AlertQueue alerts = new AlertQueue();
    private static final ShurikenReminder shuriken = new ShurikenReminder();
    private static final Interp playerInterp = new Interp();
    private static final Interp targetInterp = new Interp();
    private static int tickCount;

    private static List<HudLines.Line> hudCache = List.of();   // quest HUD rows
    private static List<HudLines.Line> hud2Cache = List.of();
    private static List<HudLines.Line> zoneCache = List.of();
    private static volatile String currentZone;
    private static long hudCacheNanos;
    private static MobFinder.Target lastTarget;
    /** Whether the quest mob is in direct sight (no solid block between eye and mob), refreshed every other tick. */
    private static volatile boolean targetVisible = true;

    @Override
    public void onInitializeClient() {
        dir = FabricLoader.getInstance().getConfigDir().resolve("trevorplus");
        store = new HistoryStore(dir, msg -> Trevorplus.LOGGER.warn("[store] {}", msg));   // only problems are reported
        settings = Settings.fromMap(store.readJson("settings.json"));
        HudLines.useColors(settings);      // custom rarity colours apply everywhere
        engine = new TrapperEngine(Clock.SYSTEM, settings, store, TrevorplusClient::playerPos,
                new Listener(), msg -> verbose("[engine] {}", msg));

        UpdateCheck.start();       // background, once; does nothing when switched off
        navKey = Keybinds.registerNavKey();
        uiKey = Keybinds.registerUiKey();

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) {
                try {
                    engine.onMessage(message.getString());
                    String yes = findYesCommand(message);
                    if (yes != null) {
                        String bare = yes.startsWith("/") ? yes.substring(1) : yes;
                        if (bare.startsWith("chatprompt ")) engine.onQuestPrompt(yes);
                        else verbose("[engine] [YES] button ignored, unexpected command: {}", yes);
                    }
                } catch (RuntimeException e) {
                    Trevorplus.LOGGER.error("message handling failed", e);
                }
            }
            return true;   // never hide or alter any message
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                onTick(mc);
            } catch (RuntimeException e) {
                Trevorplus.LOGGER.error("tick failed", e);
            }
        });

        // Click to accept: in game a left click accepts (and is swallowed, so it does not hit anything);
        // this runs before vanilla reads the attack key. With chat open, clicking the button accepts.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            try {
                if (mc.player == null || mc.gui.screen() != null || !acceptButtonShown()) return;
                boolean clicked = false;
                while (mc.options.keyAttack.consumeClick()) clicked = true;
                if (clicked) clickAccept(mc);
            } catch (RuntimeException e) {
                Trevorplus.LOGGER.error("accept click failed", e);
            }
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, ev) -> {
                if (ev.button() != 0 || !acceptButtonShown()) return true;
                int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
                if (!acceptBox(mc.font, gw, gh).contains(ev.x(), ev.y())) return true;
                clickAccept(mc);
                return false;          // the click is used up
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> serverChanged());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> serverChanged());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> engine.shutdown());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildCommand());
            dispatcher.register(buildAlias());
        });
        BlockXray.init();
        LevelRenderEvents.COLLECT_SUBMITS.register(BlockXray::collect);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("hud"), TrevorplusClient::renderHud);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("hud2"), TrevorplusClient::renderHud2);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("zone"), TrevorplusClient::renderZone);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("finder"), TrevorplusClient::renderFinder);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("rings"), TrevorplusClient::renderRings);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("alert"), TrevorplusClient::renderAlert);
        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Trevorplus.id("accept"), TrevorplusClient::renderAccept);

        Trevorplus.LOGGER.info("Trevor+ client ready, data folder: {}", dir);
    }

    // ---- tick ------------------------------------------------------------------------------

    private static void onTick(Minecraft mc) {
        while (navKey.consumeClick()) {
            // away from the Farming Islands the warp key does nothing (the press is simply swallowed)
            if (overlaysOn && mc.player != null && mc.gui.screen() == null) navigate(mc);
        }
        while (uiKey.consumeClick()) openUi = true;
        if (openUi && mc.gui.screen() == null && mc.player != null) {
            openUi = false;
            mc.gui.setScreen(new TrevorUiScreen());
        }
        // first launch: greet once, a couple of seconds after the first world has loaded
        worldTicks = mc.player == null ? 0 : worldTicks + 1;
        if ((openWelcome || !settings.welcomeShown) && worldTicks >= WELCOME_DELAY_TICKS && mc.gui.screen() == null) {
            openWelcome = false;
            mc.gui.setScreen(new WelcomeScreen());
        }
        // Hypixel server hops arrive as a new world on the same connection: treat that like leaving
        if (mc.level != lastLevel) {
            if (lastLevel != null && mc.level != null) serverChanged();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;
        trackPositions(mc);
        if (tickCount % 2 == 0) updateSight(mc);
        UpdateCheck.announceIfNeeded();

        // only in the Automatic accept mode, and never in Trevor+ Lite (compiled out there)
        String auto = BuildFlags.AUTOMATION && overlaysOn ? engine.autoAcceptCommand() : null;
        if (auto != null) {
            sendCommand(mc, auto);
            if (settings.msgAccepted) tell("Quest accepted automatically.");
        }
        MobFinder.keepGlow();
        if (tickCount % 10 == 0) { if (overlaysOn) findMob(mc); else { MobFinder.clear(); lastTarget = null; } }
        if (tickCount % 20 == 7) {        // the block scan is the heavy part: once a second, and only with a hint
            if (overlaysOn && settings.blocksEnabled && engine.hints().hasY()) BlockHighlight.scan(mc, engine.hints(), settings.blocksRingFilter);
            else BlockHighlight.clear();
        }

        if (++tickCount % 20 != 0) return;

        engine.tick();
        List<String> lines = ScoreboardReader.sidebarLines(mc);
        currentZone = com.trevorplus.quests.ZoneReader.current(lines);
        presence.setGraceMs(settings.autoHideSeconds * 1000L);
        presence.update(nowMs(), currentZone, engine.tracker().isActive());
        overlaysOn = !settings.autoHide || presence.show(nowMs());
        if (lines != null) {
            TrackerBoard.Result board = TrackerBoard.parse(lines);
            engine.onTrackerBoard(board.active(), board.area());
        }
        scanNametags(mc);
    }

    /** Records this tick's positions; the marker interpolates between ticks so it does not jitter. */
    private static void trackPositions(Minecraft mc) {
        playerInterp.set(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        MobFinder.Target t = MobFinder.current();
        if (t == null) {
            targetInterp.clear();
        } else if (t.entity() != null && !t.entity().isRemoved()) {
            targetInterp.set(t.entity().getX(), t.entity().getY(), t.entity().getZ());
        } else {
            targetInterp.set(t.x(), t.y(), t.z());
        }
    }

    /** Line of sight to the mob's body or head; drives the Dim / Only-when-visible modes and the glow outline. */
    private static void updateSight(Minecraft mc) {
        MobFinder.Target t = MobFinder.current();
        if (t == null) {
            targetVisible = true;
        } else {
            double ex = mc.player.getX(), ey = mc.player.getY() + mc.player.getEyeHeight(), ez = mc.player.getZ();
            targetVisible = BlockHighlight.visible(mc, ex, ey, ez, t.x(), t.y() + 0.5, t.z())
                    || BlockHighlight.visible(mc, ex, ey, ez, t.x(), t.y() + 1.3, t.z());
        }
        MobFinder.setGlowAllowed(espMode() == EspMode.ALWAYS || targetVisible);
    }

    /** Disconnect, join or server hop: the quest is gone, so forget it (nothing is saved). */
    private static void serverChanged() {
        MobFinder.clear();
        BlockHighlight.clear();
        lastTarget = null;
        engine.onWorldLeave();
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }

    private static void findMob(Minecraft mc) {
        var quest = engine.tracker().current();
        // Lite has no glow outline (it draws the whole mob over walls); the box there only shows while in sight
        boolean glow = BuildFlags.ESP && settings.highlightEnabled;
        boolean announce = settings.highlightEnabled;
        boolean wanted = announce || settings.shurikenReminder;
        if (!wanted || quest.isEmpty()) {
            MobFinder.clear();
            lastTarget = null;
            shuriken.update(nowMs(), false, quest.isPresent(), false);
            return;
        }
        MobFinder.Target t = MobFinder.scan(mc, quest.get(), glow);
        if (t != null && lastTarget == null && announce) {
            if (BuildFlags.ESP) {
                double d = Math.sqrt(sq(t.x() - mc.player.getX()) + sq(t.y() - mc.player.getY()) + sq(t.z() - mc.player.getZ()));
                if (settings.msgFound) tell("Found " + t.label() + ", " + Math.round(d) + " blocks away.");
                if (settings.mobFoundPing) alerts.push("MOB FOUND  -  " + Math.round(d) + "m", HudLines.color(t.rarity()), nowMs(), 2000);
            } else {          // no distance in Lite: it would say where the mob is
                if (settings.msgFound) tell("Found " + t.label() + " nearby.");
                if (settings.mobFoundPing) alerts.push("MOB FOUND", HudLines.color(t.rarity()), nowMs(), 2000);
            }
            if (settings.mobFoundPing) SoundCue.found();
        }
        lastTarget = t;
        boolean unmarkedNearby = t != null && settings.shurikenFor(t.rarity()) && Boolean.FALSE.equals(t.shuriken())
                && sq(t.x() - mc.player.getX()) + sq(t.y() - mc.player.getY()) + sq(t.z() - mc.player.getZ()) <= 40.0 * 40.0;
        if (shuriken.update(nowMs(), settings.shurikenReminder, true, unmarkedNearby)) {
            alerts.push("APPLY SHURIKEN", 0xFF55AAFF, nowMs(), 2500);
            SoundCue.shuriken();
        }
    }

    private static double sq(double v) { return v * v; }

    /** Looks through a chat message for a clickable [YES] that runs a command. */
    private static String findYesCommand(Component c) {
        ClickEvent ev = c.getStyle().getClickEvent();
        if (ev instanceof ClickEvent.RunCommand rc && rc.command().contains("YES")) return rc.command();
        for (Component sib : c.getSiblings()) {
            String found = findYesCommand(sib);
            if (found != null) return found;
        }
        return null;
    }

    private static void sendCommand(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    /** While a quest is active and the animal type is unknown, read nearby visible nametags. */
    private static void scanNametags(Minecraft mc) {
        var current = engine.tracker().current();
        if (current.isEmpty() || current.get().animal() != null) return;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48.0))) {
            Component name = e.getCustomName();
            if (name == null) continue;
            NAMETAGS.parse(name.getString(), false)
                    .filter(NametagInfo::plausible)
                    .ifPresent(engine::onNametag);
        }
    }

    // ---- navigation ------------------------------------------------------------------------

    private static void navigate(Minecraft mc) {
        Navigator.NavAction action = engine.onNavKey();
        switch (action.type()) {
            case ACCEPT_PROMPT -> {
                String cmd = engine.takePrompt();
                if (cmd != null) {
                    sendCommand(mc, cmd);
                    if (settings.msgAccepted) tell("Quest accepted.");
                }
            }
            case WARP_AREA, WARP_TRAPPER -> {
                String cmd = action.command().startsWith("/") ? action.command().substring(1) : action.command();
                mc.player.connection.sendCommand(cmd);
                if (settings.msgWarp) tell(action.note() + "  (" + action.command() + ")");
            }
            case NONE -> {
                if (settings.msgWarp && !"throttled".equals(action.note())) tell(action.note());
            }
            default -> { }
        }
    }

    // ---- HUD -------------------------------------------------------------------------------

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlaysOn || !settings.hudVisible || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof TrevorUiScreen) return;
        refreshCaches();
        HudPainter.draw(g, mc.font, hudCache, settings.hudX, settings.hudY, accent(), false);
    }

    private static void renderHud2(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlaysOn || !settings.hud2Visible || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof TrevorUiScreen) return;
        refreshCaches();
        HudPainter.draw(g, mc.font, hud2Cache, settings.hud2X, settings.hud2Y, HudLines.ACCENT, false);
    }

    private static void renderZone(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlaysOn || !settings.zoneVisible || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof TrevorUiScreen) return;
        refreshCaches();
        HudPainter.draw(g, mc.font, zoneCache, settings.zoneX, settings.zoneY, zoneAccent(), false);
    }

    private static int zoneAccent() {
        var q = engine.tracker().current();
        if (q.isEmpty()) return HudLines.LABEL;
        return com.trevorplus.quests.ZoneReader.same(q.get().areaName(), currentZone) ? HudLines.GOOD : HudLines.WARN;
    }

    private static void refreshCaches() {
        long now = System.nanoTime();
        if (now - hudCacheNanos <= 200_000_000L) return;        // rebuild text 5 times a second, not every frame
        hudCache = HudLines.buildMain(engine);
        hud2Cache = HudLines.buildSpawns(engine);
        var q = engine.tracker().current();
        zoneCache = HudLines.buildZone(q.map(QuestTracker.ActiveQuest::areaName).orElse(null), q.isPresent(), currentZone);
        hudCacheNanos = now;
    }

    // ---- Theodolite rings ---------------------------------------------------------------------

    private static int dotBudget;

    /**
     * Theodolite overlay: highlights the visible standing blocks at the hinted height (the main aid),
     * optionally draws the hint rings as dotted lines, and marks where the two newest rings cross.
     */
    private static void renderRings(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlaysOn || (!settings.ringsEnabled && !settings.blocksEnabled) || mc.player == null || mc.gui.hud.isHidden()
                || !playerInterp.has()) return;
        HintTracker hints = engine.hints();
        if (!hints.hasY()) return;
        var rings = hints.rings();
        float pt = delta.getGameTimeDeltaPartialTick(false);
        double ex = playerInterp.x(pt), ey = playerInterp.y(pt) + mc.player.getEyeHeight(), ez = playerInterp.z(pt);
        int W = g.guiWidth(), H = g.guiHeight();
        int game = gameFov();
        double fov = game > 0 ? game : settings.fovDegrees;
        double aspect = (double) W / H;
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        dotBudget = 6000;

        if (BuildFlags.ESP && settings.blocksEnabled && !BlockXray.active()) BlockHighlight.draw(g, ex, ey, ez, yaw, pitch, fov, aspect, W, H, 0x6655E0FF);

        if (settings.ringsEnabled) {
            int from = Math.max(0, rings.size() - 4);
            for (int i = from; i < rings.size(); i++) {
                HintTracker.Ring ring = rings.get(i);
                int color = i == rings.size() - 1 ? 0xFF55E0FF : 0x9055E0FF;
                int n = (int) Math.max(96, Math.min(480, ring.radius() * 2));
                double[] prev = null;
                for (double[] p : HintTracker.ringPoints(ring, n)) {
                    Projection.Result r = Projection.project(ex, ey, ez, yaw, pitch, fov, aspect, p[0], ring.cy(), p[1]);
                    double[] cur = r.inFront() && Math.abs(r.nx()) < 30 && Math.abs(r.ny()) < 30
                            ? new double[] {(r.nx() + 1) / 2 * W, (1 - r.ny()) / 2 * H} : null;
                    if (prev != null && cur != null) dotted(g, prev[0], prev[1], cur[0], cur[1], W, H, color);
                    prev = cur;
                }
            }
        }
        // where the two newest rings cross: a gold marker with its distance
        for (double[] c : hints.candidates()) {
            Projection.Result r = Projection.project(ex, ey, ez, yaw, pitch, fov, aspect, c[0], hints.estimatedY(), c[1]);
            if (!r.onScreen()) continue;
            int sx = (int) ((r.nx() + 1) / 2 * W), sy = (int) ((1 - r.ny()) / 2 * H);
            HudPainter.rect(g, sx - 7, sy - 7, sx + 7, sy + 7, 2, 0xFF000000);
            HudPainter.rect(g, sx - 6, sy - 6, sx + 6, sy + 6, 2, HudLines.GOLD);
            String tag = "Likely  " + Math.round(Math.hypot(c[0] - ex, c[1] - ez)) + "m";
            g.text(mc.font, tag, sx - mc.font.width(tag) / 2, sy - 20, HudLines.GOLD, true);
        }
    }

    private static void dotted(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int W, int H, int color) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len > 700 || len < 0.5) return;
        if ((x1 < -50 && x2 < -50) || (x1 > W + 50 && x2 > W + 50) || (y1 < -50 && y2 < -50) || (y1 > H + 50 && y2 > H + 50)) return;
        int steps = (int) Math.min(120, Math.ceil(len / 3.0));
        for (int s = 0; s <= steps && dotBudget > 0; s++) {
            int x = (int) (x1 + (x2 - x1) * s / steps), y = (int) (y1 + (y2 - y1) * s / steps);
            if (x < 0 || y < 0 || x >= W || y >= H) continue;
            g.fill(x, y, x + 2, y + 2, color);
            dotBudget--;
        }
    }

    /** Big flashing banner in the upper middle of the screen. */
    private static void renderAlert(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlaysOn || mc.player == null || mc.gui.hud.isHidden()) return;
        AlertQueue.View v = alerts.current(nowMs());
        if (v == null) return;
        int a = (int) Math.round(v.alpha() * 255);
        if (a < 8) return;
        drawAlert(g, mc.font, v.text(), v.argb(), a, v.flashOn());
    }

    static HudPainter.Box alertBox(Font font, String text, int W, int H) {
        int w = font.width("\u00a7l" + text) + 44, h = 28;
        int cx = settings.alertX >= 0 ? settings.alertX : W / 2;
        int top = settings.alertY >= 0 ? settings.alertY : (int) (H * 0.22);
        return new HudPainter.Box(cx - w / 2, top, w, h);
    }

    static void drawAlert(GuiGraphicsExtractor g, Font font, String plain, int argb, int a, boolean flashOn) {
        String text = "\u00a7l" + plain;
        int tw = font.width(text);
        HudPainter.Box b = alertBox(font, plain, g.guiWidth(), g.guiHeight());
        int x = b.x(), y = b.y(), w = b.w(), h = b.h();
        int rgb = argb & 0xFFFFFF;
        int border = ((flashOn ? a : a / 3) << 24) | (flashOn ? rgb : 0xFFFFFF);
        g.fill(x, y, x + w, y + h, ((a * 3 / 4) << 24) | 0x101218);
        HudPainter.rect(g, x, y, x + w, y + h, 2, border);
        g.fill(x, y, x + w, y + 3, (a << 24) | rgb);
        g.text(font, text, x + (w - tw) / 2, y + (h - font.lineHeight) / 2 + 1, (a << 24) | rgb, true);
    }

    // ---- click to accept ----------------------------------------------------------------------

    private static final String ACCEPT_TEXT = "CLICK TO ACCEPT QUEST";
    private static final String ACCEPT_SUB = "left click, or click here with chat open";

    /** The button shows while a fresh prompt is pending in Click mode (and overlays are on). */
    static boolean acceptButtonShown() {
        return overlaysOn && acceptMode() == AcceptMode.CLICK && engine.promptFresh();
    }

    static HudPainter.Box acceptBox(Font font, int W, int H) {
        int w = Math.max(font.width("\u00a7l" + ACCEPT_TEXT), font.width(ACCEPT_SUB)) + 28, h = 34;
        int cx = settings.acceptX >= 0 ? settings.acceptX : W / 2;
        int top = settings.acceptY >= 0 ? settings.acceptY : (int) (H * 0.6);
        return new HudPainter.Box(cx - w / 2, top, w, h);
    }

    /** @param left fraction of the prompt window still open, drawn as a shrinking bar */
    static void drawAcceptButton(GuiGraphicsExtractor g, Font font, double left) {
        HudPainter.Box b = acceptBox(font, g.guiWidth(), g.guiHeight());
        int x = b.x(), y = b.y(), w = b.w(), h = b.h();
        g.fill(x, y, x + w, y + h, 0xD0101218);
        HudPainter.rect(g, x, y, x + w, y + h, 2, HudLines.GOOD);
        String t = "\u00a7l" + ACCEPT_TEXT;
        g.text(font, t, x + (w - font.width(t)) / 2, y + 6, HudLines.GOOD, true);
        g.text(font, ACCEPT_SUB, x + (w - font.width(ACCEPT_SUB)) / 2, y + 18, HudLines.LABEL, true);
        int bar = (int) Math.round((w - 4) * Math.max(0, Math.min(1, left)));
        g.fill(x + 2, y + h - 4, x + 2 + bar, y + h - 2, HudLines.GOOD);
    }

    private static void renderAccept(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof TrevorUiScreen || !acceptButtonShown()) return;
        drawAcceptButton(g, mc.font, engine.promptRemainingMs() / (double) TrapperEngine.PROMPT_FRESH_MS);
    }

    private static void clickAccept(Minecraft mc) {
        String cmd = engine.takePrompt();
        if (cmd == null) return;
        sendCommand(mc, cmd);
        if (settings.msgAccepted) tell("Quest accepted.");
    }

    static HudPainter.Box finderBox(Font font, String text, int W) {
        int w = font.width(text);
        int cx = settings.finderX >= 0 ? settings.finderX : W / 2;
        int top = settings.finderY >= 0 ? settings.finderY : 21;
        return new HudPainter.Box(cx - w / 2 - 4, top, w + 8, font.lineHeight + 5);
    }

    static void drawFinderBar(GuiGraphicsExtractor g, Font font, String text, int color) {
        if (BuildFlags.ESP) {          // compiled out of Trevor+ Lite
            HudPainter.Box b = finderBox(font, text, g.guiWidth());
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xC0101218);
            g.fill(b.x(), b.y(), b.x() + 2, b.y() + b.h(), color);
            g.text(font, text, b.x() + 4, b.y() + 3, color, true);
        }
    }

    // used by the settings screen
    static Settings settings() { return settings; }
    /** False after a minute outside the Farming Islands (when auto-hide is on): nothing is drawn or played. */
    static boolean overlaysOn() { return overlaysOn; }
    static TrapperEngine engine() { return engine; }
    static List<HudLines.Line> mainLines() { hudCacheNanos = 0; refreshCaches(); return hudCache; }
    static List<HudLines.Line> spawnLines() { return hud2Cache; }
    static List<HudLines.Line> zoneLines() { return zoneCache; }
    static int zoneColor() { return zoneAccent(); }
    static int accent() {
        var q = engine.tracker().current();
        return q.isPresent() && q.get().rarity() != null ? HudLines.color(q.get().rarity()) : HudLines.ACCENT;
    }

    // The game's own FOV setting, read by name so a rename cannot break the build. -1 if unavailable.
    private static java.lang.reflect.Method fovOptions, fovGet;
    private static java.lang.reflect.Field optionsField;
    private static boolean fovTried;

    static int gameFov() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!fovTried) {
                fovTried = true;
                optionsField = Minecraft.class.getField("options");
                fovOptions = optionsField.getType().getMethod("fov");
                fovGet = fovOptions.getReturnType().getMethod("get");
            }
            if (optionsField == null || fovOptions == null || fovGet == null) return -1;
            Object v = fovGet.invoke(fovOptions.invoke(optionsField.get(mc)));
            return v instanceof Number n ? n.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fovOptions = null;
            return -1;
        }
    }

    /**
     * Top-centre direction line plus an on-screen box around the mob. The box is plain camera
     * math (see Projection); at the screen edge it becomes a pointer. How it behaves behind walls
     * follows the ESP mode. Trevor+ Lite keeps only the box, and only while the mob is on screen and
     * in direct sight: the direction line and the edge pointer are compiled out there.
     */
    private static void renderFinder(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MobFinder.Target t = MobFinder.current();
        if (!overlaysOn || !settings.highlightEnabled || t == null || mc.player == null || mc.gui.hud.isHidden()
                || !targetInterp.has() || !playerInterp.has()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        double tx = targetInterp.x(pt), ty = targetInterp.y(pt), tz = targetInterp.z(pt);
        double ex = playerInterp.x(pt), ey = playerInterp.y(pt) + mc.player.getEyeHeight(), ez = playerInterp.z(pt);
        double dist = Math.sqrt(sq(tx - ex) + sq(ty - ey) + sq(tz - ez));
        Font font = mc.font;
        int color = HudLines.color(t.rarity());
        if (BuildFlags.ESP) {          // the direction line: compiled out of Trevor+ Lite
            double rel = Bearing.relativeDegrees(ex, ez, mc.player.getYRot(), tx, tz);
            String height = Bearing.heightHint(ty - (ey - mc.player.getEyeHeight()));
            String text = t.label() + "  " + Math.round(dist) + "m  " + Bearing.arrow(rel) + (height.isEmpty() ? "" : "  (" + height + ")")
                    + (Boolean.FALSE.equals(t.shuriken()) ? "  no Shuriken" : Boolean.TRUE.equals(t.shuriken()) ? "  Shuriken on" : "");
            drawFinderBar(g, font, text, color);
        }

        EspMode mode = espMode();
        boolean blocked = !targetVisible;
        if (blocked && mode == EspMode.VISIBLE) return;
        boolean faded = blocked && mode == EspMode.DIM;
        int boxColor = faded ? (color & 0xFFFFFF) | 0x60000000 : color;
        int outline = faded ? 0x50000000 : 0xFF000000;
        int tagBg = faded ? 0x60101218 : 0xC0101218;

        int W = g.guiWidth(), H = g.guiHeight();
        int game = gameFov();
        double fov = game > 0 ? game : settings.fovDegrees;
        Projection.Result r = Projection.project(ex, ey, ez, mc.player.getYRot(), mc.player.getXRot(),
                fov, (double) W / H, tx, ty + 0.6, tz);
        if (r.onScreen()) {
            double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
            int half = (int) Math.max(7, Math.min(150, 0.75 / (r.depth() * tanHalf) * (H / 2.0)));
            int sx = (int) ((r.nx() + 1) / 2 * W), sy = (int) ((1 - r.ny()) / 2 * H);
            HudPainter.rect(g, sx - half - 1, sy - half - 1, sx + half + 1, sy + half + 1, 4, outline);
            HudPainter.rect(g, sx - half, sy - half, sx + half, sy + half, 2, boxColor);
            String tag = t.label() + "  " + Math.round(dist) + "m" + (faded ? "  (behind wall)" : "");
            int tw = font.width(tag);
            g.fill(sx - tw / 2 - 3, sy - half - font.lineHeight - 6, sx + tw / 2 + 3, sy - half - 2, tagBg);
            g.text(font, tag, sx - tw / 2, sy - half - font.lineHeight - 4, boxColor, true);
        } else if (BuildFlags.ESP) {   // the edge pointer for off-screen mobs: compiled out of Trevor+ Lite
            double ax = r.nx(), ay = r.ny();
            double m = Math.max(Math.abs(ax), Math.abs(ay));
            if (m < 1e-9) { ax = 0; ay = -1; } else { ax /= m; ay /= m; }
            int sx = (int) (W / 2.0 + ax * (W / 2.0 - 16)), sy = (int) (H / 2.0 - ay * (H / 2.0 - 16));
            g.fill(sx - 6, sy - 6, sx + 6, sy + 6, outline);
            g.fill(sx - 5, sy - 5, sx + 5, sy + 5, boxColor);
            String tag = Math.round(dist) + "m";
            int tw = font.width(tag);
            int lx = Math.max(2, Math.min(W - tw - 2, sx - tw / 2));
            int ly = sy < H / 2 ? sy + 9 : sy - 9 - font.lineHeight;
            g.text(font, tag, lx, ly, boxColor, true);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static double[] playerPos() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : new double[] {p.getX(), p.getY(), p.getZ()};
    }

    private static void tell(String text) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[Trevor+] §f" + text));
    }

    private static int say(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text));
        return 1;
    }

    static void saveSettings() {
        store.writeAtomic("settings.json", settings.toMap());
    }

    /** Debug commands and verbose logs: in a dev environment, or with "debugMode": true in settings.json. */
    static boolean debugEnabled() {
        return settings.debugMode || FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** Routine engine chatter: visible in the log only in debug mode, so normal play keeps latest.log clean. */
    private static void verbose(String format, Object arg) {
        if (debugEnabled()) Trevorplus.LOGGER.info(format, arg);
        else Trevorplus.LOGGER.debug(format, arg);
    }

    /** Current binding of the smart warp key, for the welcome screen. */
    static String navKeyName() {
        return navKey.getTranslatedKeyMessage().getString();
    }

    private static final class Listener implements EngineListener {
        @Override
        public void onDye(DyeLuck.DyeDrop drop) {
            if (settings.msgDye) tell("Your Pelt Dye was recorded after " + drop.baseQuestsSince() + " quests.");
            if (settings.alertsEnabled) alerts.push("PELT DYE!", HudLines.GOLD, nowMs(), 6000);
            SoundCue.dye();
        }

        @Override
        public void onCooldownReady() {
            if (!settings.readyPing) return;
            alerts.push("TREVOR READY", HudLines.GOOD, nowMs(), 2000);
            SoundCue.ready();
        }

        @Override
        public void onQuestStarted(QuestTracker.ActiveQuest quest) {
            if (!AlertQueue.shouldAlert(quest.rarity(), settings.alertMinRarity())) return;
            SoundCue.alert(quest.rarity().ordinal());
            if (!settings.alertsEnabled) return;
            String where = quest.areaName() == null ? "" : "  -  " + quest.areaName();
            alerts.push(quest.rarity().label().toUpperCase(Locale.ROOT) + " QUEST" + where,
                    HudLines.color(quest.rarity()), nowMs(), 4500);
        }
    }

    // ---- commands --------------------------------------------------------------------------

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = lit("trevorplus");
        root.executes(c -> openMenu());
        LiteralArgumentBuilder<FabricClientCommandSource> debug = lit("debug");
        LiteralArgumentBuilder<FabricClientCommandSource> data = lit("data");
        root.then(lit("status").executes(c -> status(c.getSource())));
        root.then(lit("help").executes(c -> help(c.getSource())));
        root.then(lit("nav").executes(c -> {
            navigate(Minecraft.getInstance());
            return 1;
        }));
        root.then(lit("pause").executes(c -> {
            engine.pauseSession();
            return say(c.getSource(), "Session paused.");
        }));
        root.then(lit("resume").executes(c -> {
            engine.resumeSession();
            return say(c.getSource(), "Session resumed.");
        }));
        root.then(lit("reset").executes(c -> {
            engine.resetSession();
            return say(c.getSource(), "Session saved to history and reset.");
        }));
        data.then(lit("logdye").executes(c -> {
            engine.logDyeManually();
            return say(c.getSource(), "Dye logged manually.");
        }));
        debug.then(lit("sidebar").executes(c -> {
            List<String> lines = ScoreboardReader.sidebarLines(Minecraft.getInstance());
            if (lines == null) return say(c.getSource(), "No sidebar found.");
            say(c.getSource(), "Zone read: " + com.trevorplus.quests.ZoneReader.current(lines));
            for (String l : lines) {
                StringBuilder cp = new StringBuilder();
                l.codePoints().limit(4).forEach(x -> cp.append(String.format("U+%04X ", x)));
                say(c.getSource(), "[" + cp.toString().trim() + "] " + l.replace('\u00a7', '&'));
            }
            return 1;
        }));
        root.then(lit("autohide")
                .executes(c -> {
                    settings.autoHide = !settings.autoHide;
                    saveSettings();
                    return say(c.getSource(), "Auto-hide away from the Farming Islands " + (settings.autoHide ? "on" : "off")
                            + " (after " + settings.autoHideSeconds + "s)");
                })
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "seconds", IntegerArgumentType.integer(5, 3600))
                        .executes(c -> {
                            settings.autoHideSeconds = IntegerArgumentType.getInteger(c, "seconds");
                            settings.autoHide = true;
                            saveSettings();
                            return say(c.getSource(), "Auto-hide on, after " + settings.autoHideSeconds + "s away");
                        })));
        root.then(lit("sound")
                .executes(c -> {
                    settings.soundEnabled = !settings.soundEnabled;
                    saveSettings();
                    return say(c.getSource(), "Sound cues " + (settings.soundEnabled ? "on" : "off"));
                })
                .then(lit("test").executes(c -> { SoundCue.found(); return 1; })));
        root.then(lit("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "seconds", IntegerArgumentType.integer(0, 600))
                .executes(c -> {
                    settings.cooldownSeconds = IntegerArgumentType.getInteger(c, "seconds");
                    saveSettings();
                    return say(c.getSource(), "Assumed Trevor cooldown: " + settings.cooldownSeconds + "s (his own message overrides it)");
                })));
        debug.then(lit("nametag").executes(c -> dumpNametags(c.getSource())));
        root.then(lit("warps").executes(c -> {
            for (Area a : Area.values()) say(c.getSource(), a.displayName + " -> " + settings.warpCommandFor(a));
            return 1;
        }));

        debug.then(lit("sim").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                "line", StringArgumentType.greedyString())
                .executes(c -> sim(c.getSource(), StringArgumentType.getString(c, "line")))));

        root.then(lit("mult").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "x", IntegerArgumentType.integer(1, 3))
                .executes(c -> {
                    engine.setMultiplier(IntegerArgumentType.getInteger(c, "x"));
                    saveSettings();
                    return say(c.getSource(), "Vincent multiplier: " + settings.multiplier + "x");
                })));

        root.then(lit("name").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                "username", StringArgumentType.word())
                .executes(c -> {
                    settings.playerName = StringArgumentType.getString(c, "username");
                    saveSettings();
                    return say(c.getSource(), "Your username for dye detection: " + settings.playerName);
                })));

        data.then(lit("quests").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "n", IntegerArgumentType.integer(0))
                .executes(c -> {
                    engine.setQuestsSinceDye(IntegerArgumentType.getInteger(c, "n"));
                    return say(c.getSource(), "Quests since last dye set to "
                            + IntegerArgumentType.getInteger(c, "n") + " (estimate).");
                })));

        root.then(lit("hud")
                .executes(c -> {
                    settings.hudVisible = !settings.hudVisible;
                    saveSettings();
                    return say(c.getSource(), "HUD " + (settings.hudVisible ? "shown" : "hidden"));
                })
                .then(lit("pos").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "x", IntegerArgumentType.integer(0, 4000))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                "y", IntegerArgumentType.integer(0, 4000))
                                .executes(c -> {
                                    settings.hudX = IntegerArgumentType.getInteger(c, "x");
                                    settings.hudY = IntegerArgumentType.getInteger(c, "y");
                                    saveSettings();
                                    return say(c.getSource(), "HUD moved to " + settings.hudX + ", " + settings.hudY);
                                })))));

        root.then(lit("hud2")
                .executes(c -> {
                    settings.hud2Visible = !settings.hud2Visible;
                    saveSettings();
                    return say(c.getSource(), "Creature HUD " + (settings.hud2Visible ? "shown" : "hidden"));
                })
                .then(lit("pos").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "x", IntegerArgumentType.integer(0, 4000))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                "y", IntegerArgumentType.integer(0, 4000))
                                .executes(c -> {
                                    settings.hud2X = IntegerArgumentType.getInteger(c, "x");
                                    settings.hud2Y = IntegerArgumentType.getInteger(c, "y");
                                    saveSettings();
                                    return say(c.getSource(), "Creature HUD moved to " + settings.hud2X + ", " + settings.hud2Y);
                                })))));

        root.then(lit("zone")
                .executes(c -> {
                    settings.zoneVisible = !settings.zoneVisible;
                    saveSettings();
                    return say(c.getSource(), "Zone panel " + (settings.zoneVisible ? "shown" : "hidden"));
                })
                .then(lit("pos").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "x", IntegerArgumentType.integer(0, 4000))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                "y", IntegerArgumentType.integer(0, 4000))
                                .executes(c -> {
                                    settings.zoneX = IntegerArgumentType.getInteger(c, "x");
                                    settings.zoneY = IntegerArgumentType.getInteger(c, "y");
                                    saveSettings();
                                    return say(c.getSource(), "Zone panel moved to " + settings.zoneX + ", " + settings.zoneY);
                                })))));

        root.then(lit("alerts")
                .executes(c -> {
                    settings.alertsEnabled = !settings.alertsEnabled;
                    saveSettings();
                    return say(c.getSource(), "Rarity alerts " + (settings.alertsEnabled ? "on" : "off")
                            + " (from " + settings.alertMinRarity().label() + " up)");
                })
                .then(lit("test").executes(c -> {
                    alerts.push("ELUSIVE QUEST  -  Test", HudLines.color(Rarity.ELUSIVE), nowMs(), 4500);
                    return 1;
                })));

        if (BuildFlags.ESP) root.then(lit("esp")
                .executes(c -> say(c.getSource(), "Behind walls: " + settings.esp().label
                        + ". Use /trevorplus esp always | dim | visible"))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (EspMode m : EspMode.values()) b.suggest(m.word);
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            EspMode m = EspMode.parse(StringArgumentType.getString(c, "mode"));
                            if (m == null) return say(c.getSource(), "Use one of: always dim visible");
                            settings.espMode = m.name();
                            saveSettings();
                            return say(c.getSource(), "Behind walls: " + m.label);
                        })));

        root.then(lit("rings").executes(c -> {
            settings.ringsEnabled = !settings.ringsEnabled;
            saveSettings();
            return say(c.getSource(), "Dotted Theodolite rings " + (settings.ringsEnabled ? "on" : "off"));
        }));

        root.then(lit("blocks")
                .executes(c -> {
                    settings.blocksEnabled = !settings.blocksEnabled;
                    saveSettings();
                    return say(c.getSource(), "Theodolite block highlight " + (settings.blocksEnabled ? "on" : "off"));
                })
                .then(lit("filter").executes(c -> {
                    settings.blocksRingFilter = !settings.blocksRingFilter;
                    saveSettings();
                    return say(c.getSource(), settings.blocksRingFilter
                            ? "Only blocks on the hint rings are highlighted."
                            : "Every visible block at the hinted height is highlighted.");
                })));

        root.then(lit("alertmin").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                "rarity", StringArgumentType.word())
                .executes(c -> {
                    Rarity r = Rarity.parse(StringArgumentType.getString(c, "rarity"));
                    if (r == null) return say(c.getSource(), "Use one of: trackable untrackable undetected endangered elusive");
                    settings.alertMin = r.name();
                    saveSettings();
                    return say(c.getSource(), "Alerts now fire from " + r.label() + " up.");
                })));

        root.then(lit("shuriken")
                .executes(c -> say(c.getSource(), "Shuriken reminder " + (settings.shurikenReminder ? "on" : "off")
                        + ", marker: " + (settings.shurikenMarker.isBlank() ? "(any blue symbol)" : settings.shurikenMarker)))
                .then(lit("on").executes(c -> {
                    settings.shurikenReminder = true;
                    saveSettings();
                    return say(c.getSource(), "Shuriken reminder on.");
                }))
                .then(lit("off").executes(c -> {
                    settings.shurikenReminder = false;
                    saveSettings();
                    return say(c.getSource(), "Shuriken reminder off.");
                }))
                .then(lit("clear").executes(c -> {
                    settings.shurikenMarker = "";
                    saveSettings();
                    return say(c.getSource(), "Shuriken marker cleared: any blue symbol on the name tag counts.");
                }))
                .then(lit("learn").executes(c -> learnShuriken(c.getSource())))
                .then(lit("tier").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("rarity", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (Rarity r : Rarity.values()) b.suggest(r.name().toLowerCase(Locale.ROOT));
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            Rarity r = Rarity.parse(StringArgumentType.getString(c, "rarity"));
                            if (r == null) return say(c.getSource(), "Use one of: trackable untrackable undetected endangered elusive");
                            settings.setShurikenFor(r, !settings.shurikenFor(r));
                            saveSettings();
                            return say(c.getSource(), "Shuriken reminder for " + r.label() + ": " + (settings.shurikenFor(r) ? "on" : "off"));
                        }))));

        root.then(lit("ui").executes(c -> {
            openUi = true;       // opens as soon as the chat window has closed
            return say(c.getSource(), "Opening the Trevor+ screen...");
        }));

        root.then(lit("fov").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "degrees", IntegerArgumentType.integer(30, 130))
                .executes(c -> {
                    settings.fovDegrees = IntegerArgumentType.getInteger(c, "degrees");
                    saveSettings();
                    return say(c.getSource(), "Marker FOV set to " + settings.fovDegrees
                            + (gameFov() > 0 ? " (not used: the game's own FOV " + gameFov() + " is read automatically)" : ""));
                })));

        root.then(lit("highlight").executes(c -> {
            settings.highlightEnabled = !settings.highlightEnabled;
            if (!settings.highlightEnabled) MobFinder.clear();
            saveSettings();
            return say(c.getSource(), "Mob highlight " + (settings.highlightEnabled ? "on" : "off"));
        }));

        String acceptWords = BuildFlags.AUTOMATION ? "manual | click | auto" : "manual | click";
        root.then(lit("accept")
                .executes(c -> say(c.getSource(), "Quest accept: " + acceptMode().label + ". Use /trevorplus accept " + acceptWords))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (AcceptMode m : AcceptMode.values()) if (m.allowing(BuildFlags.AUTOMATION) == m) b.suggest(m.word);
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            AcceptMode m = AcceptMode.parse(StringArgumentType.getString(c, "mode"));
                            if (m == null || m.allowing(BuildFlags.AUTOMATION) != m) {
                                return say(c.getSource(), "Use one of: " + acceptWords.replace(" |", ""));
                            }
                            return setAccept(c.getSource(), m);
                        })));
        // older name, kept so existing habits and notes still work (not in Lite: there is nothing to turn on)
        if (BuildFlags.AUTOMATION) root.then(lit("autoaccept")
                .then(lit("on").executes(c -> setAccept(c.getSource(), AcceptMode.AUTO)))
                .then(lit("off").executes(c -> setAccept(c.getSource(), AcceptMode.MANUAL))));

        root.then(lit("warpdelay")
                .executes(c -> say(c.getSource(), "Warp key delay: " + fmtDelay(settings.navMinIntervalMs)
                        + ". Use /trevorplus warpdelay <seconds> (0.5 to 10, in 0.5 s steps)"))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("seconds",
                                DoubleArgumentType.doubleArg(Settings.NAV_DELAY_MIN_MS / 1000.0, Settings.NAV_DELAY_MAX_MS / 1000.0))
                        .executes(c -> {
                            settings.setNavDelayMs(Math.round(DoubleArgumentType.getDouble(c, "seconds") * 1000));
                            saveSettings();
                            return say(c.getSource(), "Warp key delay: " + fmtDelay(settings.navMinIntervalMs));
                        })));

        data.then(lit("counts").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "trackable", IntegerArgumentType.integer(0))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "untrackable", IntegerArgumentType.integer(0))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                "undetected", IntegerArgumentType.integer(0))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                        "endangered", IntegerArgumentType.integer(0))
                                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                                                "elusive", IntegerArgumentType.integer(0))
                                                .executes(c -> {
                                                    engine.setSpawnCounts(new long[] {
                                                            IntegerArgumentType.getInteger(c, "trackable"),
                                                            IntegerArgumentType.getInteger(c, "untrackable"),
                                                            IntegerArgumentType.getInteger(c, "undetected"),
                                                            IntegerArgumentType.getInteger(c, "endangered"),
                                                            IntegerArgumentType.getInteger(c, "elusive")});
                                                    return say(c.getSource(), "Lifetime creature counts set.");
                                                })))))));

        data.then(lit("elusivegap").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                "n", IntegerArgumentType.integer(0))
                .executes(c -> {
                    engine.setSinceElusive(IntegerArgumentType.getInteger(c, "n"));
                    return say(c.getSource(), "Creatures since last Elusive set to " + IntegerArgumentType.getInteger(c, "n"));
                })));

        root.then(lit("warp").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                "area", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "command", StringArgumentType.greedyString())
                        .executes(c -> setWarp(c.getSource(),
                                StringArgumentType.getString(c, "area"), StringArgumentType.getString(c, "command"))))));
        root.then(lit("welcome").executes(c -> {
            openWelcome = true;     // opens as soon as the chat window has closed
            return 1;
        }));
        // diagnostics stay out of release builds' command tree unless debug mode is switched on
        if (debugEnabled()) root.then(debug);
        root.then(data);
        return root;
    }

    /** /tp: short alias that opens the menu. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildAlias() {
        return lit("tp").executes(c -> openMenu());
    }

    private static int openMenu() {
        openUi = true;       // opens as soon as the chat window has closed
        return 1;
    }

    private static int status(FabricClientCommandSource src) {
        for (String line : HudLines.build(engine)) say(src, line);
        for (HudLines.Line line : HudLines.buildSpawns(engine)) say(src, line.text());
        return 1;
    }

    private static void h(FabricClientCommandSource src, String cmd, String what) {
        src.sendFeedback(Component.literal("\u00a77  /trevorplus \u00a7b" + cmd + " \u00a78- \u00a77" + what));
    }

    private static void section(FabricClientCommandSource src, String title) {
        src.sendFeedback(Component.literal("\u00a73\u00a7l" + title));
    }

    private static int help(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("\u00a7b\u00a7l" + BuildFlags.NAME + " \u00a77commands   \u00a78(\u00a7f/tp\u00a78 or \u00a7f/trevorplus\u00a78 opens the menu)"));
        section(src, "Everyday");
        h(src, "ui", "open the settings menu and HUD editor");
        h(src, "welcome", "show the first-launch screen again");
        h(src, "status", "print your stats in chat");
        h(src, "nav", "the smart warp key, without the keybind");
        h(src, "pause | resume | reset", "control the session clock / start a fresh session");
        section(src, "Panels");
        h(src, "hud | hud2 | zone", "show or hide the quest / creature / zone panel");
        h(src, "hud pos <x> <y>", "move a panel (also hud2 pos, zone pos)");
        section(src, "Features");
        h(src, "highlight | blocks | rings", "mob highlight / Theodolite blocks / dotted rings");
        h(src, "blocks filter", "only highlight blocks on the hint rings");
        if (BuildFlags.ESP) h(src, "esp always|dim|visible", "mob box and blocks behind walls: always / faded / only in sight");
        h(src, "alerts [test] | alertmin <rarity>", "rarity banners and the lowest rarity that triggers them");
        h(src, "sound [test]", "sound cues on or off");
        h(src, "autohide [seconds]", "hide everything (and the warp key) after a while outside the Farming Islands");
        h(src, "shuriken on|off|learn|clear", "reminder and the Shuriken icon marker");
        h(src, "shuriken tier <rarity>", "switch the reminder on or off for one rarity");
        if (BuildFlags.AUTOMATION) h(src, "accept manual|click|auto", "how Trevor's [YES] is accepted: key / on-screen click / automatic");
        else h(src, "accept manual|click", "how Trevor's [YES] is accepted: key / on-screen click");
        h(src, "warpdelay <seconds>", "smallest gap between two warp-key presses (0.5 to 10 s)");
        section(src, "Settings");
        h(src, "mult <1-3>", "Vincent multiplier");
        h(src, "name <username>", "your name, to recognise your Pelt Dye");
        h(src, "cooldown <seconds>", "assumed Trevor cooldown");
        h(src, "fov <n>", "fallback FOV for the on-screen mob box");
        h(src, "warp <AREA> </warp cmd>", "change the warp used for an area");
        h(src, "warps", "list the current area warps");
        section(src, "Fix data");
        h(src, "data logdye", "log a Pelt Dye you missed");
        h(src, "data quests <n>", "set quests since your last dye");
        h(src, "data counts <t> <u> <d> <e> <el>", "set the creature totals");
        h(src, "data elusivegap <n>", "set creatures since the last Elusive");
        if (debugEnabled()) {
            section(src, "Debug");
            h(src, "debug nametag | sidebar", "write nametags / print the sidebar, for bug reports");
            h(src, "debug sim <chat line>", "feed a chat line through the parser");
        }
        StringBuilder areas = new StringBuilder("\u00a77Areas: \u00a7f");
        for (Area a : Area.values()) areas.append(a.name()).append(' ');
        src.sendFeedback(Component.literal(areas.toString().trim()));
        return 1;
    }

    /** The behind-walls mode in effect: Trevor+ Lite is always "only when visible". */
    static EspMode espMode() { return BuildFlags.ESP ? settings.esp() : EspMode.VISIBLE; }

    /** The accept mode in effect: a carried-over Automatic counts as Manual in Trevor+ Lite. */
    static AcceptMode acceptMode() { return settings.accept().allowing(BuildFlags.AUTOMATION); }

    /** 3000 -> "3 s", 2500 -> "2.5 s". */
    static String fmtDelay(long ms) {
        return ms % 1000 == 0 ? (ms / 1000) + " s" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private static int setAccept(FabricClientCommandSource src, AcceptMode m) {
        settings.acceptMode = m.name();
        saveSettings();
        return say(src, "Quest accept: " + m.label + switch (m) {
            case MANUAL -> ". Use the warp key or click [YES] in chat.";
            case CLICK -> ". A button appears when Trevor offers a quest: left click to accept.";
            case AUTO -> ". Trevor's [YES] is sent for you a quarter second after it appears.";
        });
    }

    private static int setWarp(FabricClientCommandSource src, String areaName, String command) {
        Area area;
        try {
            area = Area.valueOf(areaName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return say(src, "Unknown area '" + areaName + "'. Use /trevorplus help to see the area names.");
        }
        settings.warpOverrides.put(area.name(), command.trim());
        saveSettings();
        return say(src, area.displayName + " now warps with: " + command.trim());
    }

    /** Debug: feed a chat line through the same parser and engine as a real message. */
    private static int sim(FabricClientCommandSource src, String line) {
        say(src, "parsed: " + PARSER.parse(line).map(Object::toString).orElse("(nothing recognised)"));
        engine.onMessage(line);
        return 1;
    }

    /** Look at a mob that has your Shuriken on it, run this: the blue symbol on its name tag becomes the marker. */
    private static int learnShuriken(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return say(src, "Not in a world.");
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(24.0))) {
            Component name = e.getCustomName();
            if (name == null || NAMETAGS.parse(name.getString(), false).isEmpty()) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return say(src, "No Trapper mob name tag within 24 blocks. Stand next to a mob that has your Shuriken on it.");
        var segs = NameSegments.of(best.getCustomName());
        String symbol = ShurikenDetector.learn(segs);
        if (symbol == null) {
            say(src, "No blue symbol found on that name tag. Its pieces (text / colour):");
            for (var s : segs) say(src, "  '" + s.text() + "'  " + (s.rgb() < 0 ? "no colour" : String.format("#%06X", s.rgb())));
            return say(src, "Include this list in a bug report so the detection can be adjusted.");
        }
        settings.shurikenMarker = symbol;
        saveSettings();
        StringBuilder cps = new StringBuilder();
        symbol.codePoints().forEach(cp -> cps.append(String.format("U+%04X ", cp)));
        return say(src, "Learned the Shuriken marker: " + symbol + " (" + cps.toString().trim() + "). The reminder now looks for exactly that.");
    }

    /** Debug: write nearby named or large living entities (name, class, max health), for tuning the finder. */
    private static int dumpNametags(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return say(src, "Not in a world.");
        List<Entity> list = new ArrayList<>(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(24.0)));
        list.removeIf(e -> e.getCustomName() == null && !(MobFinder.maxHealth(e) >= 50));
        list.sort(Comparator.comparingDouble((Entity e) -> e.distanceToSqr(mc.player)));

        StringBuilder file = new StringBuilder("--- " + Instant.now() + " ---\n");
        int shown = 0;
        for (Entity e : list) {
            Component name = e.getCustomName();
            double dist = Math.sqrt(e.distanceToSqr(mc.player));
            double mh = MobFinder.maxHealth(e);
            file.append(String.format(Locale.ROOT, "%.1fm  %s  maxHealth=%s invisible=%s%n  plain: %s%n  raw:   %s%n",
                    dist, e.getClass().getSimpleName(), Double.isNaN(mh) ? "?" : String.valueOf(mh), e.isInvisible(),
                    name == null ? "(no name)" : name.getString(), name));
            if (name != null) {
                StringBuilder segs = new StringBuilder("  segs:  ");
                for (var sg : NameSegments.of(name)) {
                    segs.append('[').append(sg.text()).append('|')
                            .append(sg.rgb() < 0 ? "none" : String.format("#%06X", sg.rgb())).append("] ");
                }
                file.append(segs).append('\n');
            }
            if (shown++ < 6) {
                say(src, String.format(Locale.ROOT, "%.1fm  %s  hp=%s  %s", dist, e.getClass().getSimpleName(),
                        Double.isNaN(mh) ? "?" : String.valueOf((long) mh), name == null ? "" : name.getString()));
            }
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("nametag-dump.txt"), file.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            return say(src, "Could not write the dump file: " + ex);
        }
        return say(src, list.size() + " entities written to config/trevorplus/nametag-dump.txt");
    }
}
