package com.trevorplus.quests;

/** Mushroom Desert areas Trevor can send you to, with the default warp that is closest. */
public enum Area {
    OASIS("Oasis", "desert"),
    GORGE("Mushroom Gorge", "desert"),
    SETTLEMENT("Desert Settlement", "trapper"),
    OVERGROWN("Overgrown Mushroom Cave", "glowing"),
    GLOWING("Glowing Mushroom Cave", "glowing"),
    MOUNTAIN("Desert Mountain", "trapper");

    public final String displayName;
    public final String defaultWarp;

    Area(String displayName, String defaultWarp) {
        this.displayName = displayName;
        this.defaultWarp = defaultWarp;
    }

    public String defaultCommand() {
        return "/warp " + defaultWarp;
    }

    /** Returns null for areas we do not know (the game may add new ones). */
    public static Area fromName(String name) {
        if (name == null) return null;
        String t = name.trim();
        for (Area a : values()) if (a.displayName.equalsIgnoreCase(t)) return a;
        return null;
    }
}
