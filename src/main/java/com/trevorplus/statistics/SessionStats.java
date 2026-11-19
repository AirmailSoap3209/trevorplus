package com.trevorplus.statistics;

import com.trevorplus.config.Settings;
import com.trevorplus.quests.Animal;
import com.trevorplus.quests.Outcome;
import com.trevorplus.quests.QuestRecord;
import com.trevorplus.quests.Rarity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates the quests of one session. Pelt counts are the real amounts read from chat. */
public final class SessionStats {
    public record Snapshot(
            long elapsedMs, int quests, int kills, int selfDeaths, int lost, int expired, int interrupted,
            long pelts, double avgMs, long fastestMs, long slowestMs,
            Map<Rarity, Double> avgMsByRarity, Map<Animal, Double> avgMsByAnimal, Map<Rarity, Integer> countByRarity,
            double questsPerHour, double peltsPerHour, double peltsPerMinute, double peltsPerQuest) {}

    private final Settings settings;
    private final List<QuestRecord> records = new ArrayList<>();
    private long startWallMs;

    public SessionStats(Settings settings, long startWallMs) {
        this.settings = settings;
        this.startWallMs = startWallMs;
    }

    public void record(QuestRecord r) { records.add(r); }

    public void reset(long newStartWallMs) {
        records.clear();
        startWallMs = newStartWallMs;
    }

    public long startWallMs() { return startWallMs; }
    public int recordCount() { return records.size(); }

    private boolean usedForTiming(QuestRecord r) {
        if (!r.timed()) return false;
        if (r.outcome() == Outcome.KILLED) return true;
        return r.outcome() == Outcome.SELF_DIED && settings.timeSelfDeaths;
    }

    public Snapshot snapshot(long elapsedMs) {
        int quests = 0, kills = 0, selfDeaths = 0, lost = 0, expired = 0, interrupted = 0;
        long pelts = 0;
        long totalMs = 0;
        int timedCount = 0;
        long fastest = -1, slowest = -1;
        Map<Rarity, long[]> byRarity = new EnumMap<>(Rarity.class);   // {sum, count}
        Map<Animal, long[]> byAnimal = new EnumMap<>(Animal.class);
        Map<Rarity, Integer> countByRarity = new EnumMap<>(Rarity.class);

        for (QuestRecord r : records) {
            switch (r.outcome()) {
                case KILLED -> kills++;
                case SELF_DIED -> selfDeaths++;
                case LOST -> lost++;
                case EXPIRED -> expired++;
                case INTERRUPTED -> interrupted++;
            }
            if (r.completed()) {
                quests++;
                pelts += r.pelts();
                if (r.rarity() != null) countByRarity.merge(r.rarity(), 1, Integer::sum);
            }
            if (usedForTiming(r)) {
                long d = r.durationMs();
                totalMs += d;
                timedCount++;
                fastest = fastest < 0 ? d : Math.min(fastest, d);
                slowest = Math.max(slowest, d);
                if (r.rarity() != null) {
                    long[] a = byRarity.computeIfAbsent(r.rarity(), k -> new long[2]);
                    a[0] += d;
                    a[1]++;
                }
                if (r.animal() != null) {
                    long[] a = byAnimal.computeIfAbsent(r.animal(), k -> new long[2]);
                    a[0] += d;
                    a[1]++;
                }
            }
        }

        Map<Rarity, Double> avgByRarity = new LinkedHashMap<>();
        byRarity.forEach((k, v) -> avgByRarity.put(k, (double) v[0] / v[1]));
        Map<Animal, Double> avgByAnimal = new LinkedHashMap<>();
        byAnimal.forEach((k, v) -> avgByAnimal.put(k, (double) v[0] / v[1]));

        double hours = elapsedMs / 3_600_000.0;
        double qph = hours > 0 ? quests / hours : 0;
        double pph = hours > 0 ? pelts / hours : 0;
        return new Snapshot(elapsedMs, quests, kills, selfDeaths, lost, expired, interrupted, pelts,
                timedCount > 0 ? (double) totalMs / timedCount : 0, fastest, slowest,
                avgByRarity, avgByAnimal, countByRarity,
                qph, pph, pph / 60.0, quests > 0 ? (double) pelts / quests : 0);
    }

    /** Compact record for the session history file. */
    public Map<String, Object> summary(long elapsedMs, long endWallMs) {
        Snapshot s = snapshot(elapsedMs);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", startWallMs);
        m.put("end", endWallMs);
        m.put("activeMs", s.elapsedMs());
        m.put("quests", s.quests());
        m.put("pelts", s.pelts());
        m.put("avgMs", s.avgMs());
        m.put("peltsPerHour", s.peltsPerHour());
        m.put("questsPerHour", s.questsPerHour());
        return m;
    }
}
