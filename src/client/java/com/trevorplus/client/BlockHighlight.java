package com.trevorplus.client;

import com.trevorplus.navigation.Projection;
import com.trevorplus.theodolite.HintTracker;
import com.trevorplus.theodolite.QuadFill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/**
 * Theodolite block highlight: finds the standing surfaces at the hinted height that you can
 * actually see (a sight-line test against the blocks in between) and paints each one on screen.
 * Scanning runs twice a second; drawing just projects the stored blocks every frame.
 */
final class BlockHighlight {
    private static final int RADIUS = 32;
    private static final int MAX_TESTED = 1200;
    private static final int MAX_SHOWN = 400;
    private static final double BAND = 1.5;          // standing surface within estimated Y +- this
    private static final int[] EMPTY = new int[0];

    private static volatile int[] blocks = EMPTY;     // x, topY, z triples
    private static int budget;

    private BlockHighlight() {}

    static void clear() { blocks = EMPTY; }
    /** x, top-surface y, z triples of the highlighted blocks. */
    static int[] blocks() { return blocks; }
    static int count() { return blocks.length / 3; }

    private static boolean solidAt(Minecraft mc, int x, int y, int z) {
        return BlockProbe.solid(mc.level.getBlockState(BlockPos.containing(x + 0.5, y + 0.5, z + 0.5)));
    }

    static void scan(Minecraft mc, HintTracker hints, boolean ringFilter) {
        if (mc.level == null || mc.player == null || !hints.hasY()) {
            blocks = EMPTY;
            return;
        }
        double est = hints.estimatedY();
        int byMin = (int) Math.ceil(est - BAND) - 1;               // block whose top surface is at est - BAND
        int byMax = Math.max(byMin, (int) Math.floor(est + BAND) - 1);
        double ex = mc.player.getX(), ey = mc.player.getY() + mc.player.getEyeHeight(), ez = mc.player.getZ();
        int cx = (int) Math.floor(ex), cz = (int) Math.floor(ez);
        var rings = hints.rings();

        List<int[]> cands = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = cx + dx, z = cz + dz;
                if (ringFilter && !rings.isEmpty() && !onRings(rings, x + 0.5, z + 0.5)) continue;
                for (int by = byMin; by <= byMax; by++) {
                    if (solidAt(mc, x, by, z) && !solidAt(mc, x, by + 1, z)) cands.add(new int[] {x, by + 1, z, dx * dx + dz * dz});
                }
            }
        }
        cands.sort(Comparator.comparingInt(c -> c[3]));

        List<int[]> shown = new ArrayList<>();
        int tested = 0;
        for (int[] c : cands) {
            if (tested++ >= MAX_TESTED || shown.size() >= MAX_SHOWN) break;
            if (visible(mc, ex, ey, ez, c[0] + 0.5, c[1] + 0.05, c[2] + 0.5)) shown.add(c);
        }
        int[] flat = new int[shown.size() * 3];
        for (int i = 0; i < shown.size(); i++) {
            flat[i * 3] = shown.get(i)[0];
            flat[i * 3 + 1] = shown.get(i)[1];
            flat[i * 3 + 2] = shown.get(i)[2];
        }
        blocks = flat;
    }

    private static boolean onRings(List<HintTracker.Ring> rings, double x, double z) {
        for (HintTracker.Ring r : rings) {
            if (Math.abs(Math.hypot(x - r.cx(), z - r.cz()) - r.radius()) > 3.5) return false;
        }
        return true;
    }

    /** Walks the line from the eye to the block top; any solid block on the way hides it. */
    private static boolean visible(Minecraft mc, double ex, double ey, double ez, double tx, double ty, double tz) {
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.5) return true;
        double step = 0.7;
        for (double t = step; t <= dist - 0.9; t += step) {
            double f = t / dist;
            if (solidAt(mc, (int) Math.floor(ex + dx * f), (int) Math.floor(ey + dy * f), (int) Math.floor(ez + dz * f))) return false;
        }
        return true;
    }

    /** Cheap screen-space fallback (top faces only) used only when the 3D renderer is unavailable. */
    static void draw(GuiGraphicsExtractor g, double ex, double ey, double ez, float yaw, float pitch,
                     double fov, double aspect, int W, int H, int color) {
        int[] b = blocks;
        budget = 1500;
        int maxBlocks = 120;
        double[] xs = new double[4], ys = new double[4];
        final double lo = 0.08, hi = 0.92;
        double[][] corner = {{lo, lo}, {hi, lo}, {hi, hi}, {lo, hi}};
        for (int i = 0; i + 2 < b.length && i < maxBlocks * 3 && budget > 0; i += 3) {
            boolean ok = true;
            for (int k = 0; k < 4 && ok; k++) {
                Projection.Result r = Projection.project(ex, ey, ez, yaw, pitch, fov, aspect,
                        b[i] + corner[k][0], b[i + 1] + 0.02, b[i + 2] + corner[k][1]);
                if (!r.inFront() || Math.abs(r.nx()) > 40 || Math.abs(r.ny()) > 40) { ok = false; break; }
                xs[k] = (r.nx() + 1) / 2 * W;
                ys[k] = (1 - r.ny()) / 2 * H;
            }
            if (!ok) continue;
            for (int[] span : QuadFill.spans(xs, ys, W, H)) {
                g.fill(span[1], span[0], span[2], span[0] + 1, color);
                if (--budget <= 0) break;
            }
        }
    }
}
