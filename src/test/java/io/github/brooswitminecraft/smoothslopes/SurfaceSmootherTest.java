package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.brooswitminecraft.smoothslopes.SurfaceSmoother.Cell;

class SurfaceSmootherTest {
    private static int[][] flat(int h) {
        int[][] g = new int[3][3];
        for (int[] row : g) {
            java.util.Arrays.fill(row, h);
        }
        return g;
    }

    @Test
    void flatGroundIsUnchanged() {
        assertEquals(64.0, SurfaceSmoother.target(flat(64)), 1e-12);
        assertEquals(new Cell(64, 0), SurfaceSmoother.cellOf(64.0));
    }

    @Test
    void aSingleStepBecomesAFractionalRamp() {
        // Column at 64 with a 65 shelf on its east side: the surface leans toward the shelf.
        int[][] g = flat(64);
        g[2][0] = g[2][1] = g[2][2] = 65;
        double t = SurfaceSmoother.target(g);
        assertEquals(64.25, t, 1e-9);
        assertEquals(new Cell(64, 4), SurfaceSmoother.cellOf(t));
    }

    @Test
    void aHilltopIsLowered() {
        int[][] g = flat(64);
        g[1][1] = 65; // a one-block bump
        double t = SurfaceSmoother.target(g);
        assertEquals(64.25, t, 1e-9);
        assertEquals(new Cell(64, 4), SurfaceSmoother.cellOf(t), "the bump keeps 4/16 of its top block");
    }

    @Test
    void aColumnNeverMovesByAWholeBlock() {
        int[][] pit = flat(70);
        pit[1][1] = 60; // a deep hole
        assertEquals(60 + 15.0 / 16.0, SurfaceSmoother.target(pit), 1e-9);
        int[][] spire = flat(60);
        spire[1][1] = 70;
        assertEquals(70 - 15.0 / 16.0, SurfaceSmoother.target(spire), 1e-9);
    }

    @Test
    void cellOfRoundsToSixteenths() {
        assertEquals(new Cell(10, 8), SurfaceSmoother.cellOf(10.5));
        assertEquals(new Cell(10, 16), SurfaceSmoother.cellOf(10.99));
        assertEquals(new Cell(-3, 1), SurfaceSmoother.cellOf(-3 + 1.0 / 16));
    }

    @Test
    void neighbouringColumnsAgreeAcrossTheirSharedEdge() {
        // A 4-wide stripe of heights; both columns see the same neighbourhood values, so each gets the
        // same target regardless of which chunk computes it.
        int[] row = {60, 60, 61, 61, 62, 62};
        for (int x = 1; x < row.length - 1; x++) {
            int[][] g = new int[3][3];
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    g[dx][dz] = row[x + dx - 1];
                }
            }
            double again = SurfaceSmoother.target(g);
            assertEquals(again, SurfaceSmoother.target(g), 0.0);
            assertTrue(Math.abs(again - row[x]) < 1.0);
        }
    }

    @Test
    void smoothingReducesTheLargestStepOfAStaircase() {
        // 1 block per column staircase: natural steps are 1.0; smoothed steps must be smaller.
        int[] h = {60, 61, 62, 63, 64, 65, 66};
        double[] t = new double[h.length];
        for (int x = 1; x < h.length - 1; x++) {
            int[][] g = new int[3][3];
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    g[dx][dz] = h[x + dx - 1];
                }
            }
            t[x] = SurfaceSmoother.target(g);
        }
        for (int x = 2; x < h.length - 1; x++) {
            assertTrue(t[x] - t[x - 1] <= 1.0 + 1e-9);
        }
        // a staircase is already a straight line, so the average lands on the line: nothing to smooth
        assertEquals(h[3], t[3], 1e-9);
    }
}
