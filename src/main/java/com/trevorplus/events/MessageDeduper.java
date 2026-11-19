package com.trevorplus.events;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Drops an identical line that arrives again within a short window (duplicate delivery). */
public final class MessageDeduper {
    private final long windowMs;
    private final Map<String, Long> lastSeen = new HashMap<>();

    public MessageDeduper(long windowMs) { this.windowMs = windowMs; }

    public boolean isDuplicate(String text, long nowMono) {
        Long prev = lastSeen.get(text);
        if (prev != null && nowMono - prev < windowMs) return true;
        lastSeen.put(text, nowMono);
        if (lastSeen.size() > 64) {
            Iterator<Map.Entry<String, Long>> it = lastSeen.entrySet().iterator();
            while (it.hasNext()) if (nowMono - it.next().getValue() > 10_000) it.remove();
        }
        return false;
    }
}
