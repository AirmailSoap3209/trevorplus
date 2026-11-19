package com.trevorplus.quests;

import com.trevorplus.core.Clock;
import java.util.Optional;

/**
 * Tracks the single active Trevor quest. Only time from accept to finish is measured,
 * so time between quests never enters a quest duration.
 */
public final class QuestTracker {
    public static final long MAX_QUEST_MS = 10 * 60_000L;
    public static final long EXPIRE_GRACE_MS = 5_000L;

    public record ActiveQuest(Rarity rarity, String areaName, Animal animal, long elapsedMs, boolean timed) {
        public Area area() { return Area.fromName(areaName); }
    }

    private final Clock clock;
    private boolean active;
    private boolean timed;
    private long startMono;
    private long startWall;
    private Rarity rarity;
    private String areaName;
    private Animal animal;

    public QuestTracker(Clock clock) { this.clock = clock; }

    public boolean isActive() { return active; }

    public Optional<ActiveQuest> current() {
        if (!active) return Optional.empty();
        long elapsed = timed ? clock.monoMs() - startMono : -1;
        return Optional.of(new ActiveQuest(rarity, areaName, animal, elapsed, timed));
    }

    /** Starts a quest. If one was still active it is closed as LOST and returned. */
    public Optional<QuestRecord> start(Rarity r, String area) {
        Optional<QuestRecord> previous = active ? Optional.of(close(Outcome.LOST, 0, 1)) : Optional.empty();
        active = true;
        timed = true;
        startMono = clock.monoMs();
        startWall = clock.wallMs();
        rarity = r;
        areaName = area;
        animal = null;
        return previous;
    }

    /** A quest we did not see being accepted (restart, reconnect). Its timing is unknown. */
    public void recover(String area) {
        active = true;
        timed = false;
        startMono = clock.monoMs();
        startWall = clock.wallMs();
        rarity = null;
        areaName = area;
        animal = null;
    }

    public void setAreaIfUnknown(String name) {
        if (active && (areaName == null || areaName.isBlank()) && name != null && !name.isBlank()) areaName = name;
    }

    public void noteAnimal(Animal a) {
        if (active && animal == null) animal = a;
    }

    /** Finishes the quest with a reward. Works even if no accept was seen (untimed record). */
    public QuestRecord finish(Outcome outcome, int pelts, int multiplier) {
        if (!active) {
            long w = clock.wallMs();
            return new QuestRecord(w, w, -1, null, null, null, pelts, outcome, multiplier);
        }
        return close(outcome, pelts, multiplier);
    }

    /** Drops the active quest without producing a record (server hop: the quest is gone). Returns whether one was active. */
    public boolean discard() {
        boolean was = active;
        active = false;
        timed = false;
        rarity = null;
        areaName = null;
        animal = null;
        return was;
    }

    public Optional<QuestRecord> abandon(Outcome outcome) {
        if (!active) return Optional.empty();
        return Optional.of(close(outcome, 0, 1));
    }

    /** Local fallback for the 10-minute expiry, in case the server message never arrives. */
    public Optional<QuestRecord> tick() {
        if (active && timed && clock.monoMs() - startMono >= MAX_QUEST_MS + EXPIRE_GRACE_MS) {
            return Optional.of(close(Outcome.EXPIRED, 0, 1));
        }
        return Optional.empty();
    }

    private QuestRecord close(Outcome outcome, int pelts, int multiplier) {
        long duration = timed ? clock.monoMs() - startMono : -1;
        QuestRecord r = new QuestRecord(startWall, clock.wallMs(), duration, rarity, areaName, animal, pelts, outcome, multiplier);
        active = false;
        timed = false;
        rarity = null;
        areaName = null;
        animal = null;
        return r;
    }
}
