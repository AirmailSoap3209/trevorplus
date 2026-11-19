package com.trevorplus.quests;

/**
 * Trapper animal rarities.
 * Pelts, health and spawn weight come from the wiki table; dye odds are the user's figures.
 * Real pelt rewards are always read from chat, basePelts is only a fallback/reference.
 */
public enum Rarity {
    TRACKABLE(2, 100, 240, 50_000),
    UNTRACKABLE(4, 500, 180, 25_000),
    UNDETECTED(6, 1_000, 120, 10_000),
    ENDANGERED(8, 5_000, 60, 5_000),
    ELUSIVE(16, 10_000, 6, 2_500);

    public final int basePelts;
    public final int maxHealth;
    public final int weight;
    public final int dyeOneIn;

    Rarity(int basePelts, int maxHealth, int weight, int dyeOneIn) {
        this.basePelts = basePelts;
        this.maxHealth = maxHealth;
        this.weight = weight;
        this.dyeOneIn = dyeOneIn;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /** Case-insensitive; returns null for unknown text. */
    public static Rarity parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        for (Rarity r : values()) if (r.name().equalsIgnoreCase(t)) return r;
        return null;
    }
}
