package com.trevorplus.core;

import com.trevorplus.config.Settings;
import com.trevorplus.dye.DyeLuck;
import com.trevorplus.entities.NametagInfo;
import com.trevorplus.events.MessageDeduper;
import com.trevorplus.events.TrevorMessage;
import com.trevorplus.events.TrevorParser;
import com.trevorplus.navigation.Navigator;
import com.trevorplus.quests.Outcome;
import com.trevorplus.quests.QuestRecord;
import com.trevorplus.quests.QuestTracker;
import com.trevorplus.statistics.SessionClock;
import com.trevorplus.statistics.SessionStats;
import com.trevorplus.statistics.SpawnStats;
import com.trevorplus.theodolite.HintTracker;
import com.trevorplus.storage.HistoryStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ties the pieces together. The Minecraft glue only has to call:
 *   onMessage(plainChatText)      for every incoming chat/game message,
 *   tick()                        about once per second,
 *   onTrackerBoard(active, area)  when the scoreboard/tab shows or hides the tracker line,
 *   onWorldLeave()                on disconnect / server change,
 *   onNavKey(promptFresh)         when the navigation key is pressed (executes the result itself).
 * Nothing here touches Minecraft, so all of it is unit-tested.
 */
public final class TrapperEngine {
    static final long DYE_WINDOW_MS = 5_000;
    static final long LOST_GRACE_MS = 8_000;
    static final long RECOVER_CONFIRM_MS = 5_000;
    static final long RECOVER_AFTER_FINISH_MS = 10_000;
    /** The [YES] prompt only counts if Trevor spoke just before it, and only for a few seconds. */
    static final long PROMPT_AFTER_TREVOR_MS = 5_000;
    public static final long PROMPT_FRESH_MS = 5_000;
    /** Short pause so the rest of Trevor's lines land before the auto-accept fires. */
    static final long AUTO_ACCEPT_DELAY_MS = 250;
    private static final long NEVER = -1_000_000_000L;

    private final Clock clock;
    private final Settings settings;
    private final HistoryStore store;               // may be null (no persistence)
    private final Supplier<double[]> playerPos;     // {x, y, z} or null
    private final EngineListener listener;
    private final Consumer<String> log;

    private final TrevorParser parser = new TrevorParser();
    private final MessageDeduper deduper = new MessageDeduper(300);
    private final QuestTracker tracker;
    private final SessionClock sessionClock;
    private final SessionStats session;
    private final Navigator navigator;
    private final HintTracker hints = new HintTracker();
    private DyeLuck luck;
    private SpawnStats spawns;

    private long sessionId;
    private long cooldownUntilMono = NEVER;
    private boolean cooldownPending;
    private long lastFinishMono = NEVER;
    private long lastCompletionMono = NEVER;
    private long pendingDyeMono = NEVER;
    private long boardActiveSince = NEVER;
    private long boardInactiveSince = NEVER;
    private boolean boardSeenForQuest;
    private long lastTrevorLineMono = NEVER;
    private long promptMono = NEVER;
    private String promptCommand;

    public TrapperEngine(Clock clock, Settings settings, HistoryStore store, Supplier<double[]> playerPos,
                         EngineListener listener, Consumer<String> log) {
        this.clock = clock;
        this.settings = settings;
        this.store = store;
        this.playerPos = playerPos;
        this.listener = listener == null ? EngineListener.NONE : listener;
        this.log = log == null ? s -> {} : log;
        this.tracker = new QuestTracker(clock);
        this.sessionClock = new SessionClock(clock, settings);
        this.sessionId = clock.wallMs();
        this.session = new SessionStats(settings, sessionId);
        this.navigator = new Navigator(settings);
        Map<String, Object> state = store != null ? store.readJson("state.json") : new LinkedHashMap<>();
        this.luck = store != null ? DyeLuck.fromMap(state) : new DyeLuck();
        this.spawns = SpawnStats.fromMap(state);
    }

    // ---- accessors for the HUD ---------------------------------------------------------------

    public QuestTracker tracker() { return tracker; }
    public SessionClock sessionClock() { return sessionClock; }
    public SessionStats session() { return session; }
    public DyeLuck luck() { return luck; }
    public HintTracker hints() { return hints; }
    public SpawnStats spawns() { return spawns; }
    public Settings settings() { return settings; }
    /** {x, y, z} of the player, or null outside a world. */
    public double[] playerPos() { return playerPos.get(); }
    public SessionStats.Snapshot sessionSnapshot() { return session.snapshot(sessionClock.elapsedMs()); }

    public long cooldownRemainingMs() { return Math.max(0, cooldownUntilMono - clock.monoMs()); }

    // ---- inputs ------------------------------------------------------------------------------

    public void onMessage(String plainText) {
        if (plainText != null && plainText.startsWith("[NPC] Trevor")) lastTrevorLineMono = clock.monoMs();
        Optional<TrevorMessage> parsed = parser.parse(plainText);
        if (parsed.isEmpty()) return;
        TrevorMessage msg = parsed.get();
        long now = clock.monoMs();
        if (!(msg instanceof TrevorMessage.Hint) && deduper.isDuplicate(plainText, now)) {
            log.accept("duplicate dropped: " + plainText);
            return;
        }
        switch (msg) {
            case TrevorMessage.QuestAccepted q -> onAccepted(q);
            case TrevorMessage.KillReward k -> onReward(Outcome.KILLED, k.pelts());
            case TrevorMessage.SelfDeathReward s -> onReward(Outcome.SELF_DIED, s.pelts());
            case TrevorMessage.ReturnPrompt r -> onReturnPrompt();
            case TrevorMessage.Cooldown c -> {
                cooldownUntilMono = now + c.seconds() * 1000L;
                cooldownPending = !tracker.isActive();
                listener.onCooldown(c.seconds());
            }
            case TrevorMessage.AlreadyActive a -> {
                if (!tracker.isActive()) {
                    tracker.recover(null);
                    boardSeenForQuest = false;
                    log.accept("quest already active, recovered without timing");
                }
            }
            case TrevorMessage.OutOfTime o -> tracker.abandon(Outcome.EXPIRED).ifPresent(this::finished);
            case TrevorMessage.Hint h -> onHint(h);
            case TrevorMessage.DyeFound d -> onDye(d);
        }
    }

    /** Call about once per second. Handles local expiry, session activity and delayed dye events. */
    public void tick() {
        long now = clock.monoMs();
        tracker.tick().ifPresent(r -> {
            log.accept("quest expired locally after 10 minutes");
            finished(r);
        });
        if (tracker.isActive()) sessionClock.activity();
        if (cooldownPending && (tracker.isActive() || cooldownRemainingMs() == 0)) {
            boolean fire = !tracker.isActive();
            cooldownPending = false;
            if (fire) listener.onCooldownReady();
        }
        if (pendingDyeMono != NEVER && now - pendingDyeMono > DYE_WINDOW_MS) {
            pendingDyeMono = NEVER;
            applyDye();
        }
    }

    /**
     * Reconciles with the scoreboard/tab tracker line (active = the line is present).
     * It never creates a quest right after a quest ended, and only abandons a quest that the
     * board has shown before, so a slow scoreboard cannot cause false results.
     */
    public void onTrackerBoard(boolean active, String areaName) {
        long now = clock.monoMs();
        if (active) {
            boardInactiveSince = NEVER;
            boardSeenForQuest = boardSeenForQuest || tracker.isActive();
            if (boardActiveSince == NEVER) boardActiveSince = now;
            if (tracker.isActive()) {
                tracker.setAreaIfUnknown(areaName);
            } else if (now - boardActiveSince >= RECOVER_CONFIRM_MS && now - lastFinishMono >= RECOVER_AFTER_FINISH_MS) {
                tracker.recover(areaName);
                boardSeenForQuest = true;
                log.accept("recovered active quest from scoreboard: " + areaName);
                tracker.current().ifPresent(listener::onQuestStarted);
            }
        } else {
            boardActiveSince = NEVER;
            if (tracker.isActive() && boardSeenForQuest) {
                if (boardInactiveSince == NEVER) {
                    boardInactiveSince = now;
                } else if (now - boardInactiveSince >= LOST_GRACE_MS) {
                    boardInactiveSince = NEVER;
                    log.accept("scoreboard no longer shows the quest, closing as lost");
                    tracker.abandon(Outcome.LOST).ifPresent(this::finished);
                }
            } else {
                boardInactiveSince = NEVER;
            }
        }
    }

    /**
     * Disconnect or server hop: a Trevor quest does not survive it (you have to start over), so the active
     * quest is dropped without being saved or counted. History, stats and dye odds are untouched.
     */
    public void onWorldLeave() {
        if (tracker.discard()) log.accept("server change: active quest dropped, not saved");
        hints.reset();
        promptCommand = null;
        cooldownUntilMono = NEVER;
        cooldownPending = false;
        boardActiveSince = NEVER;
        boardInactiveSince = NEVER;
    }

    /** A nametag the client can see. Only used to learn the animal type of the active quest. */
    public void onNametag(NametagInfo info) {
        Optional<QuestTracker.ActiveQuest> cur = tracker.current();
        if (cur.isEmpty() || info.animal() == null) return;
        if (cur.get().rarity() != null && info.rarity() != cur.get().rarity()) return;
        tracker.noteAnimal(info.animal());
    }

    // ---- Trevor's [YES] / [NO] prompt ----------------------------------------------------------

    /**
     * The glue found a clickable [YES] in a chat line. Remembered only if Trevor spoke within the
     * last few seconds and no quest is running, so no other prompt can be mistaken for it.
     */
    public void onQuestPrompt(String chatCommand) {
        long now = clock.monoMs();
        if (chatCommand == null || chatCommand.isBlank()) return;
        if (now - lastTrevorLineMono > PROMPT_AFTER_TREVOR_MS) {
            log.accept("[YES] prompt ignored, Trevor did not just speak: " + chatCommand);
            return;
        }
        if (tracker.isActive()) {
            log.accept("[YES] prompt ignored, a quest is already active");
            return;
        }
        promptCommand = chatCommand.trim();
        promptMono = now;
        log.accept("quest prompt ready: " + promptCommand);
    }

    public boolean promptFresh() {
        return promptCommand != null && clock.monoMs() - promptMono <= PROMPT_FRESH_MS && !tracker.isActive();
    }

    /** Milliseconds left before a pending prompt goes stale; 0 when none is pending. */
    public long promptRemainingMs() {
        return promptFresh() ? Math.max(0, PROMPT_FRESH_MS - (clock.monoMs() - promptMono)) : 0;
    }

    /** Hands out the prompt command once and forgets it. Null when nothing fresh is pending. */
    public String takePrompt() {
        if (!promptFresh()) return null;
        String c = promptCommand;
        promptCommand = null;
        return c;
    }

    /**
     * Auto-accept: returns the command to send when the setting is on, a prompt is pending and the
     * short delay has passed. At most once per prompt. The glue polls this every client tick.
     */
    public String autoAcceptCommand() {
        if (settings.accept() != com.trevorplus.config.AcceptMode.AUTO || !promptFresh()) return null;
        if (clock.monoMs() - promptMono < AUTO_ACCEPT_DELAY_MS) return null;
        return takePrompt();
    }

    /** The glue executes the returned action: one command or one prompt click per keypress. */
    public Navigator.NavAction onNavKey(boolean acceptPromptFresh) {
        Optional<QuestTracker.ActiveQuest> cur = tracker.current();
        Navigator.Context ctx = new Navigator.Context(acceptPromptFresh, cur.isPresent(),
                cur.map(QuestTracker.ActiveQuest::areaName).orElse(null));
        return navigator.decide(ctx, clock.monoMs());
    }

    /** Same as onNavKey(promptFresh()). */
    public Navigator.NavAction onNavKey() { return onNavKey(promptFresh()); }

    /** What the navigation key would do right now, for the HUD. */
    public String navPreview() {
        Optional<QuestTracker.ActiveQuest> cur = tracker.current();
        return navigator.describe(new Navigator.Context(promptFresh(), cur.isPresent(),
                cur.map(QuestTracker.ActiveQuest::areaName).orElse(null)));
    }

    // ---- session management ------------------------------------------------------------------

    public void pauseSession() { sessionClock.pause(); }
    public void resumeSession() { sessionClock.resume(); }

    /** Saves the current session summary and starts a fresh one. */
    public void resetSession() {
        saveSessionSummary();
        sessionClock.reset();
        spawns.resetSession();
        sessionId = clock.wallMs();
        session.reset(sessionId);
    }

    /** Call on game exit if possible. Crashes lose nothing because quests are appended as they finish. */
    public void shutdown() {
        saveSessionSummary();
        saveState();
    }

    public void setMultiplier(int m) {
        settings.setMultiplier(m);
        log.accept("vincent multiplier set to " + settings.multiplier + "x");
    }

    public void setSpawnCounts(long[] counts) {
        spawns.setLifetime(counts);
        saveState();
    }

    public void setSinceElusive(long n) {
        spawns.setSinceElusive(n);
        saveState();
    }

    public void setQuestsSinceDye(long n) {
        luck.setQuestsSinceDye(n, settings.elusiveWeight);
        saveState();
    }

    /** Manual fallback if the dye broadcast was missed. */
    public void logDyeManually() { applyDye(); }

    // ---- handlers ----------------------------------------------------------------------------

    private void onAccepted(TrevorMessage.QuestAccepted q) {
        tracker.start(q.rarity(), q.areaName()).ifPresent(prev -> {
            log.accept("new quest accepted while one was active, closing the old one as lost");
            finished(prev);
        });
        hints.reset();
        cooldownPending = false;
        boardSeenForQuest = false;
        boardInactiveSince = NEVER;
        promptCommand = null;
        spawns.record(q.rarity());
        saveState();
        sessionClock.activity();
        log.accept("quest accepted: " + q.rarity() + " near " + q.areaName());
        tracker.current().ifPresent(listener::onQuestStarted);
    }

    private void onReward(Outcome outcome, int pelts) {
        finished(tracker.finish(outcome, pelts, settings.multiplier));
    }

    private void onReturnPrompt() {
        // Normally the reward line already closed the quest. If the quest is still open, the
        // reward line was missed: close it as lost rather than invent a pelt count.
        tracker.abandon(Outcome.LOST).ifPresent(r -> {
            log.accept("return prompt seen without reward line");
            finished(r);
        });
    }

    private void onHint(TrevorMessage.Hint h) {
        double[] p = playerPos.get();
        if (p == null) return;
        hints.addHint(p[0], p[1], p[2], h.dy(), h.angleDeg(), h.exact());
        sessionClock.activity();
        listener.onHint(hints);
    }

    private void onDye(TrevorMessage.DyeFound d) {
        if (settings.playerName == null || settings.playerName.isBlank()) {
            log.accept("dye broadcast seen but own username is not set, ignoring");
            return;
        }
        if (!d.player().equalsIgnoreCase(settings.playerName)) return;
        long now = clock.monoMs();
        if (now - lastCompletionMono <= DYE_WINDOW_MS) {
            applyDye();
        } else {
            // the dye line can arrive just before the reward line; wait briefly for it
            pendingDyeMono = now;
        }
    }

    private void applyDye() {
        DyeLuck.DyeDrop drop = luck.recordDye(clock.wallMs(), settings.multiplier);
        if (store != null) store.appendDye(drop);
        saveState();
        log.accept("own dye recorded after " + drop.baseQuestsSince() + " quests");
        listener.onDye(drop);
    }

    private void finished(QuestRecord rec) {
        long now = clock.monoMs();
        lastFinishMono = now;
        if (settings.cooldownSeconds > 0) {
            cooldownUntilMono = now + settings.cooldownSeconds * 1000L;
            cooldownPending = true;
        }
        hints.reset();
        boardSeenForQuest = false;
        boardInactiveSince = NEVER;
        if (store != null) store.appendQuest(rec, sessionId);
        if (!sessionClock.isPaused()) {
            sessionClock.activity();
            session.record(rec);
        } else {
            log.accept("session paused: quest kept in history, excluded from session stats");
        }
        boolean forDye = rec.rarity() != null
                && (rec.outcome() == Outcome.KILLED
                    || (rec.outcome() == Outcome.SELF_DIED && settings.selfDeathCountsForDye));
        if (forDye) luck.recordQuest(rec.rarity(), rec.multiplier());
        if (rec.completed()) lastCompletionMono = now;
        if (pendingDyeMono != NEVER && now - pendingDyeMono <= DYE_WINDOW_MS) {
            pendingDyeMono = NEVER;
            applyDye();                       // the quest above belongs to the streak the dye ends
        }
        saveState();
        log.accept("quest finished: " + rec.outcome() + " pelts=" + rec.pelts() + " dur=" + rec.durationMs() + "ms");
        listener.onQuestFinished(rec);
    }

    private void saveState() {
        if (store == null) return;
        Map<String, Object> state = new LinkedHashMap<>(luck.toMap());
        state.putAll(spawns.toMap());
        store.writeAtomic("state.json", state);
    }

    private void saveSessionSummary() {
        if (store == null || session.recordCount() == 0) return;
        store.appendSession(session.summary(sessionClock.elapsedMs(), clock.wallMs()));
    }
}
