package com.trevorplus.statistics;

import com.trevorplus.quests.Rarity;
import com.trevorplus.storage.FlatJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts how many creatures of each rarity Trevor has given you (counted when the quest is
 * accepted, so abandoned and lost quests count too), plus the number of creatures between
 * Elusive spawns. Lifetime numbers are saved; session numbers restart with the session.
 */
public final class SpawnStats {
    private final long[] life = new long[Rarity.values().length];
    private final long[] session = new long[Rarity.values().length];
    private long sinceElusive;
    private long lastGap = -1;
    private long longestGap = -1;
    private long gapSum;
    private long gapCount;

    /** Call once for every accepted quest whose rarity is known. */
    public void record(Rarity r) {
        if (r == null) return;
        life[r.ordinal()]++;
        session[r.ordinal()]++;
        if (r == Rarity.ELUSIVE) {
            // creatures between the previous Elusive and this one (the Elusive itself not counted)
            lastGap = sinceElusive;
            longestGap = Math.max(longestGap, sinceElusive);
            gapSum += sinceElusive;
            gapCount++;
            sinceElusive = 0;
        } else {
            sinceElusive++;
        }
    }

    public long life(Rarity r) { return life[r.ordinal()]; }
    public long session(Rarity r) { return session[r.ordinal()]; }
    public long lifeTotal() { long t = 0; for (long v : life) t += v; return t; }
    public long sessionTotal() { long t = 0; for (long v : session) t += v; return t; }
    public long sinceElusive() { return sinceElusive; }
    /** -1 when no gap has been measured yet. */
    public long lastGap() { return lastGap; }
    public long longestGap() { return longestGap; }
    public double averageGap() { return gapCount == 0 ? Double.NaN : (double) gapSum / gapCount; }
    public long gapCount() { return gapCount; }

    public void resetSession() { java.util.Arrays.fill(session, 0); }

    /** Manual sync, e.g. from another mod's totals. Does not touch the elusive gap history. */
    public void setLifetime(long[] counts) {
        for (int i = 0; i < life.length && i < counts.length; i++) life[i] = Math.max(0, counts[i]);
    }

    public void setSinceElusive(long n) { sinceElusive = Math.max(0, n); }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Rarity r : Rarity.values()) m.put("spawn.life." + r.name(), life[r.ordinal()]);
        m.put("spawn.since", sinceElusive);
        m.put("spawn.lastGap", lastGap);
        m.put("spawn.longestGap", longestGap);
        m.put("spawn.gapSum", gapSum);
        m.put("spawn.gapCount", gapCount);
        return m;
    }

    public static SpawnStats fromMap(Map<String, Object> m) {
        SpawnStats s = new SpawnStats();
        for (Rarity r : Rarity.values()) s.life[r.ordinal()] = Math.max(0, FlatJson.lng(m, "spawn.life." + r.name(), 0));
        s.sinceElusive = Math.max(0, FlatJson.lng(m, "spawn.since", 0));
        s.lastGap = FlatJson.lng(m, "spawn.lastGap", -1);
        s.longestGap = FlatJson.lng(m, "spawn.longestGap", -1);
        s.gapSum = Math.max(0, FlatJson.lng(m, "spawn.gapSum", 0));
        s.gapCount = Math.max(0, FlatJson.lng(m, "spawn.gapCount", 0));
        return s;
    }
}
