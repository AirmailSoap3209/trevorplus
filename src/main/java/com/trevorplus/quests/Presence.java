package com.trevorplus.quests;

import java.util.List;

/**
 * Decides when the overlays should be on screen: always while you are in the Farming Islands (or a quest is
 * active), and for one minute after you leave. A moment with no readable zone neither starts nor resets the
 * timer, and if the sidebar never names a zone the overlays simply stay on. Pure logic.
 */
public final class Presence {
    public static final long GRACE_MS = 60_000;

    /** Zones of the Farming Islands (Mushroom Desert and the Barn) besides the Trevor areas. */
    private static final List<String> EXTRA_ZONES = List.of(
            "trapper's den", "mushroom desert", "desert mountain", "the barn", "barn", "the farming islands", "farming islands");

    private long lastHereMs;
    private boolean started;
    private boolean seenZone;

    public static boolean isFarmingZone(String zone) {
        if (zone == null) return false;
        String z = zone.trim().toLowerCase();
        if (Area.fromName(zone) != null) return true;
        for (String e : EXTRA_ZONES) if (z.equals(e)) return true;
        return z.contains("farming islands");
    }

    /** Call about once a second. */
    public void update(long nowMs, String zone, boolean questActive) {
        if (!started) { started = true; lastHereMs = nowMs; }
        if (zone != null) seenZone = true;
        if (questActive || isFarmingZone(zone)) lastHereMs = nowMs;
    }

    public boolean show(long nowMs) {
        // with no readable sidebar at all (hidden scoreboard) there is no way to tell, so stay visible
        return !started || !seenZone || nowMs - lastHereMs <= GRACE_MS;
    }
}
