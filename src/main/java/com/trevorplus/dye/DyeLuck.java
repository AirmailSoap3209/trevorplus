package com.trevorplus.dye;

import com.trevorplus.quests.Rarity;
import com.trevorplus.storage.FlatJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dye luck tracker. The Pelt Dye has NO pity: every completed quest is an independent roll
 * with a per-rarity chance, multiplied by Vincent's 2x/3x when active. So this tracks:
 *  - base progress (completed quests since the last dye, unmultiplied),
 *  - multiplied progress (sum of multipliers, "quest-equivalents"),
 *  - the probability of having seen at least one dye by now,
 *  - expected/median quests per dye, and lifetime luck versus expectation.
 * Nothing here is a guarantee; all of it is an estimate from locally observed quests.
 */
public final class DyeLuck {
    public record DyeDrop(long wallMs, long baseQuestsSince, double effectiveQuestsSince,
                          double chanceBeforeDrop, int multiplier) {}

    private long baseQuests;
    private double effectiveQuests;
    private double logSurvival;      // sum of ln(1-p) since the last dye
    private double hazardSince;      // sum of p since the last dye
    private long questsLifetime;
    private double hazardLifetime;   // expected dyes so far
    private int dyesFound;
    private boolean estimated;       // true after a manual adjustment

    public static double probability(Rarity r, int multiplier) {
        return Math.min(0.999999, (double) multiplier / r.dyeOneIn);
    }

    public static double averageProbability(int multiplier, int elusiveWeight) {
        double totalWeight = 0;
        double sum = 0;
        for (Rarity r : Rarity.values()) {
            int w = r == Rarity.ELUSIVE ? elusiveWeight : r.weight;
            totalWeight += w;
            sum += w * probability(r, multiplier);
        }
        return sum / totalWeight;
    }

    public static double expectedQuestsPerDye(int multiplier, int elusiveWeight) {
        return 1.0 / averageProbability(multiplier, elusiveWeight);
    }

    /** Number of quests after which there is a 50% chance of having found a dye. */
    public static double medianQuestsPerDye(int multiplier, int elusiveWeight) {
        return Math.log(2) / -Math.log1p(-averageProbability(multiplier, elusiveWeight));
    }

    public void recordQuest(Rarity r, int multiplier) {
        double p = probability(r, multiplier);
        baseQuests++;
        effectiveQuests += multiplier;
        hazardSince += p;
        hazardLifetime += p;
        questsLifetime++;
        logSurvival += Math.log1p(-p);
    }

    public DyeDrop recordDye(long wallMs, int multiplier) {
        DyeDrop d = new DyeDrop(wallMs, baseQuests, effectiveQuests, chanceSinceLastDye(), multiplier);
        dyesFound++;
        baseQuests = 0;
        effectiveQuests = 0;
        logSurvival = 0;
        hazardSince = 0;
        estimated = false;
        return d;
    }

    /** Manual sync: assumes 1x and the average rarity mix for the quests entered. Marked as estimated. */
    public void setQuestsSinceDye(long n, int elusiveWeight) {
        double p = averageProbability(1, elusiveWeight);
        baseQuests = Math.max(0, n);
        effectiveQuests = baseQuests;
        hazardSince = baseQuests * p;
        logSurvival = baseQuests * Math.log1p(-p);
        estimated = true;
    }

    public double chanceSinceLastDye() { return -Math.expm1(logSurvival); }
    public long baseQuestsSinceDye() { return baseQuests; }
    public double effectiveQuestsSinceDye() { return effectiveQuests; }
    public double expectedDyesSinceLast() { return hazardSince; }
    public long questsLifetime() { return questsLifetime; }
    public double expectedDyesLifetime() { return hazardLifetime; }
    public int dyesFound() { return dyesFound; }
    public boolean isEstimated() { return estimated; }

    /** dyes found / dyes expected; above 1 is lucky. NaN until there is any expectation. */
    public double luckRatio() { return hazardLifetime > 0 ? dyesFound / hazardLifetime : Double.NaN; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("luck.base", baseQuests);
        m.put("luck.eff", effectiveQuests);
        m.put("luck.logS", logSurvival);
        m.put("luck.hazS", hazardSince);
        m.put("luck.qLife", questsLifetime);
        m.put("luck.hazLife", hazardLifetime);
        m.put("luck.dyes", dyesFound);
        m.put("luck.est", estimated);
        return m;
    }

    public static DyeLuck fromMap(Map<String, Object> m) {
        DyeLuck d = new DyeLuck();
        d.baseQuests = FlatJson.lng(m, "luck.base", 0);
        d.effectiveQuests = FlatJson.dbl(m, "luck.eff", 0);
        d.logSurvival = FlatJson.dbl(m, "luck.logS", 0);
        d.hazardSince = FlatJson.dbl(m, "luck.hazS", 0);
        d.questsLifetime = FlatJson.lng(m, "luck.qLife", 0);
        d.hazardLifetime = FlatJson.dbl(m, "luck.hazLife", 0);
        d.dyesFound = (int) FlatJson.lng(m, "luck.dyes", 0);
        d.estimated = FlatJson.bool(m, "luck.est", false);
        return d;
    }
}
