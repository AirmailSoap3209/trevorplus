package com.trevorplus.theodolite;

import java.util.ArrayList;
import java.util.List;

/** Fills a convex polygon given in screen pixels as horizontal spans, clipped to the screen. */
public final class QuadFill {
    private QuadFill() {}

    /** Each span is {y, xLeft, xRightExclusive}. */
    public static List<int[]> spans(double[] xs, double[] ys, int width, int height) {
        List<int[]> out = new ArrayList<>();
        int n = xs.length;
        if (n < 3 || ys.length != n) return out;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double y : ys) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        int y0 = (int) Math.max(0, Math.ceil(minY)), y1 = (int) Math.min(height - 1, Math.floor(maxY));
        for (int y = y0; y <= y1; y++) {
            double yc = y + 0.5, left = Double.MAX_VALUE, right = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ya = ys[i], yb = ys[j];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    double x = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (right < left) continue;
            int xl = (int) Math.max(0, Math.round(left)), xr = (int) Math.min(width, Math.round(right));
            if (xr > xl) out.add(new int[] {y, xl, xr});
        }
        return out;
    }
}
