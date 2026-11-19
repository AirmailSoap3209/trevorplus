package com.trevorplus.quests;

/**
 * One finished quest. durationMs is -1 when the start was not observed (recovered quests,
 * rewards seen without an accept line), and such records never enter timing statistics.
 */
public record QuestRecord(
        long startWallMs,
        long endWallMs,
        long durationMs,
        Rarity rarity,
        String areaName,
        Animal animal,
        int pelts,
        Outcome outcome,
        int multiplier) {

    public boolean timed() { return durationMs >= 0; }

    public boolean completed() { return outcome == Outcome.KILLED || outcome == Outcome.SELF_DIED; }

    public Area area() { return Area.fromName(areaName); }
}
