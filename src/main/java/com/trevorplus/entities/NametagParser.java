package com.trevorplus.entities;

import com.trevorplus.quests.Animal;
import com.trevorplus.quests.Rarity;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses visible nametags like "[Lv3] ... Endangered Sheep 4,998/5,000". Works only on text of
 * entities the client already shows, nothing hidden. The Shuriken marker is a styled icon, so
 * the Minecraft glue detects it from the text component's style and passes it in.
 */
public final class NametagParser {
    private static final Pattern MAIN = Pattern.compile(
            "(?i)\\b(trackable|untrackable|undetected|endangered|elusive)\\s+(cow|horse|sheep|pig|rabbit|chicken)\\b"
                    + "\\D{0,8}?([\\d,]+)\\s*/\\s*([\\d,]+)");
    private static final Pattern LEVEL = Pattern.compile("\\[Lv\\s*(\\d+)]");

    public Optional<NametagInfo> parse(String plainName, boolean hasShurikenMarker) {
        if (plainName == null) return Optional.empty();
        String s = plainName.replace('\u00a0', ' ').replaceAll("\u00a7.", "");
        Matcher m = MAIN.matcher(s);
        if (!m.find()) return Optional.empty();
        Rarity rarity = Rarity.parse(m.group(1));
        Animal animal = Animal.parse(m.group(2));
        long hp = Long.parseLong(m.group(3).replace(",", ""));
        long max = Long.parseLong(m.group(4).replace(",", ""));
        Matcher lv = LEVEL.matcher(s);
        int level = lv.find() ? Integer.parseInt(lv.group(1)) : -1;
        // horses carry double health (Undetected Horse 2,000/2,000), so accept 1x or 2x for them
        boolean plausible = rarity != null
                && (max == rarity.maxHealth || (animal == Animal.HORSE && max == 2L * rarity.maxHealth));
        return Optional.of(new NametagInfo(rarity, animal, hp, max, level, hasShurikenMarker, plausible));
    }
}
