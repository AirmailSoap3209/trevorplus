package com.trevorplus.entities;

import com.trevorplus.quests.Animal;
import com.trevorplus.quests.Rarity;

/** Parsed Trapper mob nametag. plausible = max health matches what that rarity is expected to have. */
public record NametagInfo(Rarity rarity, Animal animal, long health, long maxHealth, int level,
                          boolean shuriken, boolean plausible) {}
