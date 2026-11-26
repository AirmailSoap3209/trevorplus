package com.trevorplus.core;

import com.trevorplus.dye.DyeLuck;
import com.trevorplus.quests.QuestTracker.ActiveQuest;
import com.trevorplus.quests.Rarity;
import com.trevorplus.statistics.SpawnStats;
import com.trevorplus.statistics.SessionStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds the plain-text lines of the main HUD. No Minecraft code, so it is unit-tested. */
public final class HudLines {
    private HudLines() {}

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;

    /** A coloured piece of a HUD row. */
    public record Seg(String text, int argb) {}

    public static final int KIND_NORMAL = 0, KIND_TITLE = 1, KIND_DIVIDER = 2;

    /** One HUD row: coloured segments. text()/argb() give the plain text and first colour. */
    public record Line(String text, int argb, List<Seg> segs, int kind) {
        public Line(String text, int argb) { this(text, argb, List.of(new Seg(text, argb)), KIND_NORMAL); }

        public static Line of(Seg... segs) {
            StringBuilder sb = new StringBuilder();
            for (Seg g : segs) sb.append(g.text());
            return new Line(sb.toString(), segs.length == 0 ? WHITE : segs[0].argb(), List.of(segs), KIND_NORMAL);
        }
        public static Line title(Seg... segs) {
            Line l = of(segs);
            return new Line(l.text(), l.argb(), l.segs(), KIND_TITLE);
        }
        public static Line divider() { return new Line("", GRAY, List.of(), KIND_DIVIDER); }
    }

    public static final int ACCENT = 0xFF55E0FF, LABEL = 0xFF9AA4B0, GOLD = 0xFFFFD35A, GOOD = 0xFF6CFF8A,
            BAD = 0xFFFF7A7A, WARN = 0xFFFFAA33, VALUE = 0xFFFFFFFF;

    private static Seg lab(String t) { return new Seg(t, LABEL); }
    private static Seg val(String t, int c) { return new Seg(t, c); }

    /** Main HUD with colours: label grey, values bright, quest coloured by rarity. */
    public static List<Line> buildMain(TrapperEngine e) {
        List<Line> out = new ArrayList<>();
        SessionStats.Snapshot s = e.sessionSnapshot();
        List<Seg> title = new ArrayList<>();
        title.add(val("TREVOR+", ACCENT));
        if (e.sessionClock().isPaused()) title.add(val("   paused", WARN));
        out.add(Line.title(title.toArray(new Seg[0])));

        Optional<ActiveQuest> q = e.tracker().current();
        if (q.isPresent()) {
            ActiveQuest a = q.get();
            String name = (a.rarity() == null ? "Quest" : a.rarity().label()) + (a.animal() == null ? "" : " " + a.animal().displayName);
            List<Seg> row = new ArrayList<>();
            row.add(val(name, color(a.rarity())));
            if (a.areaName() != null) { row.add(lab("  @  ")); row.add(val(a.areaName(), VALUE)); }
            row.add(val(a.timed() ? "   " + Fmt.duration(a.elapsedMs()) : "   untimed", GOLD));
            out.add(Line.of(row.toArray(new Seg[0])));
        } else {
            long cd = e.cooldownRemainingMs();
            out.add(cd > 0 ? Line.of(val("Next quest in ", WARN), val(Fmt.duration(cd + 999), WARN))
                           : Line.of(val("Trevor ready", GOOD), lab("  no active quest")));
        }
        out.add(Line.of(lab("Key  "), val(e.navPreview(), VALUE)));
        out.add(Line.divider());
        out.add(Line.of(lab("Avg "), val(s.avgMs() > 0 ? Fmt.duration((long) s.avgMs()) : "-", VALUE),
                lab("   Best "), val(s.fastestMs() >= 0 ? Fmt.duration(s.fastestMs()) : "-", GOOD),
                lab("   Worst "), val(s.slowestMs() >= 0 ? Fmt.duration(s.slowestMs()) : "-", BAD)));
        out.add(Line.of(lab("Quests "), val(String.valueOf(s.quests()), VALUE),
                lab(String.format(Locale.ROOT, "   %.1f/h", s.questsPerHour()))));
        out.add(Line.of(lab("Pelts "), val(String.format(Locale.ROOT, "%,d", s.pelts()), GOLD),
                lab(String.format(Locale.ROOT, "   %,.0f/h   %.1f/min", s.peltsPerHour(), s.peltsPerMinute()))));
        out.add(Line.of(lab("Session "), val(Fmt.duration(s.elapsedMs()), VALUE)));
        addTheodolite(out, e);
        out.add(Line.divider());
        DyeLuck l = e.luck();
        out.add(Line.of(lab("Dye chance "), val(String.format(Locale.ROOT, "%.3f%%", l.chanceSinceLastDye() * 100.0), ACCENT),
                lab(String.format(Locale.ROOT, "   %d quests   %dx%s", l.baseQuestsSinceDye(), e.settings().multiplier,
                        l.isEstimated() ? "   est." : ""))));
        double luck = l.luckRatio();
        out.add(Line.of(lab("Dyes "), val(String.valueOf(l.dyesFound()), VALUE), lab("   luck "),
                Double.isNaN(luck) ? val("n/a", LABEL) : val(String.format(Locale.ROOT, "%.2fx", luck), luck >= 1 ? GOOD : BAD)));
        return out;
    }

    /** Settings that may hold custom rarity colours; null = defaults only. Set once by the client. */
    private static volatile com.trevorplus.config.Settings colorSettings;

    public static void useColors(com.trevorplus.config.Settings s) { colorSettings = s; }

    /** Colour of a rarity everywhere (HUD text, mob box, banners): the player's custom colour, else the default. */
    public static int color(Rarity r) {
        if (r == null) return WHITE;
        com.trevorplus.config.Settings s = colorSettings;
        int custom = s == null ? -1 : s.customColor(r);
        return custom >= 0 ? 0xFF000000 | custom : defaultColor(r);
    }

    /** The built-in colour of a rarity (ARGB). */
    public static int defaultColor(Rarity r) {
        if (r == null) return WHITE;
        return switch (r) {
            case TRACKABLE -> 0xFF55FF55;
            case UNTRACKABLE -> 0xFF55AAFF;
            case UNDETECTED -> 0xFFC080FF;
            case ENDANGERED -> 0xFFFFAA00;
            case ELUSIVE -> 0xFFFF5555;
        };
    }

    /** Hint rows, only while a Theodolite hint is active: height band, latest ring, likely spots. */
    static void addTheodolite(List<Line> out, TrapperEngine e) {
        var h = e.hints();
        if (!h.hasY()) return;
        out.add(Line.divider());
        out.add(Line.of(val("THEODOLITE", ACCENT), lab(String.format(Locale.ROOT, "   Y %.0f to %.0f   rings %d",
                h.minY(), h.maxY(), h.rings().size()))));
        double[] p = e.playerPos();
        var rings = h.rings();
        if (p != null && !rings.isEmpty()) {
            var r = rings.get(rings.size() - 1);
            double d = com.trevorplus.theodolite.HintTracker.distanceToRing(r, p[0], p[2]);
            String rel = Math.abs(d) < 3 ? "on the ring" : String.format(Locale.ROOT, "%.0fm %s", Math.abs(d), d > 0 ? "outside" : "inside");
            out.add(Line.of(lab("Ring "), val(String.format(Locale.ROOT, "%.0fm", r.radius()), VALUE), lab("   you are "),
                    val(rel, Math.abs(d) < 3 ? GOOD : VALUE)));
        }
        if (p != null) {
            var cands = new ArrayList<>(h.candidates());
            cands.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] - p[0], c[1] - p[2])));
            for (int i = 0; i < Math.min(2, cands.size()); i++) {
                double[] c = cands.get(i);
                out.add(Line.of(lab("Likely "), val(String.format(Locale.ROOT, "%.0f, %.0f", c[0], c[1]), GOLD),
                        lab(String.format(Locale.ROOT, "   %.0fm away", Math.hypot(c[0] - p[0], c[1] - p[2])))));
            }
        }
    }

    /** Second HUD: creatures received per rarity (this session / all time) and the Elusive gap. */
    /** Always-on zone indicator: where the mob is (the quest zone) and where you are. */
    public static List<Line> buildZone(String mobZone, boolean questActive, String here) {
        List<Line> out = new ArrayList<>();
        out.add(Line.title(val("ZONE", ACCENT)));
        if (questActive) {
            out.add(Line.of(lab("Mob   "), val(mobZone == null || mobZone.isBlank() ? "unknown" : mobZone, GOLD)));
        } else {
            out.add(Line.of(lab("Mob   "), val("no active quest", LABEL)));
        }
        out.add(Line.of(lab("You   "), val(here == null || here.isBlank() ? "unknown" : here, VALUE)));
        if (questActive && mobZone != null && here != null) {
            boolean in = com.trevorplus.quests.ZoneReader.same(mobZone, here);
            out.add(Line.of(val(in ? "In the mob's zone" : "Not in the mob's zone", in ? GOOD : WARN)));
        }
        return out;
    }

    public static List<Line> buildSpawns(TrapperEngine e) {
        SpawnStats sp = e.spawns();
        List<Line> out = new ArrayList<>();
        out.add(Line.title(val("CREATURES", ACCENT), lab("   session / total")));
        for (Rarity r : Rarity.values()) {
            out.add(new Line(String.format(Locale.ROOT, "%-11s %,d / %,d", r.label(), sp.session(r), sp.life(r)), color(r)));
        }
        out.add(Line.divider());
        out.add(Line.of(lab(String.format(Locale.ROOT, "%-11s ", "All")),
                val(String.format(Locale.ROOT, "%,d / %,d", sp.sessionTotal(), sp.lifeTotal()), VALUE)));
        out.add(Line.of(val("Since Elusive: " + sp.sinceElusive(), color(Rarity.ELUSIVE))));
        out.add(Line.of(lab("Gap  last "), val(sp.lastGap() >= 0 ? String.valueOf(sp.lastGap()) : "-", VALUE),
                lab("  avg "), val(Double.isNaN(sp.averageGap()) ? "-" : String.format(Locale.ROOT, "%.0f", sp.averageGap()), VALUE),
                lab("  max "), val(sp.longestGap() >= 0 ? String.valueOf(sp.longestGap()) : "-", VALUE)));
        out.add(Line.of(lab(String.format(Locale.ROOT, "Expected: 1 in %.0f", expectedGap(e.settings().elusiveWeight)))));
        return out;
    }

    /** Average creatures per Elusive (including the Elusive itself), from the spawn weights. */
    public static double expectedGap(int elusiveWeight) {
        int total = 0;
        for (Rarity r : Rarity.values()) total += (r == Rarity.ELUSIVE ? elusiveWeight : r.weight);
        return (double) total / elusiveWeight;
    }

    public static List<String> build(TrapperEngine e) {
        List<String> out = new ArrayList<>();
        SessionStats.Snapshot s = e.sessionSnapshot();
        out.add("Trevor+" + (e.sessionClock().isPaused() ? "  [paused]" : ""));

        Optional<ActiveQuest> q = e.tracker().current();
        if (q.isPresent()) {
            ActiveQuest a = q.get();
            String rarity = a.rarity() == null ? "Quest" : a.rarity().label();
            String animal = a.animal() == null ? "" : " " + a.animal().displayName;
            String where = a.areaName() == null ? "" : " @ " + a.areaName();
            String time = a.timed() ? "  " + Fmt.duration(a.elapsedMs()) : "  (untimed)";
            out.add(rarity + animal + where + time);
        } else {
            long cd = e.cooldownRemainingMs();
            out.add(cd > 0 ? "No quest - cooldown " + ((cd + 999) / 1000) + "s" : "No active quest");
        }
        out.add("Key: " + e.navPreview());

        out.add("Avg " + (s.avgMs() > 0 ? Fmt.duration((long) s.avgMs()) : "-")
                + "  Best " + (s.fastestMs() >= 0 ? Fmt.duration(s.fastestMs()) : "-")
                + "  Worst " + (s.slowestMs() >= 0 ? Fmt.duration(s.slowestMs()) : "-"));
        out.add(String.format(Locale.ROOT, "Quests %d  (%.1f/h)", s.quests(), s.questsPerHour()));
        out.add(String.format(Locale.ROOT, "Pelts %,d  (%,.0f/h  %.1f/min)", s.pelts(), s.peltsPerHour(), s.peltsPerMinute()));
        out.add("Session " + Fmt.duration(s.elapsedMs()));

        DyeLuck l = e.luck();
        out.add(String.format(Locale.ROOT, "Dye: %.3f%% chance by now (%d quests, %dx)%s",
                l.chanceSinceLastDye() * 100.0, l.baseQuestsSinceDye(), e.settings().multiplier,
                l.isEstimated() ? " est." : ""));
        double luck = l.luckRatio();
        out.add(String.format(Locale.ROOT, "Dyes %d  luck %s", l.dyesFound(),
                Double.isNaN(luck) ? "n/a" : String.format(Locale.ROOT, "%.2fx", luck)));
        return out;
    }
}
