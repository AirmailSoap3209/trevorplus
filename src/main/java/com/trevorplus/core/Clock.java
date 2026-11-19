package com.trevorplus.core;

/** Time source, injectable so tests can control time. */
public interface Clock {
    /** Monotonic milliseconds, used for durations. */
    long monoMs();

    /** Wall-clock epoch milliseconds, used for stored timestamps. */
    long wallMs();

    Clock SYSTEM = new Clock() {
        @Override public long monoMs() { return System.nanoTime() / 1_000_000L; }
        @Override public long wallMs() { return System.currentTimeMillis(); }
    };
}
