package com.trevorplus.alerts;

import com.trevorplus.quests.Rarity;
import java.util.ArrayList;
import java.util.List;

/** On-screen flash messages (never chat). The newest alert is the one shown. Pure logic, no Minecraft. */
public final class AlertQueue {
    public record Alert(String text, int argb, long startMs, long durationMs) {}

    /** What to draw right now. alpha fades out at the end; flashOn blinks during the first moments. */
    public record View(String text, int argb, double alpha, boolean flashOn) {}

    static final long FADE_MS = 600;
    static final long FLASH_PHASE_MS = 1500;
    static final long FLASH_PERIOD_MS = 150;

    private final List<Alert> alerts = new ArrayList<>();

    public void push(String text, int argb, long nowMs, long durationMs) {
        if (!alerts.isEmpty()) {
            Alert last = alerts.get(alerts.size() - 1);
            if (last.text().equals(text) && nowMs - last.startMs() < 1000) return;   // same alert twice in a row
        }
        alerts.add(new Alert(text, argb, nowMs, durationMs));
    }

    public View current(long nowMs) {
        alerts.removeIf(a -> nowMs - a.startMs() >= a.durationMs());
        if (alerts.isEmpty()) return null;
        Alert a = alerts.get(alerts.size() - 1);
        long age = nowMs - a.startMs();
        long left = a.durationMs() - age;
        double alpha = left >= FADE_MS ? 1.0 : Math.max(0.0, (double) left / FADE_MS);
        boolean flashOn = age >= FLASH_PHASE_MS || (age / FLASH_PERIOD_MS) % 2 == 0;
        return new View(a.text(), a.argb(), alpha, flashOn);
    }

    public void clear() { alerts.clear(); }

    /** Rarity at or above the minimum triggers an alert. A null rarity (unknown) never does. */
    public static boolean shouldAlert(Rarity r, Rarity min) {
        return r != null && min != null && r.ordinal() >= min.ordinal();
    }
}
