package com.trevorplus.core;

import com.trevorplus.dye.DyeLuck.DyeDrop;
import com.trevorplus.quests.QuestRecord;
import com.trevorplus.quests.QuestTracker.ActiveQuest;
import com.trevorplus.theodolite.HintTracker;

/** Hooks for the HUD / notification layer. All methods are optional. */
public interface EngineListener {
    default void onQuestStarted(ActiveQuest quest) {}
    default void onQuestFinished(QuestRecord record) {}
    default void onDye(DyeDrop drop) {}
    default void onHint(HintTracker hints) {}
    default void onCooldown(int seconds) {}
    /** Trevor's cooldown ran out and no quest is active. */
    default void onCooldownReady() {}
    EngineListener NONE = new EngineListener() {};
}
