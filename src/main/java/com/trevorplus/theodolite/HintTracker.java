package com.trevorplus.theodolite;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns Theodolite hints into (a) an estimated Y band for the animal and (b) horizontal rings
 * at that height. Each hint ("N blocks above/below, at A degrees angle") gives the height
 * difference dy and an elevation angle, so the horizontal distance to the animal is
 * tan(90 - A) * |dy|. The animal lies on that ring around the spot where you used the item;
 * two rings from different spots intersect near the animal.
 */
public final class HintTracker {
    public static final double MAX_RADIUS = 500.0;
    public static final double DEFAULT_BAND = 2.5;

    public record Ring(double cx, double cy, double cz, double radius) {}

    private final double band;
    private final List<Ring> rings = new ArrayList<>();
    private double minY = Double.NaN;
    private double maxY = Double.NaN;

    public HintTracker() { this(DEFAULT_BAND); }
    public HintTracker(double band) { this.band = band; }

    public static double radiusFor(double dy, double angleDeg) {
        return Math.tan(Math.toRadians(90.0 - angleDeg)) * Math.abs(dy);
    }

    public void addHint(double px, double py, double pz, double dy, double angleDeg, boolean exact) {
        double mobY = exact ? py : py + dy;
        double lo = mobY - band;
        double hi = mobY + band;
        if (Double.isNaN(minY)) {
            minY = lo;
            maxY = hi;
        } else {
            double nlo = Math.max(minY, lo);
            double nhi = Math.min(maxY, hi);
            if (nlo <= nhi) {
                minY = nlo;
                maxY = nhi;
            } else {      // the animal moved vertically, trust the newest hint
                minY = lo;
                maxY = hi;
            }
        }
        if (!exact && dy != 0) {
            double r = radiusFor(dy, angleDeg);
            if (Double.isFinite(r) && r >= 0 && r <= MAX_RADIUS) rings.add(new Ring(px, mobY, pz, r));
        }
    }

    public boolean hasY() { return !Double.isNaN(minY); }
    public double minY() { return minY; }
    public double maxY() { return maxY; }
    public double estimatedY() { return hasY() ? (minY + maxY) / 2.0 : Double.NaN; }
    public List<Ring> rings() { return List.copyOf(rings); }

    /** n+1 points {x, z} around the ring (first equals last), for drawing it as a polyline. */
    public static List<double[]> ringPoints(Ring r, int n) {
        List<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            pts.add(new double[] {r.cx() + r.radius() * Math.cos(a), r.cz() + r.radius() * Math.sin(a)});
        }
        return pts;
    }

    /** Positive when you are outside the ring, negative inside, near zero when on it. */
    public static double distanceToRing(Ring r, double px, double pz) {
        return Math.hypot(px - r.cx(), pz - r.cz()) - r.radius();
    }

    /** Candidate {x, z} positions from the two most recent rings (empty with fewer than two). */
    public List<double[]> candidates() {
        if (rings.size() < 2) return List.of();
        return intersect(rings.get(rings.size() - 2), rings.get(rings.size() - 1), 4.0);
    }

    /** Circle intersection in the XZ plane. tol lets nearly-touching rings (rounded hints) still give a point. */
    public static List<double[]> intersect(Ring a, Ring b, double tol) {
        List<double[]> out = new ArrayList<>();
        double dx = b.cx() - a.cx();
        double dz = b.cz() - a.cz();
        double d = Math.hypot(dx, dz);
        if (d < 1e-9) return out;
        if (d > a.radius() + b.radius() + tol) return out;
        if (d < Math.abs(a.radius() - b.radius()) - tol) return out;
        double along = (a.radius() * a.radius() - b.radius() * b.radius() + d * d) / (2 * d);
        double h2 = a.radius() * a.radius() - along * along;
        double mx = a.cx() + along * dx / d;
        double mz = a.cz() + along * dz / d;
        if (h2 <= 0) {
            out.add(new double[] {mx, mz});
            return out;
        }
        double h = Math.sqrt(h2);
        out.add(new double[] {mx - h * dz / d, mz + h * dx / d});
        out.add(new double[] {mx + h * dz / d, mz - h * dx / d});
        return out;
    }

    public void reset() {
        rings.clear();
        minY = Double.NaN;
        maxY = Double.NaN;
    }
}
