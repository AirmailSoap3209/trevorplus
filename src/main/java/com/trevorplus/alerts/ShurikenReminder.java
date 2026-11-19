package com.trevorplus.alerts;

/**
 * Decides when to remind you to apply a Shuriken: the quest mob is in range, its name tag shows
 * no Shuriken marker, and that has been true for a moment (name tags flicker while loading).
 * Repeats every few seconds until the marker shows up. Pure logic with explicit time.
 */
public final class ShurikenReminder {
    static final long SETTLE_MS = 1_500;
    static final long REPEAT_MS = 12_000;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private long unmarkedSince = NEVER;
    private long lastFire = NEVER;

    /** @return true when a reminder should be shown now */
    public boolean update(long nowMs, boolean enabled, boolean questActive, boolean inRangeAndUnmarked) {
        if (!questActive) {
            unmarkedSince = NEVER;
            lastFire = NEVER;              // the next quest gets a fresh reminder
            return false;
        }
        if (!enabled || !inRangeAndUnmarked) {
            unmarkedSince = NEVER;
            return false;
        }
        if (unmarkedSince == NEVER) unmarkedSince = nowMs;
        if (nowMs - unmarkedSince >= SETTLE_MS && nowMs - lastFire >= REPEAT_MS) {
            lastFire = nowMs;
            return true;
        }
        return false;
    }
}
