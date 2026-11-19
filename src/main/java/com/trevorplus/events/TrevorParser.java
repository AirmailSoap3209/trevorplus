package com.trevorplus.events;

import com.trevorplus.quests.Rarity;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises Trevor-related chat lines from plain text (formatting codes removed).
 * All patterns are anchored at the start, so a player typing the same words after a
 * "[Rank] Name:" prefix can never trigger them.
 */
public final class TrevorParser {
    private static final Pattern ACCEPT = Pattern.compile(
            "^\\[NPC\\] Trevor: You can find your (\\w+) animal near the (.+?)\\.$");
    private static final Pattern KILL = Pattern.compile(
            "^Killing the animal rewarded you (\\d[\\d,]*) pelts?[.!]?$");
    private static final Pattern SELF_DEATH = Pattern.compile(
            "^Your mob died randomly, you are rewarded (\\d[\\d,]*) pelts?[.!]?$");
    private static final Pattern RETURN = Pattern.compile(
            "^Return to the Trapper soon to get a new animal to hunt!$");
    private static final Pattern COOLDOWN = Pattern.compile(
            "^\\[NPC\\] Trevor: Try coming back in (\\d+)s!$");
    private static final Pattern ALREADY = Pattern.compile(
            "^\\[NPC\\] Trevor: You already have a mob spawned! Hunt that mob first!$");
    private static final Pattern OUT_OF_TIME = Pattern.compile(
            "^You ran out of time and the animal disappeared!$");
    private static final Pattern HINT = Pattern.compile(
            "^The target is around (\\d+(?:\\.\\d+)?) blocks (above|below), at a (\\d+(?:\\.\\d+)?) degrees angle!$");
    private static final Pattern EXACT = Pattern.compile("^You are at the exact height!$");
    private static final Pattern DYE = Pattern.compile(
            "^WOW! (?:\\[[^\\]]+\\] )?(\\w{1,16}) found a Pelt Dye!$");

    public Optional<TrevorMessage> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = clean(raw);
        if (s.isEmpty()) return Optional.empty();
        Matcher m;

        if ((m = ACCEPT.matcher(s)).matches()) {
            return Optional.of(new TrevorMessage.QuestAccepted(Rarity.parse(m.group(1)), m.group(2).trim()));
        }
        if ((m = KILL.matcher(s)).matches()) {
            return Optional.of(new TrevorMessage.KillReward(toInt(m.group(1))));
        }
        if ((m = SELF_DEATH.matcher(s)).matches()) {
            return Optional.of(new TrevorMessage.SelfDeathReward(toInt(m.group(1))));
        }
        if (RETURN.matcher(s).matches()) return Optional.of(new TrevorMessage.ReturnPrompt());
        if ((m = COOLDOWN.matcher(s)).matches()) {
            return Optional.of(new TrevorMessage.Cooldown(toInt(m.group(1))));
        }
        if (ALREADY.matcher(s).matches()) return Optional.of(new TrevorMessage.AlreadyActive());
        if (OUT_OF_TIME.matcher(s).matches()) return Optional.of(new TrevorMessage.OutOfTime());
        if ((m = HINT.matcher(s)).matches()) {
            double dy = Double.parseDouble(m.group(1));
            if (m.group(2).equals("below")) dy = -dy;
            return Optional.of(new TrevorMessage.Hint(dy, Double.parseDouble(m.group(3)), false));
        }
        if (EXACT.matcher(s).matches()) return Optional.of(new TrevorMessage.Hint(0, 0, true));
        if ((m = DYE.matcher(s)).matches()) return Optional.of(new TrevorMessage.DyeFound(m.group(1)));
        return Optional.empty();
    }

    /** Removes section-sign colour codes, normalises spaces. */
    static String clean(String s) {
        String t = s.replace('\u00a0', ' ').replaceAll("\u00a7.", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static int toInt(String digits) {
        return Integer.parseInt(digits.replace(",", ""));
    }
}
