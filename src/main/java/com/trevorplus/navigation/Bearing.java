package com.trevorplus.navigation;

/** Direction helper for the "where is the mob" marker. Minecraft yaw: 0 = south (+Z), 90 = west (-X). */
public final class Bearing {
    private Bearing() {}

    /** Angle from where you look to the target, in degrees: 0 = straight ahead, positive = to your right. */
    public static double relativeDegrees(double px, double pz, double yawDeg, double tx, double tz) {
        double dx = tx - px, dz = tz - pz;
        if (dx == 0 && dz == 0) return 0;
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double d = (targetYaw - yawDeg) % 360.0;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;   // yaw grows clockwise seen from above, so a larger target yaw is to your right
    }

    public static String arrow(double rel) {
        double a = Math.abs(rel);
        if (a <= 12) return "^ ahead";
        if (a >= 150) return "v behind you";
        if (rel > 0) return a < 60 ? "> right" : (a < 120 ? ">> far right" : ">> behind right");
        return a < 60 ? "< left" : (a < 120 ? "<< far left" : "<< behind left");
    }

    public static String heightHint(double dy) {
        if (dy > 4) return String.format(java.util.Locale.ROOT, "%.0f up", dy);
        if (dy < -4) return String.format(java.util.Locale.ROOT, "%.0f down", -dy);
        return "";
    }
}
