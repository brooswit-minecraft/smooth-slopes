package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class StructureBoundsTest {
    @Test
    void containsIncludesTheEdges() {
        StructureBounds bounds = new StructureBounds(0, 0, 15, 15);
        assertTrue(bounds.contains(0, 0));
        assertTrue(bounds.contains(15, 15));
        assertTrue(bounds.contains(8, 3));
        assertFalse(bounds.contains(16, 0));
        assertFalse(bounds.contains(-1, 0));
        assertFalse(bounds.contains(0, 16));
    }

    @Test
    void containsAnyChecksEveryBoxInTheList() {
        List<StructureBounds> bounds = List.of(
                new StructureBounds(0, 0, 3, 3),
                new StructureBounds(100, 100, 103, 103));
        assertTrue(StructureBounds.containsAny(bounds, 2, 2));
        assertTrue(StructureBounds.containsAny(bounds, 101, 102));
        assertFalse(StructureBounds.containsAny(bounds, 50, 50));
    }

    @Test
    void containsAnyOfAnEmptyListIsFalse() {
        assertFalse(StructureBounds.containsAny(List.of(), 0, 0));
    }
}
