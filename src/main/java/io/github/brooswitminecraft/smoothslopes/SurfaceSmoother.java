package io.github.brooswitminecraft.smoothslopes;

import io.github.brooswitminecraft.layeredslopes.LayerMath;

/**
 * Pure math for worldgen smoothing. A column's natural surface is an integer
 * height S (the top of its highest solid block). The smoothed surface is a
 * fractional height T: a 3x3 weighted average of the natural heights around
 * the column, kept within 15/16 of S so a column never moves by a whole block.
 * T is then expressed as the cell that holds the surface and how many sixteenths
 * of that cell are filled.
 *
 * Because T depends only on natural heights, two adjacent chunks compute the
 * same T along their shared edge, so the seam stays seamless.
 */
public final class SurfaceSmoother {
    /** Row/column weights of the smoothing kernel (a 1-2-1 binomial), out of 16 in total after the outer product. */
    private static final int[] K = {1, 2, 1};
    private static final double MAX_SHIFT = 15.0 / 16.0;

    private SurfaceSmoother() {}

    /** Where the smoothed surface falls: the cell {@code y} holds {@code layers} sixteenths (0..16) of material. */
    public record Cell(int y, int layers) {}

    /** @param heights natural surface heights, [dx+1][dz+1] for dx, dz in -1..1 around the column */
    public static double target(int[][] heights) {
        double sum = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                sum += K[dx] * K[dz] * heights[dx][dz];
            }
        }
        double t = sum / 16.0;
        int s = heights[1][1];
        return Math.max(s - MAX_SHIFT, Math.min(s + MAX_SHIFT, t));
    }

    /** The cell holding the surface at height {@code t}, and its fill. */
    public static Cell cellOf(double t) {
        int y = (int) Math.floor(t);
        int layers = (int) Math.round((t - y) * LayerMath.MAX_LAYERS);
        if (layers >= LayerMath.MAX_LAYERS) {
            return new Cell(y, LayerMath.MAX_LAYERS);
        }
        return new Cell(y, layers);
    }
}
