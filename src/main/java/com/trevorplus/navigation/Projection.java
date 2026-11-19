package com.trevorplus.navigation;

/**
 * Plain camera math: where does a world point land on the screen? Used for the on-screen box
 * around the quest mob, so no render-pipeline API is needed. Minecraft conventions: yaw 0 faces
 * +Z, yaw grows clockwise seen from above, positive pitch looks down, FOV is the vertical angle.
 */
public final class Projection {
    private Projection() {}

    /**
     * @param nx -1 (left edge) .. 1 (right edge), may exceed that range when off screen
     * @param ny -1 (bottom edge) .. 1 (top edge)
     * @param depth distance along the view direction, in blocks; behind the camera when <= 0
     */
    public record Result(boolean inFront, double nx, double ny, double depth) {
        public boolean onScreen() { return inFront && Math.abs(nx) <= 1 && Math.abs(ny) <= 1; }
    }

    public static Result project(double ex, double ey, double ez, double yawDeg, double pitchDeg,
                                 double fovYDeg, double aspect, double tx, double ty, double tz) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double fx = -sy * cp, fy = -sp, fz = cy * cp;       // forward
        double rx = -cy, rz = -sy;                          // right (no vertical part)
        double ux = -sy * sp, uy = cp, uz = cy * sp;        // up
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        double depth = dx * fx + dy * fy + dz * fz;
        double right = dx * rx + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        double tan = Math.tan(Math.toRadians(Math.max(1, Math.min(170, fovYDeg))) / 2.0);
        if (depth <= 0.05) {
            // behind: keep the sideways direction so an edge marker can point the right way
            double len = Math.hypot(right, up);
            return new Result(false, len == 0 ? 0 : right / len, len == 0 ? 0 : up / len, depth);
        }
        return new Result(true, right / (depth * tan * aspect), up / (depth * tan), depth);
    }
}
