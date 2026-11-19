package com.trevorplus.statistics;

import com.trevorplus.config.Settings;
import com.trevorplus.core.Clock;

/**
 * Session time that only counts active grinding. Time between activity points is counted up to
 * the idle limit, so walking and cooldowns count, but walking away does not. Manual pause
 * freezes it. While a quest is active the engine keeps sending activity, so a long hunt is
 * never cut short by the idle limit.
 */
public final class SessionClock {
    private final Clock clock;
    private final Settings settings;
    private boolean started;
    private boolean paused;
    private long accumulated;
    private long lastMark;

    public SessionClock(Clock clock, Settings settings) {
        this.clock = clock;
        this.settings = settings;
    }

    private long cap() { return settings.idleTimeoutSeconds * 1000L; }

    public void activity() {
        if (paused) return;
        long now = clock.monoMs();
        if (!started) {
            started = true;
            lastMark = now;
            return;
        }
        accumulated += Math.min(now - lastMark, cap());
        lastMark = now;
    }

    public long elapsedMs() {
        if (!started) return 0;
        if (paused) return accumulated;
        return accumulated + Math.min(clock.monoMs() - lastMark, cap());
    }

    public void pause() {
        if (paused) return;
        if (started) accumulated += Math.min(clock.monoMs() - lastMark, cap());
        paused = true;
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        lastMark = clock.monoMs();
    }

    public boolean isPaused() { return paused; }
    public boolean isStarted() { return started; }

    public void reset() {
        started = false;
        paused = false;
        accumulated = 0;
        lastMark = 0;
    }
}
