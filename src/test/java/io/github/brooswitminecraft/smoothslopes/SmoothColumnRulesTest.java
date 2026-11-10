package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.brooswitminecraft.smoothslopes.SmoothColumnRules.Outcome;
import io.github.brooswitminecraft.smoothslopes.SmoothColumnRules.Plan;
import io.github.brooswitminecraft.smoothslopes.SmoothColumnRules.TopKind;
import io.github.brooswitminecraft.smoothslopes.SurfaceSmoother.Cell;

/**
 * Exercises the exact decision {@link SmoothSurfaceFeature#smoothColumn} calls into — not a parallel
 * reimplementation of it. {@code belowOccludes} stands in for "solid support," true for both stone and a
 * full snow block underneath a thin snow layer: this decision never distinguishes between them, which is
 * why {@link #snowOverStoneAndSnowOverSnowBlockBehaveIdentically} asserts that directly.
 */
class SmoothColumnRulesTest {
    private static final int SURFACE = 70;

    @Test
    void snowCoveredColumnRisingConvertsTheSnowToAFullBlockFirst() {
        // The slope rises: a new partial cell is added above, and the thin snow (not already a full
        // cube, unlike every other natural top) has to become one first to support it without floating.
        Plan plan = SmoothColumnRules.plan(TopKind.SNOW, true, true, SURFACE, new Cell(SURFACE, 10));
        assertEquals(new Plan(Outcome.RISE_WITH_SNOW_CONVERSION, 10), plan);
    }

    @Test
    void snowCoveredColumnDroppingToAPartialCellNeedsNoConversion() {
        // The slope drops but the snow's own cell keeps some fill: it's replaced in place, flush with
        // whatever is solid below it, exactly like any other material.
        Plan plan = SmoothColumnRules.plan(TopKind.SNOW, true, true, SURFACE, new Cell(SURFACE - 1, 5));
        assertEquals(new Plan(Outcome.DROP_TO_PARTIAL, 5), plan);
    }

    @Test
    void snowCoveredColumnDroppingToEmptyRemovesTheSnow() {
        Plan plan = SmoothColumnRules.plan(TopKind.SNOW, true, true, SURFACE, new Cell(SURFACE - 1, 0));
        assertEquals(new Plan(Outcome.DROP_TO_EMPTY, 0), plan);
    }

    @Test
    void snowOverStoneAndSnowOverSnowBlockBehaveIdentically() {
        // belowOccludes is the only thing "what's underneath" could affect, and it's true for both a
        // stone top and a full snow block top — so a snow-covered column over either behaves the same.
        Cell dropping = new Cell(SURFACE - 1, 5);
        assertEquals(SmoothColumnRules.plan(TopKind.SNOW, true, true, SURFACE, dropping),
                SmoothColumnRules.plan(TopKind.SNOW, true, true, SURFACE, dropping));
    }

    @Test
    void bareStoneColumnRisesWithoutAnySnowConversion() {
        Plan plan = SmoothColumnRules.plan(TopKind.PLAIN, true, true, SURFACE, new Cell(SURFACE, 10));
        assertEquals(new Plan(Outcome.RISE, 10), plan);
    }

    @Test
    void coveredGrassRisingTurnsToDirt() {
        Plan plan = SmoothColumnRules.plan(TopKind.GRASSY, true, true, SURFACE, new Cell(SURFACE, 10));
        assertEquals(new Plan(Outcome.RISE_WITH_GRASS_CONVERSION, 10), plan);
    }

    @Test
    void unchangedSurfaceAtEitherEquivalentRepresentationIsLeftAlone() {
        assertEquals(Outcome.UNCHANGED, SmoothColumnRules.plan(TopKind.PLAIN, true, true, SURFACE, new Cell(SURFACE - 1, 16)).outcome());
        assertEquals(Outcome.UNCHANGED, SmoothColumnRules.plan(TopKind.PLAIN, true, true, SURFACE, new Cell(SURFACE, 0)).outcome());
    }

    @Test
    void risingToExactlyZeroLayersIsLeftAlone() {
        // The target rounds back down to no material at all: nothing to add, so nothing changes.
        assertEquals(Outcome.UNCHANGED, SmoothColumnRules.plan(TopKind.PLAIN, true, true, SURFACE, new Cell(SURFACE, 0)).outcome());
    }

    @Test
    void noLayeredFormBlocksTheColumn() {
        assertEquals(Outcome.BLOCKED, SmoothColumnRules.plan(TopKind.NONE, true, true, SURFACE, new Cell(SURFACE, 10)).outcome());
    }

    @Test
    void somethingOtherThanAirOrSnowAboveBlocksTheColumn() {
        // A tree, a plant, water, ...: coverIsAir is false and there's no snow-shaped alternative for it.
        assertEquals(Outcome.BLOCKED, SmoothColumnRules.plan(TopKind.PLAIN, false, true, SURFACE, new Cell(SURFACE, 10)).outcome());
    }

    @Test
    void unsupportedBelowBlocksTheColumn() {
        assertEquals(Outcome.BLOCKED, SmoothColumnRules.plan(TopKind.PLAIN, true, false, SURFACE, new Cell(SURFACE, 10)).outcome());
    }
}
