package io.github.brooswitminecraft.smoothslopes;

import io.github.brooswitminecraft.layeredslopes.LayerMath;

/**
 * The pure decision for one column of worldgen smoothing, free of Minecraft types.
 * {@link SmoothSurfaceFeature} reads the real blocks at a column into a {@link TopKind} and two
 * booleans, calls {@link #plan}, and turns the result back into actual block writes; this class
 * never touches a {@code BlockState}.
 */
public final class SmoothColumnRules {
    private SmoothColumnRules() {}

    /** What the column's top block is, as far as this decision cares. */
    public enum TopKind {
        /** No layered form at all (or not plain natural ground): the column is left untouched. */
        NONE,
        /** Grass, mycelium or podzol: a covered top turns to dirt when the surface rises over it. */
        GRASSY,
        /**
         * A thin vanilla snow layer: unlike every other kind, it is not already a full cube, so a rising
         * surface has to make it one first before adding a new cell on top of it.
         */
        SNOW,
        /** Anything else with a layered form (stone, dirt, a full snow block, ...). */
        PLAIN,
    }

    /** What happens to a column, and how full the moved cell ends up (0 when that's not meaningful). */
    public enum Outcome { BLOCKED, UNCHANGED, RISE, RISE_WITH_GRASS_CONVERSION, RISE_WITH_SNOW_CONVERSION, DROP_TO_EMPTY, DROP_TO_PARTIAL }

    public record Plan(Outcome outcome, int layers) {
        private static final Plan BLOCKED = new Plan(Outcome.BLOCKED, 0);
        private static final Plan UNCHANGED = new Plan(Outcome.UNCHANGED, 0);
    }

    /**
     * @param kind what the current top block is
     * @param coverIsAir whether the cell directly above the top block is air
     * @param belowOccludes whether the cell directly below the top block is solid enough to support it
     * @param surface the natural (pre-smoothing) surface height of this column
     * @param cell where {@link SurfaceSmoother} says the smoothed surface actually falls
     */
    public static Plan plan(TopKind kind, boolean coverIsAir, boolean belowOccludes, int surface, SurfaceSmoother.Cell cell) {
        if (kind == TopKind.NONE || !coverIsAir || !belowOccludes) {
            return Plan.BLOCKED;
        }
        if (cell.y() == surface - 1 && cell.layers() == LayerMath.MAX_LAYERS) {
            return Plan.UNCHANGED; // the surface did not move
        }
        if (cell.y() == surface && cell.layers() == 0) {
            return Plan.UNCHANGED;
        }
        if (cell.y() >= surface) {
            if (cell.layers() == 0) {
                return Plan.UNCHANGED;
            }
            Outcome outcome = switch (kind) {
                case GRASSY -> Outcome.RISE_WITH_GRASS_CONVERSION;
                case SNOW -> Outcome.RISE_WITH_SNOW_CONVERSION;
                default -> Outcome.RISE;
            };
            return new Plan(outcome, cell.layers());
        }
        return cell.layers() == 0 ? new Plan(Outcome.DROP_TO_EMPTY, 0) : new Plan(Outcome.DROP_TO_PARTIAL, cell.layers());
    }
}
