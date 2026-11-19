package com.trevorplus.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Reads the visible sidebar scoreboard as plain text lines, top to bottom. Read only. */
final class ScoreboardReader {
    private ScoreboardReader() {}

    /** Returns null when there is no level or no sidebar objective. */
    static List<String> sidebarLines(Minecraft mc) {
        if (mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return null;

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

        List<String> lines = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            // Hypixel puts the visible text in the team prefix/suffix; the owner is just a filler.
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            String prefix = team == null ? "" : team.getPlayerPrefix().getString();
            String suffix = team == null ? "" : team.getPlayerSuffix().getString();
            lines.add(prefix + entry.owner() + suffix);
        }
        return lines;
    }
}
