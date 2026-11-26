package com.trevorplus;

import com.trevorplus.config.Settings;
import com.trevorplus.core.Clock;
import com.trevorplus.core.EngineListener;
import com.trevorplus.core.TrapperEngine;
import com.trevorplus.dye.DyeLuck;
import com.trevorplus.entities.NametagInfo;
import com.trevorplus.entities.NametagParser;
import com.trevorplus.events.TrevorMessage;
import com.trevorplus.events.TrevorParser;
import com.trevorplus.navigation.Navigator;
import com.trevorplus.navigation.Navigator.NavAction;
import com.trevorplus.navigation.Navigator.Type;
import com.trevorplus.quests.*;
import com.trevorplus.statistics.SessionStats;
import com.trevorplus.storage.FlatJson;
import com.trevorplus.storage.HistoryStore;
import com.trevorplus.theodolite.HintTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dependency-free test runner (JUnit is not available offline). Port to JUnit 5 once Gradle is set up. */
public final class TestMain {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL: " + name); }
    }
    static void eq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) System.out.println("   expected=" + expected + " actual=" + actual);
        check(name, ok);
    }
    static void near(String name, double expected, double actual, double eps) {
        boolean ok = Math.abs(expected - actual) <= eps;
        if (!ok) System.out.println("   expected=" + expected + " actual=" + actual);
        check(name, ok);
    }

    static final class FakeClock implements Clock {
        long mono = 1_000_000L, wall = 1_700_000_000_000L;
        public long monoMs() { return mono; }
        public long wallMs() { return wall; }
        void add(long ms) { mono += ms; wall += ms; }
    }

    static final class Rig {
        final FakeClock clock = new FakeClock();
        final Settings settings = new Settings();
        final List<QuestRecord> finished = new ArrayList<>();
        final List<DyeLuck.DyeDrop> dyes = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        double[] pos = {0, 64, 0};
        int ready;
        TrapperEngine engine;

        Rig(HistoryStore store) {
            engine = new TrapperEngine(clock, settings, store, () -> pos, new EngineListener() {
                @Override public void onQuestFinished(QuestRecord r) { finished.add(r); }
                @Override public void onDye(DyeLuck.DyeDrop d) { dyes.add(d); }
                @Override public void onCooldownReady() { ready++; }
            }, logs::add);
        }
        Rig() { this(null); }

        /** Advance time in 1 second steps, ticking like the game would. */
        void advance(long ms) {
            long left = ms;
            while (left > 0) {
                long step = Math.min(1000, left);
                clock.add(step);
                engine.tick();
                left -= step;
            }
        }
        void say(String line) { engine.onMessage(line); }
    }

    static final String ACCEPT_OASIS = "[NPC] Trevor: You can find your TRACKABLE animal near the Oasis.";
    static final String ACCEPT_SETTLEMENT = "[NPC] Trevor: You can find your UNTRACKABLE animal near the Desert Settlement.";

    public static void main(String[] args) throws Exception {
        testParser();
        testZone();
        testPresence();
        testCooldown();
        testQuestTimingAndStats();
        testDedupeReplaceExpiry();
        testSelfDeathAndTimingSetting();
        testBoardReconcile();
        testPause();
        testDyeLuck();
        testDyeEngine();
        testNavigator();
        testTheodolite();
        testNametag();
        testStorage();
        testRestartKeepsLuck();
        testHudAndBoard();
        testSpawns();
        testPromptAndAutoAccept();
        testBearing();
        testProjectionAndRichHud();
        testAlertsAndShuriken();
        testRingsAndShurikenFallback();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void testCooldown() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.advance(20_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        check("cooldown starts after kill", r.engine.cooldownRemainingMs() > 29_000);
        r.advance(10_000);
        check("cooldown counts down", r.engine.cooldownRemainingMs() > 19_000 && r.engine.cooldownRemainingMs() <= 20_000);
        check("not ready yet", r.ready == 0);
        r.advance(21_000);
        check("ready fires once", r.ready == 1);
        r.advance(30_000);
        check("ready not repeated", r.ready == 1);
        check("hud ready text", String.join("|", com.trevorplus.core.HudLines.build(r.engine)).contains("No active quest"));

        Rig q = new Rig();
        q.say(ACCEPT_OASIS);
        q.advance(5_000);
        q.say("Killing the animal rewarded you 2 pelts.");
        q.say(ACCEPT_OASIS);                       // took the next quest early, no ready ping
        q.advance(60_000);
        check("no ready ping during a quest", q.ready == 0);

        Rig s = new Rig();
        s.settings.cooldownSeconds = 0;
        s.say(ACCEPT_OASIS);
        s.say("Killing the animal rewarded you 2 pelts.");
        check("cooldown disabled", s.engine.cooldownRemainingMs() == 0);
        s.say("[NPC] Trevor: Try coming back in 12s!");
        s.advance(13_000);
        check("server cooldown still pings", s.ready == 1);
    }

    static void testPresence() {
        com.trevorplus.quests.Presence p = new com.trevorplus.quests.Presence();
        p.update(0, "Glowing Mushroom Cave", false);
        check("shown in the islands", p.show(1_000));
        p.update(10_000, "Village", false);
        check("still shown just after leaving", p.show(60_000));
        check("hidden after a minute away", !p.show(80_000));
        p.update(81_000, null, false);
        check("unreadable zone does not bring it back", !p.show(82_000));
        p.update(90_000, "Oasis", false);
        check("shown again on return", p.show(91_000));
        p.update(95_000, "Village", true);
        p.update(200_000, "Hub", true);
        check("active quest keeps it shown", p.show(230_000));
        com.trevorplus.quests.Presence q = new com.trevorplus.quests.Presence();
        q.update(0, null, false);
        q.update(500_000, null, false);
        check("no sidebar at all stays visible", q.show(600_000));
        com.trevorplus.quests.Presence g = new com.trevorplus.quests.Presence();
        g.setGraceMs(120_000);
        g.update(0, "Oasis", false);
        g.update(1_000, "Village", false);
        check("longer grace keeps it shown", g.show(100_000));
        check("longer grace still ends", !g.show(130_000));
        Settings ss = Settings.fromMap(new Settings().toMap());
        check("new settings default", ss.autoHideSeconds == 60 && ss.msgFound && ss.msgAccepted && ss.msgWarp && ss.msgDye);
        check("trapper den is farming", com.trevorplus.quests.Presence.isFarmingZone("Trapper's Den"));
        check("hub is not farming", !com.trevorplus.quests.Presence.isFarmingZone("Village"));
    }

    static void testZone() {
        eq("zone symbol fallback", "Glowing Mushroom Cave",
                com.trevorplus.quests.ZoneReader.current(java.util.List.of("SKYBLOCK", "Early Spring 4th", "4:50am", " \u2692 Glowing Mushroom Cave", "Purse: 60,086,474")));
        eq("zone skips tracker", null,
                com.trevorplus.quests.ZoneReader.current(java.util.List.of("Tracker Mob Location:", "Glowing Mushroom Cave", "Bits: 70")));
        eq("zone with colour codes", "Hub",
                com.trevorplus.quests.ZoneReader.current(java.util.List.of("\u00a77\u23e3 \u00a7bHub")));
        java.util.List<String> sb = java.util.List.of("SKYBLOCK", "Late Summer 5th", " \u23e3 Mushroom Gorge", "Purse: 1");
        check("zone read", "Mushroom Gorge".equals(com.trevorplus.quests.ZoneReader.current(sb)));
        check("zone absent", com.trevorplus.quests.ZoneReader.current(java.util.List.of("a", "b")) == null);
        check("zone same", com.trevorplus.quests.ZoneReader.same("The Oasis", "oasis"));
        check("zone differs", !com.trevorplus.quests.ZoneReader.same("Oasis", "Mushroom Gorge"));
        java.util.List<com.trevorplus.core.HudLines.Line> z = com.trevorplus.core.HudLines.buildZone("Oasis", true, "Oasis");
        check("zone hud match", z.get(z.size() - 1).text().contains("In the mob"));
        check("zone hud idle", com.trevorplus.core.HudLines.buildZone(null, false, "Hub").get(1).text().contains("no active quest"));
        Settings st = Settings.fromMap(new Settings().toMap());
        check("zone settings roundtrip", st.zoneVisible && st.finderX == -1 && st.zoneY == 200);
    }

    static void testParser() {
        TrevorParser p = new TrevorParser();
        eq("accept", Optional.of(new TrevorMessage.QuestAccepted(Rarity.TRACKABLE, "Oasis")), p.parse(ACCEPT_OASIS));
        eq("accept w/ color codes", Optional.of(new TrevorMessage.QuestAccepted(Rarity.TRACKABLE, "Desert Mountain")),
                p.parse("[NPC] Trevor: You can find your TRACKABLE animal near the \u00a7eDesert Mountain\u00a7r."));
        eq("accept multiword area", Optional.of(new TrevorMessage.QuestAccepted(Rarity.ENDANGERED, "Overgrown Mushroom Cave")),
                p.parse("[NPC] Trevor: You can find your ENDANGERED animal near the Overgrown Mushroom Cave."));
        eq("accept unknown rarity", Optional.of(new TrevorMessage.QuestAccepted(null, "Oasis")),
                p.parse("[NPC] Trevor: You can find your MYTHIC animal near the Oasis."));
        eq("kill", Optional.of(new TrevorMessage.KillReward(2)), p.parse("Killing the animal rewarded you 2 pelts."));
        eq("kill singular", Optional.of(new TrevorMessage.KillReward(1)), p.parse("Killing the animal rewarded you 1 pelt."));
        eq("self death", Optional.of(new TrevorMessage.SelfDeathReward(8)),
                p.parse("Your mob died randomly, you are rewarded 8 pelts."));
        eq("return prompt", Optional.of(new TrevorMessage.ReturnPrompt()),
                p.parse("Return to the Trapper soon to get a new animal to hunt!"));
        eq("cooldown", Optional.of(new TrevorMessage.Cooldown(17)), p.parse("[NPC] Trevor: Try coming back in 17s!"));
        eq("already active", Optional.of(new TrevorMessage.AlreadyActive()),
                p.parse("[NPC] Trevor: You already have a mob spawned! Hunt that mob first!"));
        eq("out of time", Optional.of(new TrevorMessage.OutOfTime()),
                p.parse("You ran out of time and the animal disappeared!"));
        eq("hint above", Optional.of(new TrevorMessage.Hint(25, 30, false)),
                p.parse("The target is around 25 blocks above, at a 30 degrees angle!"));
        eq("hint below", Optional.of(new TrevorMessage.Hint(-60, 30, false)),
                p.parse("The target is around 60 blocks below, at a 30 degrees angle!"));
        eq("hint exact", Optional.of(new TrevorMessage.Hint(0, 0, true)), p.parse("You are at the exact height!"));
        eq("dye with rank", Optional.of(new TrevorMessage.DyeFound("CharlesMa529")),
                p.parse("WOW! [VIP+] CharlesMa529 found a Pelt Dye!"));
        eq("dye without rank", Optional.of(new TrevorMessage.DyeFound("Steve")), p.parse("WOW! Steve found a Pelt Dye!"));
        check("noise ignored", p.parse("The animal sent you flying!").isEmpty());
        check("player spoof ignored", p.parse("[MVP+] Bob: Killing the animal rewarded you 99 pelts.").isEmpty());
        check("player spoof dye ignored", p.parse("Guild > [MVP+] Bob: WOW! Bob found a Pelt Dye!").isEmpty());
        check("null safe", p.parse(null).isEmpty());
    }

    static void testQuestTimingAndStats() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.advance(83_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.say("Return to the Trapper soon to get a new animal to hunt!");
        eq("first record count", 1, r.finished.size());
        QuestRecord a = r.finished.get(0);
        eq("duration 1", 83_000L, a.durationMs());
        eq("outcome 1", Outcome.KILLED, a.outcome());
        eq("pelts 1", 2, a.pelts());
        eq("rarity 1", Rarity.TRACKABLE, a.rarity());
        eq("area 1", Area.OASIS, a.area());
        check("return prompt after reward does not create a record", r.finished.size() == 1);

        r.advance(20 * 60_000);          // long idle, must not count as quest time
        r.say(ACCEPT_SETTLEMENT);
        r.advance(40_000);
        r.say("Killing the animal rewarded you 4 pelts.");
        QuestRecord b = r.finished.get(1);
        eq("duration 2 excludes the gap", 40_000L, b.durationMs());

        SessionStats.Snapshot s = r.engine.sessionSnapshot();
        eq("quests", 2, s.quests());
        eq("pelts", 6L, s.pelts());
        near("avg", 61_500, s.avgMs(), 0.01);
        eq("fastest", 40_000L, s.fastestMs());
        eq("slowest", 83_000L, s.slowestMs());
        near("avg trackable", 83_000, s.avgMsByRarity().get(Rarity.TRACKABLE), 0.01);
        near("avg untrackable", 40_000, s.avgMsByRarity().get(Rarity.UNTRACKABLE), 0.01);
        // idle capped at 5 min: 83s + 300s + 40s
        eq("session time with idle cap", 423_000L, s.elapsedMs());
        near("pelts/hour", 6 * 3600.0 / 423.0, s.peltsPerHour(), 0.001);
        near("pelts/min", 6 * 60.0 / 423.0, s.peltsPerMinute(), 0.001);
        near("pelts/quest", 3.0, s.peltsPerQuest(), 1e-9);
        near("quests/hour", 2 * 3600.0 / 423.0, s.questsPerHour(), 0.001);
    }

    static void testDedupeReplaceExpiry() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.advance(30_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.clock.add(50);
        r.say("Killing the animal rewarded you 2 pelts.");   // duplicate delivery
        eq("duplicate reward dropped", 1, r.finished.size());

        // replaced quest -> LOST
        r.advance(5_000);
        r.say(ACCEPT_OASIS);
        r.advance(10_000);
        r.say(ACCEPT_SETTLEMENT);
        eq("replaced quest closed", 2, r.finished.size());
        eq("replaced outcome", Outcome.LOST, r.finished.get(1).outcome());

        // local expiry with no server message
        r.advance(QuestTracker.MAX_QUEST_MS + QuestTracker.EXPIRE_GRACE_MS + 2000);
        eq("local expiry", 3, r.finished.size());
        eq("expiry outcome", Outcome.EXPIRED, r.finished.get(2).outcome());
        check("tracker idle after expiry", !r.engine.tracker().isActive());

        // server expiry message
        r.say(ACCEPT_OASIS);
        r.advance(60_000);
        r.say("You ran out of time and the animal disappeared!");
        eq("server expiry", Outcome.EXPIRED, r.finished.get(3).outcome());

        SessionStats.Snapshot s = r.engine.sessionSnapshot();
        eq("quests count only completions", 1, s.quests());
        eq("lost counted", 1, s.lost());
        eq("expired counted", 2, s.expired());
        near("failed quests do not affect average", 30_000, s.avgMs(), 0.01);
    }

    static void testSelfDeathAndTimingSetting() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.advance(20_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.advance(3_000);
        r.say("[NPC] Trevor: You can find your ENDANGERED animal near the Oasis.");
        r.advance(100_000);
        r.say("Your mob died randomly, you are rewarded 8 pelts.");
        eq("self died outcome", Outcome.SELF_DIED, r.finished.get(1).outcome());
        SessionStats.Snapshot s = r.engine.sessionSnapshot();
        eq("quests incl self death", 2, s.quests());
        eq("pelts incl self death", 10L, s.pelts());
        near("avg includes self death by default", 60_000, s.avgMs(), 0.01);
        r.settings.timeSelfDeaths = false;
        near("avg excludes self death when disabled", 20_000, r.engine.sessionSnapshot().avgMs(), 0.01);
        eq("self death did not advance dye luck", 1L, r.engine.luck().questsLifetime());
        r.settings.selfDeathCountsForDye = true;
        r.advance(3000);
        r.say("[NPC] Trevor: You can find your ENDANGERED animal near the Oasis.");
        r.advance(1000);
        r.say("Your mob died randomly, you are rewarded 8 pelts.");
        eq("self death counts for dye when enabled", 2L, r.engine.luck().questsLifetime());
    }

    static void testBoardReconcile() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        for (int i = 0; i < 5; i++) { r.advance(1000); r.engine.onTrackerBoard(true, "Oasis"); }
        check("still active while board shows quest", r.engine.tracker().isActive());
        for (int i = 0; i < 12; i++) { r.advance(1000); r.engine.onTrackerBoard(false, null); }
        check("closed after board lost the quest", !r.engine.tracker().isActive());
        eq("lost outcome from board", Outcome.LOST, r.finished.get(r.finished.size() - 1).outcome());

        // slow scoreboard right after accept must not close the quest
        Rig r2 = new Rig();
        r2.say(ACCEPT_OASIS);
        for (int i = 0; i < 15; i++) { r2.advance(1000); r2.engine.onTrackerBoard(false, null); }
        check("never-seen board does not abandon", r2.engine.tracker().isActive());

        // no phantom quest right after a finish
        Rig r3 = new Rig();
        r3.say(ACCEPT_OASIS);
        r3.advance(10_000);
        r3.say("Killing the animal rewarded you 2 pelts.");
        for (int i = 0; i < 8; i++) { r3.advance(1000); r3.engine.onTrackerBoard(true, "Oasis"); }
        check("no phantom recovery after finish", !r3.engine.tracker().isActive());

        // genuine recovery after restart
        Rig r4 = new Rig();
        for (int i = 0; i < 7; i++) { r4.advance(1000); r4.engine.onTrackerBoard(true, "Glowing Mushroom Cave"); }
        check("recovered from board", r4.engine.tracker().isActive());
        eq("recovered area", "Glowing Mushroom Cave", r4.engine.tracker().current().get().areaName());
        check("recovered quest is untimed", !r4.engine.tracker().current().get().timed());
        r4.say("Killing the animal rewarded you 6 pelts.");
        QuestRecord rec = r4.finished.get(0);
        check("recovered record untimed", !rec.timed());
        eq("recovered pelts counted", 6, rec.pelts());
        eq("untimed excluded from timing", 0.0, r4.engine.sessionSnapshot().avgMs());
        eq("untimed still counted as a quest", 1, r4.engine.sessionSnapshot().quests());

        // reward without any accept seen
        Rig r5 = new Rig();
        r5.say("Killing the animal rewarded you 4 pelts.");
        eq("reward without accept counted", 4L, r5.engine.sessionSnapshot().pelts());

        // disconnect mid quest
        Rig r6 = new Rig();
        r6.say(ACCEPT_OASIS);
        r6.advance(5000);
        r6.engine.onWorldLeave();
check("server hop saves nothing", r6.finished.isEmpty());
        check("server hop clears the quest", r6.engine.tracker().current().isEmpty());
        eq("server hop not counted", 0, r6.engine.sessionSnapshot().quests());
        r6.say(ACCEPT_OASIS);
        r6.advance(3000);
        r6.say("Killing the animal rewarded you 2 pelts.");
        r6.engine.onWorldLeave();
        check("hop clears cooldown", r6.engine.cooldownRemainingMs() == 0);
        eq("earlier quests kept after a hop", 1, r6.engine.sessionSnapshot().quests());
    }

    static void testPause() {
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.advance(10_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.engine.pauseSession();
        r.advance(60_000);
        r.say(ACCEPT_OASIS);
        r.advance(10_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        SessionStats.Snapshot s = r.engine.sessionSnapshot();
        eq("paused quest excluded from session", 1, s.quests());
        eq("paused session time frozen", 10_000L, s.elapsedMs());
        eq("paused quest still in history callbacks", 2, r.finished.size());
        eq("paused quest still advanced dye luck", 2L, r.engine.luck().questsLifetime());
        r.engine.resumeSession();
        r.advance(1000);
        eq("resumed session continues", 11_000L, r.engine.sessionSnapshot().elapsedMs());
        r.engine.resetSession();
        eq("reset clears quests", 0, r.engine.sessionSnapshot().quests());
        eq("reset clears time", 0L, r.engine.sessionSnapshot().elapsedMs());
    }

    static void testDyeLuck() {
        near("expected quests per dye 1x", 15_781.25, DyeLuck.expectedQuestsPerDye(1, 6), 0.01);
        near("expected quests per dye 3x", 5_260.4167, DyeLuck.expectedQuestsPerDye(3, 6), 0.01);
        check("slayer 9 weight raises odds", DyeLuck.averageProbability(1, 7) > DyeLuck.averageProbability(1, 6));
        near("median < expected", 10_938.7, DyeLuck.medianQuestsPerDye(1, 6), 5.0);
        near("p trackable", 2e-5, DyeLuck.probability(Rarity.TRACKABLE, 1), 1e-12);
        near("p elusive 3x", 0.0012, DyeLuck.probability(Rarity.ELUSIVE, 3), 1e-12);

        DyeLuck d = new DyeLuck();
        for (int i = 0; i < 100; i++) d.recordQuest(Rarity.TRACKABLE, 1);
        near("chance after 100 trackable", 1 - Math.pow(1 - 2e-5, 100), d.chanceSinceLastDye(), 1e-12);
        eq("base progress", 100L, d.baseQuestsSinceDye());
        for (int i = 0; i < 10; i++) d.recordQuest(Rarity.ELUSIVE, 3);
        eq("base counts each quest once", 110L, d.baseQuestsSinceDye());
        near("effective counts multiplier", 130.0, d.effectiveQuestsSinceDye(), 1e-9);
        near("expected dyes lifetime", 100 * 2e-5 + 10 * 0.0012, d.expectedDyesLifetime(), 1e-12);
        DyeLuck.DyeDrop drop = d.recordDye(5L, 3);
        eq("drop base", 110L, drop.baseQuestsSince());
        eq("reset after dye", 0L, d.baseQuestsSinceDye());
        near("chance resets", 0.0, d.chanceSinceLastDye(), 1e-12);
        eq("dyes found", 1, d.dyesFound());
        d.setQuestsSinceDye(5000, 6);
        check("manual adjust flagged as estimate", d.isEstimated());
        near("manual adjust chance", 1 - Math.pow(1 - DyeLuck.averageProbability(1, 6), 5000), d.chanceSinceLastDye(), 1e-9);
        DyeLuck copy = DyeLuck.fromMap(FlatJson.decode(FlatJson.encode(d.toMap())));
        near("persisted chance", d.chanceSinceLastDye(), copy.chanceSinceLastDye(), 1e-12);
        eq("persisted dyes", d.dyesFound(), copy.dyesFound());
    }

    static void testDyeEngine() {
        Rig r = new Rig();
        r.settings.playerName = "Tester";
        r.settings.setMultiplier(3);
        r.say(ACCEPT_OASIS);
        r.advance(10_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.say("WOW! [MVP+] Someone found a Pelt Dye!");
        eq("other players' dye ignored", 0, r.dyes.size());
        r.advance(1000);
        r.say("WOW! [MVP+] Tester found a Pelt Dye!");
        eq("own dye recorded", 1, r.dyes.size());
        eq("quest counted before reset", 1L, r.dyes.get(0).baseQuestsSince());
        eq("multiplier stored on drop", 3, r.dyes.get(0).multiplier());
        eq("luck reset", 0L, r.engine.luck().baseQuestsSinceDye());
        near("record used 3x", 3.0 / 50_000, r.finished.get(0).multiplier() * 1.0 / 50_000, 1e-12);

        // dye line arrives BEFORE the reward line
        r.advance(30_000);
        r.say(ACCEPT_OASIS);
        r.advance(10_000);
        r.say("WOW! Tester found a Pelt Dye!");
        r.advance(1000);
        eq("dye waits for reward line", 1, r.dyes.size());
        r.say("Killing the animal rewarded you 2 pelts.");
        eq("dye applied after reward", 2, r.dyes.size());
        eq("quest belongs to streak the dye ended", 1L, r.dyes.get(1).baseQuestsSince());
        eq("luck reset after both", 0L, r.engine.luck().baseQuestsSinceDye());

        // multiplier change mid-session
        r.settings.setMultiplier(2);
        r.advance(10_000);
        r.say(ACCEPT_OASIS);
        r.advance(5_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        eq("multiplier change applies to later quests", 2, r.finished.get(r.finished.size() - 1).multiplier());
        near("effective progress", 2.0, r.engine.luck().effectiveQuestsSinceDye(), 1e-9);

        // unset username never records
        Rig r2 = new Rig();
        r2.say("WOW! Tester found a Pelt Dye!");
        r2.advance(10_000);
        eq("no username -> ignored", 0, r2.dyes.size());
    }

    static void testNavigator() {
        Settings s = new Settings();
        Navigator n = new Navigator(s);
        long t = 0;
        NavAction a = n.decide(new Navigator.Context(false, false, null), t);
        eq("idle -> trapper", Type.WARP_TRAPPER, a.type());
        eq("trapper command", "/warp trapper", a.command());
        eq("throttled", Type.NONE, n.decide(new Navigator.Context(false, false, null), t + 1000).type());
        a = n.decide(new Navigator.Context(true, false, null), t + 4000);
        eq("prompt -> accept", Type.ACCEPT_PROMPT, a.type());
        a = n.decide(new Navigator.Context(false, true, "Oasis"), t + 8000);
        eq("oasis warp", "/warp desert", a.command());
        a = n.decide(new Navigator.Context(false, true, "Overgrown Mushroom Cave"), t + 12000);
        eq("overgrown warp", "/warp glowing", a.command());
        a = n.decide(new Navigator.Context(false, true, "Desert Mountain"), t + 16000);
        eq("mountain warp", "/warp trapper", a.command());
        a = n.decide(new Navigator.Context(false, true, "Mystery Cave"), t + 20000);
        eq("unknown area -> no action", Type.NONE, a.type());
        s.warpOverrides.put("OASIS", "/warp custom");
        a = n.decide(new Navigator.Context(false, true, "Oasis"), t + 24000);
        eq("override applies", "/warp custom", a.command());
        s.navWarpTrapper = false;
        eq("trapper warp can be disabled", Type.NONE, n.decide(new Navigator.Context(false, false, null), t + 28000).type());
        check("describe", n.describe(new Navigator.Context(false, true, "Gorge")).contains("Unknown")
                || n.describe(new Navigator.Context(false, true, "Mushroom Gorge")).contains("/warp"));

        // engine-level
        Rig r = new Rig();
        eq("engine idle nav", Type.WARP_TRAPPER, r.engine.onNavKey(false).type());
        r.advance(4000);
        r.say("[NPC] Trevor: You can find your TRACKABLE animal near the Glowing Mushroom Cave.");
        r.advance(4000);
        eq("engine active nav", "/warp glowing", r.engine.onNavKey(false).command());
    }

    static void testTheodolite() {
        near("radius 25 @30deg", 43.30127, HintTracker.radiusFor(25, 30), 1e-4);
        near("radius 60 @45deg", 60.0, HintTracker.radiusFor(60, 45), 1e-9);
        check("0 degrees ring is huge", HintTracker.radiusFor(5, 0) > HintTracker.MAX_RADIUS);

        HintTracker h = new HintTracker();
        h.addHint(0, 64, 0, 5, 0, false);
        eq("0 degree hint adds no ring", 0, h.rings().size());
        check("but still sets a Y band", h.hasY());
        near("Y estimate", 69.0, h.estimatedY(), 1e-9);

        h.reset();
        h.addHint(0, 64, 0, -10, 45, false);
        near("ring below", 10.0, h.rings().get(0).radius(), 1e-9);
        near("ring at mob Y", 54.0, h.rings().get(0).cy(), 1e-9);
        h.addHint(2, 64, 1, -11, 45, false);
        near("band narrows", 2.0, h.maxY() - h.minY(), 3.0);
        h.addHint(0, 70, 0, 0, 0, true);
        check("exact height sets band around player Y", h.estimatedY() > 60 && h.estimatedY() < 75);
        h.addHint(0, 200, 0, 0, 0, true);
        near("disjoint hint replaces band", 200.0, h.estimatedY(), 1e-9);

        // animal at (30, 40) from origin; used item at (0,0) and (60,0) -> both 50 away
        HintTracker t = new HintTracker();
        t.addHint(0, 64, 0, 50, 45, false);
        t.addHint(60, 64, 0, 50, 45, false);
        List<double[]> c = t.candidates();
        eq("two candidates", 2, c.size());
        boolean found = false;
        for (double[] p : c) if (Math.abs(p[0] - 30) < 1e-6 && Math.abs(p[1] - 40) < 1e-6) found = true;
        check("true position among candidates", found);
        near("distance to ring outside", 5.0, HintTracker.distanceToRing(t.rings().get(0), 55, 0), 1e-9);
        near("distance to ring inside", -10.0, HintTracker.distanceToRing(t.rings().get(0), 40, 0), 1e-9);
        check("fewer than two rings gives none", new HintTracker().candidates().isEmpty());
        // nearly-touching rings (rounded hints) still give a point
        List<double[]> near = HintTracker.intersect(new HintTracker.Ring(0, 0, 0, 30), new HintTracker.Ring(60, 0, 0, 28), 4.0);
        eq("near-tangent gives one point", 1, near.size());

        // through the engine: position supplier is used
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.pos = new double[] {10, 70, 10};
        r.say("The target is around 20 blocks below, at a 45 degrees angle!");
        near("engine ring radius", 20.0, r.engine.hints().rings().get(0).radius(), 1e-9);
        near("engine ring centre y", 50.0, r.engine.hints().rings().get(0).cy(), 1e-9);
        r.say(ACCEPT_SETTLEMENT);
        eq("new quest clears hints", 0, r.engine.hints().rings().size());
    }

    static void testNametag() {
        NametagParser p = new NametagParser();
        Optional<NametagInfo> a = p.parse("[Lv3] \u2764\u2764 Endangered Sheep 4,998/5,000\u2764 \u2605", true);
        check("sheep parsed", a.isPresent());
        eq("sheep rarity", Rarity.ENDANGERED, a.get().rarity());
        eq("sheep animal", Animal.SHEEP, a.get().animal());
        eq("sheep hp", 4998L, a.get().health());
        eq("sheep max", 5000L, a.get().maxHealth());
        eq("level", 3, a.get().level());
        check("plausible health", a.get().plausible());
        check("shuriken flag passed through", a.get().shuriken());
        Optional<NametagInfo> b = p.parse("[Lv1] Trackable Cow 100/100\u2764", false);
        eq("cow rarity", Rarity.TRACKABLE, b.get().rarity());
        check("no shuriken", !b.get().shuriken());
        Optional<NametagInfo> c = p.parse("Untrackable Pig 480/500", false);
        eq("untrackable not confused with trackable", Rarity.UNTRACKABLE, c.get().rarity());
        check("horse double health plausible", p.parse("[Lv1] Undetected Horse 2,000/2,000\u2764", false).get().plausible());
        check("cow double health not plausible", !p.parse("[Lv1] Undetected Cow 2,000/2,000\u2764", false).get().plausible());
        check("odd max health flagged", !p.parse("Elusive Rabbit 5/9999", false).get().plausible());
        check("non-trapper nametag ignored", p.parse("[Lv100] Zealot 13,000/13,000", false).isEmpty());
        check("null ignored", p.parse(null, false).isEmpty());

        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        r.engine.onNametag(p.parse("Endangered Sheep 100/5000", false).get());
        check("mismatched rarity ignored", r.engine.tracker().current().get().animal() == null);
        r.engine.onNametag(p.parse("Trackable Cow 100/100", false).get());
        eq("matching nametag teaches animal", Animal.COW, r.engine.tracker().current().get().animal());
        r.advance(10_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        eq("animal stored in record", Animal.COW, r.finished.get(0).animal());
        near("avg by animal", 10_000, r.engine.sessionSnapshot().avgMsByAnimal().get(Animal.COW), 0.01);
    }

    static void testStorage() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "quote\" back\\slash\nnewline\ttab \u00e9\u4e2d");
        m.put("l", 1_700_000_000_123L);
        m.put("d", 1.5e-7);
        m.put("neg", -42);
        m.put("b", true);
        Map<String, Object> back = FlatJson.decode(FlatJson.encode(m));
        eq("string roundtrip", m.get("s"), back.get("s"));
        eq("long roundtrip", 1_700_000_000_123L, back.get("l"));
        near("double roundtrip", 1.5e-7, (Double) back.get("d"), 1e-20);
        eq("neg roundtrip", -42L, back.get("neg"));
        eq("bool roundtrip", true, back.get("b"));
        boolean threw = false;
        try { FlatJson.decode("{\"a\":1,\"b\":"); } catch (IllegalArgumentException e) { threw = true; }
        check("truncated json rejected", threw);

        Path dir = Files.createTempDirectory("trapperplus-test");
        List<String> logs = new ArrayList<>();
        HistoryStore store = new HistoryStore(dir, logs::add);
        Rig r = new Rig(store);
        r.settings.playerName = "Tester";
        r.say(ACCEPT_OASIS);
        r.advance(12_000);
        r.say("Killing the animal rewarded you 2 pelts.");
        r.advance(3000);
        r.say(ACCEPT_SETTLEMENT);
        r.advance(9_000);
        r.say("Killing the animal rewarded you 4 pelts.");
        eq("two quests stored", 2, new HistoryStore(dir, logs::add).loadQuests().size());

        // simulate a crash that left a half-written line, then keep working
        Files.writeString(dir.resolve("quests.jsonl"), "{\"t0\":17000000", StandardOpenOption.APPEND);
        HistoryStore store2 = new HistoryStore(dir, logs::add);
        Rig r2 = new Rig(store2);
        r2.say(ACCEPT_OASIS);
        r2.advance(7_000);
        r2.say("Killing the animal rewarded you 2 pelts.");
        List<QuestRecord> all = new HistoryStore(dir, logs::add).loadQuests();
        eq("damaged line skipped, new record intact", 3, all.size());
        eq("loaded duration", 7_000L, all.get(2).durationMs());
        eq("loaded rarity", Rarity.TRACKABLE, all.get(0).rarity());
        eq("loaded area", "Desert Settlement", all.get(1).areaName());
        HistoryStore reader = new HistoryStore(dir, logs::add);
        reader.loadQuests();
        eq("one damaged line counted", 1, reader.skippedLines());

        r.engine.resetSession();
        eq("session summary saved", 1, new HistoryStore(dir, logs::add).loadSessions().size());
        check("state file written", Files.exists(dir.resolve("state.json")));
        check("no temp file left behind", !Files.exists(dir.resolve("state.json.tmp")));

        Files.writeString(dir.resolve("state.json"), "{garbage");
        eq("corrupt state ignored", 0, new HistoryStore(dir, logs::add).readJson("state.json").size());

        Settings s = new Settings();
        s.setMultiplier(7);
        eq("multiplier clamped", 3, s.multiplier);
        s.playerName = "Me";
        s.warpOverrides.put("OASIS", "/warp x");
        Settings s2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(s.toMap())));
        eq("settings roundtrip name", "Me", s2.playerName);
        eq("settings roundtrip multiplier", 3, s2.multiplier);
        eq("settings roundtrip warp", "/warp x", s2.warpCommandFor(Area.OASIS));
    }

    static void testRestartKeepsLuck() throws Exception {
        Path dir = Files.createTempDirectory("trapperplus-luck");
        HistoryStore store = new HistoryStore(dir, s -> {});
        Rig r = new Rig(store);
        for (int i = 0; i < 3; i++) {
            r.say(ACCEPT_OASIS);
            r.advance(5000);
            r.say("Killing the animal rewarded you 2 pelts.");
            r.advance(1000);
        }
        double chance = r.engine.luck().chanceSinceLastDye();
        // "restart": new engine over the same folder
        Rig r2 = new Rig(new HistoryStore(dir, s -> {}));
        eq("luck progress survives restart", 3L, r2.engine.luck().baseQuestsSinceDye());
        near("chance survives restart", chance, r2.engine.luck().chanceSinceLastDye(), 1e-15);
    }

    static void testHudAndBoard() {
        List<String> board = List.of("SKYBLOCK", "11/19/26 m181AE", "Late Winter 25th", "10:30am",
                "\u23e3 Overgrown Mushroom Cave", "", "Purse: 22,805,782", "Bits: 70", "",
                "Tracker Mob Location:", "Overgrown Mushroom Cave", "", "www.hypixel.net");
        com.trevorplus.quests.TrackerBoard.Result r = com.trevorplus.quests.TrackerBoard.parse(board);
        check("board active", r.active());
        eq("board area from next line", "Overgrown Mushroom Cave", r.area());
        eq("board inline with colour codes", "Oasis",
                com.trevorplus.quests.TrackerBoard.parse(List.of("\u00a7fTracker Mob Location: \u00a7aOasis")).area());
        check("board inactive without the line",
                !com.trevorplus.quests.TrackerBoard.parse(List.of("Purse: 5", "\u23e3 Oasis")).active());
        eq("key as last line has no area", null,
                com.trevorplus.quests.TrackerBoard.parse(List.of("Tracker Mob Location:")).area());

        eq("fmt 1:05", "1:05", com.trevorplus.core.Fmt.duration(65_000));
        eq("fmt hours", "1:01:01", com.trevorplus.core.Fmt.duration(3_661_000));
        eq("fmt negative", "0:00", com.trevorplus.core.Fmt.duration(-5));

        Rig h = new Rig();
        String idle = String.join("|", com.trevorplus.core.HudLines.build(h.engine));
        check("hud idle text", idle.contains("No active quest"));
        h.say("[NPC] Trevor: Try coming back in 17s!");
        check("hud cooldown text", String.join("|", com.trevorplus.core.HudLines.build(h.engine)).contains("cooldown 17s"));
        h.say(ACCEPT_OASIS);
        h.advance(83_000);
        String active = String.join("|", com.trevorplus.core.HudLines.build(h.engine));
        check("hud shows quest", active.contains("Trackable @ Oasis  1:23"));
        check("hud shows nav target", active.contains("Oasis (/warp desert)"));
        h.say("Killing the animal rewarded you 2 pelts.");
        String done = String.join("|", com.trevorplus.core.HudLines.build(h.engine));
        check("hud pelts", done.contains("Pelts 2"));
        check("hud quests", done.contains("Quests 1"));
        check("hud best time", done.contains("Best 1:23"));
        check("hud dye line", done.contains("Dye: 0.002% chance by now (1 quests, 1x)"));
        h.engine.pauseSession();
        check("hud paused tag", com.trevorplus.core.HudLines.build(h.engine).get(0).contains("[paused]"));

        Settings s = new Settings();
        s.hudVisible = false;
        s.hudX = 40;
        s.hudY = 55;
        Settings s2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(s.toMap())));
        check("hud settings roundtrip", !s2.hudVisible && s2.hudX == 40 && s2.hudY == 55);
        check("hud defaults", new Settings().hudVisible && new Settings().hudX == 6);
    }

    static final String ACCEPT_ELUSIVE = "[NPC] Trevor: You can find your ELUSIVE animal near the Oasis.";

    static void testSpawns() throws Exception {
        Rig r = new Rig();
        for (int i = 0; i < 3; i++) { r.say(ACCEPT_OASIS); r.say("Killing the animal rewarded you 2 pelts."); r.advance(61_000); }
        r.say(ACCEPT_SETTLEMENT); r.say("Killing the animal rewarded you 4 pelts."); r.advance(61_000);
        var sp = r.engine.spawns();
        eq("spawn trackable", 3L, sp.life(Rarity.TRACKABLE));
        eq("spawn untrackable", 1L, sp.life(Rarity.UNTRACKABLE));
        eq("spawn since elusive", 4L, sp.sinceElusive());
        eq("no gap yet", -1L, sp.lastGap());
        r.say(ACCEPT_ELUSIVE); r.say("Killing the animal rewarded you 16 pelts."); r.advance(61_000);
        eq("elusive counted", 1L, sp.life(Rarity.ELUSIVE));
        eq("gap between elusives", 4L, sp.lastGap());
        eq("since resets", 0L, sp.sinceElusive());
        for (int i = 0; i < 9; i++) { r.say(ACCEPT_OASIS); r.say("Killing the animal rewarded you 2 pelts."); r.advance(61_000); }
        r.say(ACCEPT_ELUSIVE); r.say("Killing the animal rewarded you 16 pelts."); r.advance(61_000);
        eq("second gap", 9L, sp.lastGap());
        eq("longest gap", 9L, sp.longestGap());
        check("average gap", Math.abs(sp.averageGap() - 6.5) < 1e-9);
        // duplicate accept line inside the dedupe window is not counted twice
        long before = sp.lifeTotal();
        r.say(ACCEPT_OASIS); r.say(ACCEPT_OASIS);
        eq("duplicate accept ignored", before + 1, sp.lifeTotal());
        // session reset keeps lifetime
        r.engine.resetSession();
        eq("session counts reset", 0L, sp.sessionTotal());
        check("lifetime kept", sp.lifeTotal() == before + 1);
        // persistence
        Path dir = Files.createTempDirectory("trevorplus-spawn");
        HistoryStore store = new HistoryStore(dir, m -> {});
        Rig a = new Rig(store);
        a.say(ACCEPT_OASIS); a.say("Killing the animal rewarded you 2 pelts."); a.advance(61_000);
        a.say(ACCEPT_ELUSIVE); a.say("Killing the animal rewarded you 16 pelts."); a.advance(61_000);
        a.say(ACCEPT_OASIS); a.say("Killing the animal rewarded you 2 pelts."); a.advance(61_000);
        Rig b = new Rig(new HistoryStore(dir, m -> {}));
        eq("persist trackable", 2L, b.engine.spawns().life(Rarity.TRACKABLE));
        eq("persist since", 1L, b.engine.spawns().sinceElusive());
        eq("persist gap", 1L, b.engine.spawns().lastGap());
        eq("persist session starts at 0", 0L, b.engine.spawns().sessionTotal());
        b.engine.setSpawnCounts(new long[] {23, 16, 12, 5, 1});
        b.engine.setSinceElusive(30);
        Rig c = new Rig(new HistoryStore(dir, m -> {}));
        eq("manual counts persist", 12L, c.engine.spawns().life(Rarity.UNDETECTED));
        eq("manual since persists", 30L, c.engine.spawns().sinceElusive());
        // dye state still intact in the same file
        check("state file still has luck", c.engine.luck() != null);
        // HUD text
        String hud = com.trevorplus.core.HudLines.buildSpawns(c.engine).stream()
                .map(com.trevorplus.core.HudLines.Line::text).reduce("", (x, y) -> x + "|" + y);
        check("hud2 elusive row", hud.contains("Elusive"));
        check("hud2 since", hud.contains("Since Elusive: 30"));
        check("hud2 expected", hud.contains("Expected: 1 in 101"));
        check("expected gap 7 weight", Math.round(com.trevorplus.core.HudLines.expectedGap(7)) == 87);
        check("rarity colours differ", com.trevorplus.core.HudLines.color(Rarity.ELUSIVE) != com.trevorplus.core.HudLines.color(Rarity.TRACKABLE));
    }

    static void testPromptAndAutoAccept() {
        String cmd = "/chatprompt abc-123 YES";
        Rig r = new Rig();
        r.settings.acceptMode = com.trevorplus.config.AcceptMode.AUTO.name();
        // prompt without Trevor speaking first is ignored
        r.engine.onQuestPrompt(cmd);
        check("prompt needs trevor line", !r.engine.promptFresh());
        r.say("[NPC] Trevor: You're a fine hunter, ready for a trapping task?");
        r.engine.onQuestPrompt(cmd);
        check("prompt fresh", r.engine.promptFresh());
        check("not before delay", r.engine.autoAcceptCommand() == null);
        r.clock.add(300);
        eq("auto-accept command", cmd, r.engine.autoAcceptCommand());
        check("only once", r.engine.autoAcceptCommand() == null && !r.engine.promptFresh());
        // setting off: never auto-sends, but the key can still use it
        Rig o = new Rig();
        o.say("[NPC] Trevor: Ready?");
        o.engine.onQuestPrompt(cmd);
        o.clock.add(1000);
        check("off by default", new Settings().accept() == com.trevorplus.config.AcceptMode.MANUAL && o.engine.autoAcceptCommand() == null);
        eq("key accepts prompt", Type.ACCEPT_PROMPT, o.engine.onNavKey().type());
        eq("key hands out command", cmd, o.engine.takePrompt());
        // expiry
        Rig e = new Rig();
        e.settings.acceptMode = com.trevorplus.config.AcceptMode.AUTO.name();
        e.say("[NPC] Trevor: Ready?");
        e.engine.onQuestPrompt(cmd);
        e.clock.add(6000);
        check("prompt expires", !e.engine.promptFresh() && e.engine.autoAcceptCommand() == null);
        // not while a quest is active
        Rig q = new Rig();
        q.settings.acceptMode = com.trevorplus.config.AcceptMode.AUTO.name();
        q.say(ACCEPT_OASIS);
        q.engine.onQuestPrompt(cmd);
        check("no prompt during quest", !q.engine.promptFresh());
        // accepting clears a stale prompt
        Rig s = new Rig();
        s.say("[NPC] Trevor: Ready?");
        s.engine.onQuestPrompt(cmd);
        s.say(ACCEPT_OASIS);
        check("accept clears prompt", s.engine.takePrompt() == null);
        Settings st = Settings.fromMap(FlatJson.decode(FlatJson.encode(new Settings().toMap())));
        check("new settings default", st.accept() == com.trevorplus.config.AcceptMode.MANUAL && st.highlightEnabled && st.hud2Visible && st.hud2Y == 130);
        Settings on = new Settings();
        on.acceptMode = com.trevorplus.config.AcceptMode.AUTO.name();
        on.hud2X = 50;
        Settings on2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(on.toMap())));
        check("new settings roundtrip", on2.accept() == com.trevorplus.config.AcceptMode.AUTO && on2.hud2X == 50);
    }

    static void testBearing() {
        var B = com.trevorplus.navigation.Bearing.class;
        // yaw 0 faces +Z (south); west (-X) is on the right
        check("ahead", Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 0, 0, 10)) < 1e-6);
        check("west is right", Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 0, -10, 0) - 90) < 1e-6);
        check("east is left", Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 0, 10, 0) + 90) < 1e-6);
        check("behind", Math.abs(Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 0, 0, -10)) - 180) < 1e-6);
        // facing west (yaw 90), a target to the west is straight ahead, to the north (-Z) is on the right
        check("yaw 90 ahead", Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 90, -10, 0)) < 1e-6);
        check("yaw 90 north right", com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 90, 0, -10) > 80);
        check("yaw wrap", Math.abs(com.trevorplus.navigation.Bearing.relativeDegrees(0, 0, 350, 0, 10) - 10) < 1e-6);
        check("arrow ahead", com.trevorplus.navigation.Bearing.arrow(5).startsWith("^"));
        check("arrow right", com.trevorplus.navigation.Bearing.arrow(40).equals("> right"));
        check("arrow left", com.trevorplus.navigation.Bearing.arrow(-40).equals("< left"));
        check("arrow behind", com.trevorplus.navigation.Bearing.arrow(170).startsWith("v"));
        check("height hint", com.trevorplus.navigation.Bearing.heightHint(10).equals("10 up")
                && com.trevorplus.navigation.Bearing.heightHint(-7).equals("7 down")
                && com.trevorplus.navigation.Bearing.heightHint(2).isEmpty());
    }

    static void testProjectionAndRichHud() {
        var P = com.trevorplus.navigation.Projection.class;
        // camera at origin looking south (+Z), pitch 0, fov 70, 16:9
        var ahead = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, 0, 0, 10);
        check("proj ahead centre", ahead.inFront() && Math.abs(ahead.nx()) < 1e-9 && Math.abs(ahead.ny()) < 1e-9 && Math.abs(ahead.depth() - 10) < 1e-9);
        var west = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, -2, 0, 10);
        check("proj west is right of centre", west.nx() > 0);
        var east = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, 2, 0, 10);
        check("proj east is left of centre", east.nx() < 0 && Math.abs(east.nx() + west.nx()) < 1e-9);
        var above = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, 0, 3, 10);
        check("proj above is up", above.ny() > 0);
        // vertical fov 70: a point at depth 10 and height 10*tan(35deg) sits on the top edge
        var edge = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, 0, 10 * Math.tan(Math.toRadians(35)), 10);
        check("proj top edge", Math.abs(edge.ny() - 1) < 1e-9 && edge.onScreen());
        var behind = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 0, 70, 16.0 / 9, 0, 0, -10);
        check("proj behind", !behind.inFront() && !behind.onScreen());
        // looking down 90 degrees, a point straight below is dead centre; one ahead (south) is up on screen
        var below = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 90, 70, 16.0 / 9, 0, -10, 0);
        check("proj straight down", below.inFront() && Math.abs(below.nx()) < 1e-9 && Math.abs(below.ny()) < 1e-9);
        var downAhead = com.trevorplus.navigation.Projection.project(0, 0, 0, 0, 90, 70, 16.0 / 9, 0, -10, 3);
        check("proj down and ahead is up", downAhead.ny() > 0 && Math.abs(downAhead.nx()) < 1e-9);
        // yaw 90 faces west: a target to the west is centred
        var w = com.trevorplus.navigation.Projection.project(0, 0, 0, 90, 0, 70, 16.0 / 9, -10, 0, 0);
        check("proj yaw 90 west ahead", Math.abs(w.nx()) < 1e-9 && w.inFront());

        Rig r = new Rig();
        var idle = com.trevorplus.core.HudLines.buildMain(r.engine);
        check("rich hud title", idle.get(0).kind() == com.trevorplus.core.HudLines.KIND_TITLE && idle.get(0).text().startsWith("TREVOR+"));
        check("rich hud has dividers", idle.stream().anyMatch(l -> l.kind() == com.trevorplus.core.HudLines.KIND_DIVIDER));
        r.say(ACCEPT_OASIS);
        r.advance(83_000);
        var active = com.trevorplus.core.HudLines.buildMain(r.engine);
        check("rich hud quest row", active.get(1).text().contains("Trackable") && active.get(1).text().contains("Oasis") && active.get(1).text().contains("1:23"));
        check("rich hud quest colour", active.get(1).segs().get(0).argb() == com.trevorplus.core.HudLines.color(Rarity.TRACKABLE));
        r.say("Killing the animal rewarded you 2 pelts.");
        String all = com.trevorplus.core.HudLines.buildMain(r.engine).stream().map(com.trevorplus.core.HudLines.Line::text).reduce("", (x, y) -> x + "|" + y);
        check("rich hud pelts", all.contains("Pelts 2"));
        check("rich hud dye", all.contains("Dye chance 0.002%"));
        Settings fs = Settings.fromMap(FlatJson.decode(FlatJson.encode(new Settings().toMap())));
        check("fov default", fs.fovDegrees == 70);
    }

    static void testAlertsAndShuriken() {
        var AQ = com.trevorplus.alerts.AlertQueue.class;
        check("alert elusive", com.trevorplus.alerts.AlertQueue.shouldAlert(Rarity.ELUSIVE, Rarity.ENDANGERED));
        check("alert endangered at min", com.trevorplus.alerts.AlertQueue.shouldAlert(Rarity.ENDANGERED, Rarity.ENDANGERED));
        check("no alert undetected", !com.trevorplus.alerts.AlertQueue.shouldAlert(Rarity.UNDETECTED, Rarity.ENDANGERED));
        check("no alert unknown rarity", !com.trevorplus.alerts.AlertQueue.shouldAlert(null, Rarity.TRACKABLE));
        check("alert min trackable", com.trevorplus.alerts.AlertQueue.shouldAlert(Rarity.TRACKABLE, Rarity.TRACKABLE));
        var q = new com.trevorplus.alerts.AlertQueue();
        check("empty queue", q.current(0) == null);
        q.push("ELUSIVE", 0xFFFF5555, 1000, 4000);
        var v0 = q.current(1000);
        check("alert shown", v0 != null && v0.text().equals("ELUSIVE") && v0.alpha() == 1.0 && v0.flashOn());
        check("alert flashes off", !q.current(1150).flashOn() && q.current(1300).flashOn());
        check("alert steady after flash phase", q.current(1000 + 1500 + 150).flashOn());
        var fade = q.current(1000 + 4000 - 300);
        check("alert fades", fade != null && Math.abs(fade.alpha() - 0.5) < 1e-9);
        check("alert expires", q.current(1000 + 4000) == null);
        q.push("A", 1, 0, 3000);
        q.push("B", 2, 500, 3000);
        check("newest alert wins", q.current(600).text().equals("B"));
        q.push("B", 2, 700, 3000);
        q.clear();
        check("clear", q.current(700) == null);
        q.push("X", 1, 0, 3000); q.push("X", 1, 100, 3000);
        q.push("Y", 1, 200, 100);
        check("duplicate suppressed, short alert expires", q.current(400).text().equals("X"));

        var R = new com.trevorplus.alerts.ShurikenReminder();
        check("no reminder without quest", !R.update(0, true, false, true));
        check("not instantly", !R.update(1000, true, true, true));
        check("not before settle", !R.update(2000, true, true, true));
        check("fires after settle", R.update(2600, true, true, true));
        check("not again at once", !R.update(5000, true, true, true));
        check("repeats later", R.update(2600 + 12_000, true, true, true));
        check("marker present resets", !R.update(20_000, true, true, false));
        check("flicker restarts settle", !R.update(30_000, true, true, true));
        check("disabled never fires", !R.update(60_000, false, true, true));
        R.update(61_000, true, false, false);
        check("new quest reminds again", !R.update(62_000, true, true, true) && R.update(63_600, true, true, true));

        var D = com.trevorplus.entities.ShurikenDetector.class;
        var plain = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("[Lv3] ", 0xAAAAAA),
                new com.trevorplus.entities.ShurikenDetector.Segment("Elusive Cow 10,000/10,000", 0x55FF55));
        check("no marker on plain tag", !com.trevorplus.entities.ShurikenDetector.hasMarker(plain, ""));
        var blue = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("\u2726 ", 0x5555FF),
                new com.trevorplus.entities.ShurikenDetector.Segment("Elusive Cow 10,000/10,000", 0x55FF55));
        check("blue symbol detected", com.trevorplus.entities.ShurikenDetector.hasMarker(blue, ""));
        eq("learn returns the symbol", "\u2726", com.trevorplus.entities.ShurikenDetector.learn(blue));
        check("learned exact match", com.trevorplus.entities.ShurikenDetector.hasMarker(blue, "\u2726"));
        check("learned marker absent", !com.trevorplus.entities.ShurikenDetector.hasMarker(plain, "\u2726"));
        var redSymbol = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("\u2764 ", 0xFF5555));
        check("red symbol is not a shuriken", !com.trevorplus.entities.ShurikenDetector.hasMarker(redSymbol, ""));
        var blueLetters = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("Cow", 0x5555FF));
        check("blue plain text is not a shuriken", !com.trevorplus.entities.ShurikenDetector.hasMarker(blueLetters, ""));
        var noColour = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("\u2726", -1));
        check("uncoloured symbol not matched without learning", !com.trevorplus.entities.ShurikenDetector.hasMarker(noColour, ""));
        check("uncoloured symbol matched when learned", com.trevorplus.entities.ShurikenDetector.hasMarker(noColour, "\u2726"));
        Settings as = new Settings();
        as.alertMin = "UNDETECTED"; as.alertsEnabled = false; as.shurikenReminder = false; as.shurikenMarker = "\u2726";
        Settings as2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(as.toMap())));
        check("alert settings roundtrip", as2.alertMinRarity() == Rarity.UNDETECTED && !as2.alertsEnabled && !as2.shurikenReminder && as2.shurikenMarker.equals("\u2726"));
        Settings bad = new Settings(); bad.alertMin = "nonsense";
        check("bad alert min falls back", bad.alertMinRarity() == Rarity.ENDANGERED);
        check("alert defaults", new Settings().alertsEnabled && new Settings().shurikenReminder && new Settings().shurikenMarker.isEmpty());
    }

    static void testRingsAndShurikenFallback() {
        var Seg = com.trevorplus.entities.ShurikenDetector.Segment.class;
        // the real tag from the user: "[Lv3]  Endangered Cow 4,998/5,000" + heart, with a star after the heart
        var tagWithStar = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("[Lv3]  Endangered Cow 4,998/5,000\u2764 ", 0xFFFFFF),
                new com.trevorplus.entities.ShurikenDetector.Segment("\u2605", -1));
        eq("fallback finds star after health", "\u2605", com.trevorplus.entities.ShurikenDetector.learn(tagWithStar));
        check("fallback marks tag", com.trevorplus.entities.ShurikenDetector.hasMarker(tagWithStar, ""));
        var tagNoStar = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("[Lv3]  Endangered Cow 4,998/5,000\u2764 ", 0xFFFFFF));
        check("heart alone is not a shuriken", !com.trevorplus.entities.ShurikenDetector.hasMarker(tagNoStar, ""));
        var sameSegment = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("Endangered Cow 4,998/5,000\u2764\u2605", -1));
        eq("star in the same segment", "\u2605", com.trevorplus.entities.ShurikenDetector.learn(sameSegment));
        var iconBefore = java.util.List.of(new com.trevorplus.entities.ShurikenDetector.Segment("\u2620 Endangered Cow 4,998/5,000\u2764", -1));
        check("icon before the health text is ignored", !com.trevorplus.entities.ShurikenDetector.hasMarker(iconBefore, ""));

        // ring points
        var ring = new HintTracker.Ring(100, 64, -50, 30);
        var pts = HintTracker.ringPoints(ring, 72);
        eq("ring point count", 73, pts.size());
        check("ring closed", Math.abs(pts.get(0)[0] - pts.get(72)[0]) < 1e-9 && Math.abs(pts.get(0)[1] - pts.get(72)[1]) < 1e-9);
        boolean onRing = true;
        for (double[] p : pts) onRing &= Math.abs(Math.hypot(p[0] - 100, p[1] + 50) - 30) < 1e-9;
        check("ring points on the circle", onRing);

        // HUD rows appear only with a hint
        Rig r = new Rig();
        r.say(ACCEPT_OASIS);
        String none = String.join("|", com.trevorplus.core.HudLines.buildMain(r.engine).stream().map(com.trevorplus.core.HudLines.Line::text).toList());
        check("no theodolite rows without hint", !none.contains("THEODOLITE"));
        r.pos = new double[] {0, 64, 0};
        r.say("The target is around 20 blocks below, at a 45 degrees angle!");
        String one = String.join("|", com.trevorplus.core.HudLines.buildMain(r.engine).stream().map(com.trevorplus.core.HudLines.Line::text).toList());
        check("theodolite header", one.contains("THEODOLITE") && one.contains("Y 42 to 47") && one.contains("rings 1"));
        check("ring row radius 20", one.contains("Ring 20m") && one.contains("on the ring") == false && one.contains("outside") == false && one.contains("inside"));
        r.pos = new double[] {40, 64, 0};
        r.say("The target is around 20 blocks below, at a 45 degrees angle!");
        String two = String.join("|", com.trevorplus.core.HudLines.buildMain(r.engine).stream().map(com.trevorplus.core.HudLines.Line::text).toList());
        check("likely spot appears with two rings", two.contains("Likely 20, 0") && two.contains("rings 2"));
        Settings rs = new Settings();
        rs.ringsEnabled = true; rs.blocksEnabled = false; rs.blocksRingFilter = true;
        Settings rs2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(rs.toMap())));
        check("rings off by default, blocks on", !new Settings().ringsEnabled && new Settings().blocksEnabled && !new Settings().blocksRingFilter);
        check("block settings roundtrip", rs2.ringsEnabled && !rs2.blocksEnabled && rs2.blocksRingFilter);

        // first launch and debug mode
        check("fresh install shows the welcome", !Settings.fromMap(new java.util.LinkedHashMap<>()).welcomeShown);
        java.util.Map<String, Object> upgrading = new java.util.LinkedHashMap<>();
        upgrading.put("hudX", 40L);
        check("existing settings without the key skip the welcome", Settings.fromMap(upgrading).welcomeShown);
        Settings ws = new Settings();
        Settings ws2 = Settings.fromMap(FlatJson.decode(FlatJson.encode(ws.toMap())));
        check("welcome not seen survives a save", !ws2.welcomeShown);
        ws.welcomeShown = true;
        check("welcome seen survives a save", Settings.fromMap(FlatJson.decode(FlatJson.encode(ws.toMap()))).welcomeShown);
        check("debug mode off by default and not written", !new Settings().debugMode && !new Settings().toMap().containsKey("debugMode"));
        Settings dbg = new Settings(); dbg.debugMode = true;
        // accept mode
        java.util.Map<String, Object> oldAuto = new java.util.LinkedHashMap<>(); oldAuto.put("autoAccept", true);
        eq("old autoAccept true becomes Automatic", com.trevorplus.config.AcceptMode.AUTO, Settings.fromMap(oldAuto).accept());
        eq("old file without it is Manual", com.trevorplus.config.AcceptMode.MANUAL, Settings.fromMap(upgrading).accept());
        Settings am = new Settings(); am.acceptMode = com.trevorplus.config.AcceptMode.CLICK.name();
        eq("accept mode roundtrip", com.trevorplus.config.AcceptMode.CLICK, Settings.fromMap(FlatJson.decode(FlatJson.encode(am.toMap()))).accept());
        Rig cl = new Rig(); cl.settings.acceptMode = com.trevorplus.config.AcceptMode.CLICK.name();
        cl.say("[NPC] Trevor: Ready?");
        cl.engine.onQuestPrompt("/chatprompt abc YES");
        cl.clock.add(1000);
        check("click mode never auto-sends", cl.engine.autoAcceptCommand() == null && cl.engine.promptFresh());
        check("prompt countdown", cl.engine.promptRemainingMs() > 3900 && cl.engine.promptRemainingMs() <= 4000);
        eq("click hands out the command", "/chatprompt abc YES", cl.engine.takePrompt());
        check("countdown gone after accepting", cl.engine.promptRemainingMs() == 0);

        // lite: no Automatic
        eq("cycle skips auto", com.trevorplus.config.AcceptMode.MANUAL, com.trevorplus.config.AcceptMode.CLICK.next(false));
        eq("cycle back skips auto", com.trevorplus.config.AcceptMode.CLICK, com.trevorplus.config.AcceptMode.MANUAL.previous(false));
        eq("full cycle keeps auto", com.trevorplus.config.AcceptMode.AUTO, com.trevorplus.config.AcceptMode.CLICK.next(true));
        eq("carried-over auto becomes manual", com.trevorplus.config.AcceptMode.MANUAL, com.trevorplus.config.AcceptMode.AUTO.allowing(false));
        eq("auto stays when allowed", com.trevorplus.config.AcceptMode.AUTO, com.trevorplus.config.AcceptMode.AUTO.allowing(true));
        eq("click unaffected", com.trevorplus.config.AcceptMode.CLICK, com.trevorplus.config.AcceptMode.CLICK.allowing(false));

        // warp-key delay
        eq("warp delay default", 3000L, new Settings().navMinIntervalMs);
        Settings wd = new Settings();
        wd.setNavDelayMs(1700); eq("rounds to half seconds", 1500L, wd.navMinIntervalMs);
        wd.setNavDelayMs(1800); eq("rounds up to half seconds", 2000L, wd.navMinIntervalMs);
        wd.setNavDelayMs(100); eq("minimum half a second", 500L, wd.navMinIntervalMs);
        wd.setNavDelayMs(60_000); eq("maximum ten seconds", 10_000L, wd.navMinIntervalMs);
        java.util.Map<String, Object> badDelay = new java.util.LinkedHashMap<>(); badDelay.put("navMinIntervalMs", 0L);
        eq("bad saved delay is clamped", 500L, Settings.fromMap(badDelay).navMinIntervalMs);
        wd.setNavDelayMs(4500);
        eq("delay roundtrip", 4500L, Settings.fromMap(FlatJson.decode(FlatJson.encode(wd.toMap()))).navMinIntervalMs);

        // shuriken tiers
        Settings sk = new Settings();
        check("shuriken all tiers by default", java.util.Arrays.stream(Rarity.values()).allMatch(sk::shurikenFor) && sk.shurikenFor(null));
        sk.setShurikenFor(Rarity.TRACKABLE, false); sk.setShurikenFor(Rarity.UNTRACKABLE, false);
        check("tiers switch off", !sk.shurikenFor(Rarity.TRACKABLE) && !sk.shurikenFor(Rarity.UNTRACKABLE) && sk.shurikenFor(Rarity.ELUSIVE));
        eq("tiers stored in order", "UNDETECTED,ENDANGERED,ELUSIVE", sk.shurikenRarities);
        sk.setShurikenFor(Rarity.TRACKABLE, true);
        eq("tier back on keeps order", "TRACKABLE,UNDETECTED,ENDANGERED,ELUSIVE", sk.shurikenRarities);
        check("tiers roundtrip", !Settings.fromMap(FlatJson.decode(FlatJson.encode(sk.toMap()))).shurikenFor(Rarity.UNTRACKABLE));
        Settings noTiers = new Settings(); for (Rarity x : Rarity.values()) noTiers.setShurikenFor(x, false);
        check("no tiers stays empty after a save", !Settings.fromMap(FlatJson.decode(FlatJson.encode(noTiers.toMap()))).shurikenFor(Rarity.ELUSIVE));

        check("debug mode roundtrip", Settings.fromMap(FlatJson.decode(FlatJson.encode(dbg.toMap()))).debugMode);

        // behind-walls mode
        eq("fresh install esp", com.trevorplus.config.EspMode.DIM, Settings.fromMap(new java.util.LinkedHashMap<>()).esp());
        eq("upgrade keeps always", com.trevorplus.config.EspMode.ALWAYS, Settings.fromMap(upgrading).esp());
        Settings es = new Settings(); es.espMode = com.trevorplus.config.EspMode.VISIBLE.name();
        eq("esp roundtrip", com.trevorplus.config.EspMode.VISIBLE, Settings.fromMap(FlatJson.decode(FlatJson.encode(es.toMap()))).esp());
        es.espMode = "nonsense";
        eq("bad esp falls back to dim", com.trevorplus.config.EspMode.DIM, es.esp());
        eq("esp parse word", com.trevorplus.config.EspMode.VISIBLE, com.trevorplus.config.EspMode.parse(" Visible "));
        check("esp parse junk", com.trevorplus.config.EspMode.parse("walls") == null);
        eq("esp cycles", com.trevorplus.config.EspMode.ALWAYS, com.trevorplus.config.EspMode.VISIBLE.next());
        eq("esp cycles back", com.trevorplus.config.EspMode.VISIBLE, com.trevorplus.config.EspMode.ALWAYS.previous());
        Settings uc = new Settings(); uc.updateCheck = false;
        check("update check on by default, roundtrips off", new Settings().updateCheck
                && !Settings.fromMap(FlatJson.decode(FlatJson.encode(uc.toMap()))).updateCheck);

        // update check: version picking
        eq("extract display name", "2.4.0", com.trevorplus.update.Versions.extract("Trevor+ 2.4.0"));
        eq("extract file name", "2.4.1", com.trevorplus.update.Versions.extract("trevorplus-2.4.1-26.2.jar"));
        eq("extract short", "3.0.0", com.trevorplus.update.Versions.extract("Trevor+ 3.0"));
        check("extract none", com.trevorplus.update.Versions.extract("Trevor+") == null);
        check("compare", com.trevorplus.update.Versions.compare("2.10.0", "2.9.9") > 0
                && com.trevorplus.update.Versions.compare("2.3.0+26.2", "2.3.0") == 0
                && com.trevorplus.update.Versions.compare("2.3", "2.3.1") < 0);
        var files = java.util.List.of(
                new com.trevorplus.update.Versions.RemoteFile("Trevor+ 2.3.0", "trevorplus-2.3.0-26.2.jar", 4, 1, java.util.List.of("Client", "Fabric", "26.2")),
                new com.trevorplus.update.Versions.RemoteFile("Trevor+ 2.5.0", "x.jar", 3, 1, java.util.List.of("26.2")),       // under review
                new com.trevorplus.update.Versions.RemoteFile("Trevor+ 2.6.0", "x.jar", 4, 2, java.util.List.of("26.2")),       // beta
                new com.trevorplus.update.Versions.RemoteFile("Trevor+ 2.7.0", "x.jar", 4, 1, java.util.List.of("26.3")),       // other MC
                new com.trevorplus.update.Versions.RemoteFile("Trevor+ 2.4.0", "x.jar", 4, 1, java.util.List.of("Fabric", "26.2")));
        // Modrinth version list (Trevor+ Lite's update check)
        var mr = java.util.List.of(
                com.trevorplus.update.Versions.modrinth("2.4.1", "release", "listed", java.util.List.of("26.2")),
                com.trevorplus.update.Versions.modrinth("2.5.0-beta.1", "beta", "listed", java.util.List.of("26.2")),     // beta: ignored
                com.trevorplus.update.Versions.modrinth("2.6.0", "release", "archived", java.util.List.of("26.2")),       // not listed: ignored
                com.trevorplus.update.Versions.modrinth("2.7.0", "release", "listed", java.util.List.of("26.3")));        // other MC: ignored
        eq("modrinth newest usable", "2.4.1", com.trevorplus.update.Versions.newest(mr, "26.2"));
        check("modrinth update over 2.4.0 only", com.trevorplus.update.Versions.isNewer(com.trevorplus.update.Versions.newest(mr, "26.2"), "2.4.0") && !com.trevorplus.update.Versions.isNewer(com.trevorplus.update.Versions.newest(mr, "26.2"), "2.4.1"));

        // semantic-versioning precedence, the semver.org example chain
        String[] chain = {"1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
                "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0"};
        boolean ordered = true;
        for (int i = 0; i + 1 < chain.length; i++) {
            ordered &= com.trevorplus.update.Versions.compare(chain[i], chain[i + 1]) < 0 && com.trevorplus.update.Versions.compare(chain[i + 1], chain[i]) > 0;
        }
        check("semver precedence chain", ordered);
        check("pre-release below its release", com.trevorplus.update.Versions.compare("2.4.0-beta.1", "2.4.0") < 0 && !com.trevorplus.update.Versions.isNewer("2.4.0-beta.1", "2.4.0"));
        check("build metadata ignored", com.trevorplus.update.Versions.compare("2.4.0+26.2", "2.4.0+build.7") == 0);
        check("semver accepted", com.trevorplus.update.Versions.isSemver("2.4.0") && com.trevorplus.update.Versions.isSemver("1.0.0-rc.1") && com.trevorplus.update.Versions.isSemver("0.1.0"));
        check("not semver rejected", !com.trevorplus.update.Versions.isSemver("2.4") && !com.trevorplus.update.Versions.isSemver("v2.4.0") && !com.trevorplus.update.Versions.isSemver("02.4.0")
                && !com.trevorplus.update.Versions.isSemver("2.4.0-") && !com.trevorplus.update.Versions.isSemver("Release2"));
        eq("newest usable release", "2.4.0", com.trevorplus.update.Versions.newest(files, "26.2"));
        check("nothing for unknown MC", com.trevorplus.update.Versions.newest(files, "1.8.9") == null);
        check("newer than running", com.trevorplus.update.Versions.isNewer("2.4.0", "2.3.0+26.2"));
        check("same is not newer", !com.trevorplus.update.Versions.isNewer("2.3.0", "2.3.0+26.2")
                && !com.trevorplus.update.Versions.isNewer(null, "2.3.0"));

        // the real tag from the log: pieces split, aqua icon (custom font, prints blank) after the heart
        var real = java.util.List.of(
                new com.trevorplus.entities.ShurikenDetector.Segment("[", 0x555555),
                new com.trevorplus.entities.ShurikenDetector.Segment("Lv3", 0xAAAAAA),
                new com.trevorplus.entities.ShurikenDetector.Segment("] ", 0x555555),
                new com.trevorplus.entities.ShurikenDetector.Segment("\uE001", 0x55FF55),
                new com.trevorplus.entities.ShurikenDetector.Segment(" ", 0xFFFF55),
                new com.trevorplus.entities.ShurikenDetector.Segment("Endangered Chicken", 0xAA00AA),
                new com.trevorplus.entities.ShurikenDetector.Segment(" ", -1),
                new com.trevorplus.entities.ShurikenDetector.Segment("4,993", 0x55FF55),
                new com.trevorplus.entities.ShurikenDetector.Segment("/", 0xFFFFFF),
                new com.trevorplus.entities.ShurikenDetector.Segment("5,000", 0x55FF55),
                new com.trevorplus.entities.ShurikenDetector.Segment("\u2764 ", 0xFF5555),
                new com.trevorplus.entities.ShurikenDetector.Segment("\uE0F2", 0x55FFFF));
        eq("real tag: icon after the heart", "\uE0F2", com.trevorplus.entities.ShurikenDetector.learn(real));
        check("real tag detected without learning", com.trevorplus.entities.ShurikenDetector.hasMarker(real, ""));
        check("real tag detected with learned icon", com.trevorplus.entities.ShurikenDetector.hasMarker(real, "\uE0F2"));
        var realNoIcon = real.subList(0, real.size() - 1);
        check("same tag without the icon is unmarked", !com.trevorplus.entities.ShurikenDetector.hasMarker(realNoIcon, "")
                && !com.trevorplus.entities.ShurikenDetector.hasMarker(realNoIcon, "\uE0F2"));

        // polygon fill
        var sq = com.trevorplus.theodolite.QuadFill.spans(new double[] {10, 20, 20, 10}, new double[] {10, 10, 20, 20}, 100, 100);
        eq("square span rows", 10, sq.size());
        check("square span width", sq.get(0)[1] == 10 && sq.get(0)[2] == 20 && sq.get(9)[0] == 19);
        var clip = com.trevorplus.theodolite.QuadFill.spans(new double[] {-10, 10, 10, -10}, new double[] {-10, -10, 10, 10}, 100, 100);
        check("clipped to screen", clip.size() == 10 && clip.get(0)[0] == 0 && clip.get(0)[1] == 0 && clip.get(0)[2] == 10);
        var tri = com.trevorplus.theodolite.QuadFill.spans(new double[] {0, 10, 0}, new double[] {0, 0, 10}, 100, 100);
        check("triangle narrows", tri.get(0)[2] - tri.get(0)[1] > tri.get(tri.size() - 1)[2] - tri.get(tri.size() - 1)[1]);
        check("off screen gives nothing", com.trevorplus.theodolite.QuadFill.spans(new double[] {200, 210, 210}, new double[] {5, 5, 15}, 100, 100).isEmpty());
        check("degenerate polygon gives nothing", com.trevorplus.theodolite.QuadFill.spans(new double[] {1, 2}, new double[] {1, 2}, 100, 100).isEmpty());
    }
}
