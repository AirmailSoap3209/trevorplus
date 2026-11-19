package com.trevorplus.navigation;

import com.trevorplus.config.Settings;
import com.trevorplus.quests.Area;

/**
 * Decides what the single navigation key does. Pure logic: the Minecraft glue executes the
 * returned action (one command or one prompt click per keypress, never on its own).
 *  1. a fresh [YES] prompt is pending -> accept it,
 *  2. a quest is active -> warp to the animal's area,
 *  3. otherwise -> warp back to Trevor.
 */
public final class Navigator {
    public enum Type { NONE, ACCEPT_PROMPT, WARP_AREA, WARP_TRAPPER }

    public record NavAction(Type type, String command, String note) {
        static NavAction none(String note) { return new NavAction(Type.NONE, null, note); }
    }

    public record Context(boolean acceptPromptFresh, boolean questActive, String areaName) {}

    private final Settings settings;
    private long lastActionMono = Long.MIN_VALUE / 2;

    public Navigator(Settings settings) { this.settings = settings; }

    public NavAction decide(Context c, long nowMono) {
        if (nowMono - lastActionMono < settings.navMinIntervalMs) return NavAction.none("throttled");
        NavAction a;
        if (c.acceptPromptFresh() && settings.navAcceptPrompt) {
            a = new NavAction(Type.ACCEPT_PROMPT, null, "accept the quest prompt");
        } else if (c.questActive()) {
            if (!settings.navWarpArea) {
                a = NavAction.none("area warp disabled");
            } else {
                Area area = Area.fromName(c.areaName());
                a = area == null
                        ? NavAction.none("unknown area: " + c.areaName())
                        : new NavAction(Type.WARP_AREA, settings.warpCommandFor(area), "warp to " + area.displayName);
            }
        } else if (settings.navWarpTrapper) {
            a = new NavAction(Type.WARP_TRAPPER, "/warp trapper", "warp to Trevor");
        } else {
            a = NavAction.none("trapper warp disabled");
        }
        if (a.type() != Type.NONE) lastActionMono = nowMono;
        return a;
    }

    /** Non-acting preview for the HUD: what the key would do right now (ignores throttling). */
    public String describe(Context c) {
        if (c.acceptPromptFresh() && settings.navAcceptPrompt) return "Accept quest";
        if (c.questActive()) {
            Area area = Area.fromName(c.areaName());
            return area == null ? "Unknown area" : area.displayName + " (" + settings.warpCommandFor(area) + ")";
        }
        return "Trevor (/warp trapper)";
    }
}
