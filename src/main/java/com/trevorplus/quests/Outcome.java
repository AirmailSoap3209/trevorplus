package com.trevorplus.quests;

public enum Outcome {
    /** Player killed the animal. */
    KILLED,
    /** "Your mob died randomly" - quest completed, but not by the player. */
    SELF_DIED,
    /** Ran out of the 10 minutes (chat message or local fallback). */
    EXPIRED,
    /** Quest vanished without a reward line, or was replaced by a new accept. */
    LOST,
    /** Player left the server/world mid-quest. */
    INTERRUPTED
}
