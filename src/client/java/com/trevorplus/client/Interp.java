package com.trevorplus.client;

/** Remembers last tick's and this tick's position so the on-screen marker moves smoothly between ticks. */
final class Interp {
    private double px, py, pz, cx, cy, cz;
    private boolean has;

    void set(double x, double y, double z) {
        if (!has || Math.abs(x - cx) + Math.abs(y - cy) + Math.abs(z - cz) > 8) {
            px = x; py = y; pz = z;                       // first value or a teleport: no sliding
        } else {
            px = cx; py = cy; pz = cz;
        }
        cx = x; cy = y; cz = z;
        has = true;
    }

    void clear() { has = false; }
    boolean has() { return has; }
    double x(float t) { return px + (cx - px) * t; }
    double y(float t) { return py + (cy - py) * t; }
    double z(float t) { return pz + (cz - pz) * t; }
}
