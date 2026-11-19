package com.trevorplus.events;

import com.trevorplus.quests.Rarity;

/** Typed events recognised from chat. Game detection lives in TrevorParser; logic lives elsewhere. */
public sealed interface TrevorMessage {
    /** rarity is null when the game uses a rarity we do not know. */
    record QuestAccepted(Rarity rarity, String areaName) implements TrevorMessage {}
    record KillReward(int pelts) implements TrevorMessage {}
    record SelfDeathReward(int pelts) implements TrevorMessage {}
    record ReturnPrompt() implements TrevorMessage {}
    record Cooldown(int seconds) implements TrevorMessage {}
    record AlreadyActive() implements TrevorMessage {}
    record OutOfTime() implements TrevorMessage {}
    /** dy is signed (above positive, below negative); exact means "You are at the exact height!". */
    record Hint(double dy, double angleDeg, boolean exact) implements TrevorMessage {}
    record DyeFound(String player) implements TrevorMessage {}
}
