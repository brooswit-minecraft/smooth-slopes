package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.brooswitminecraft.smoothslopes.StructureProtection.ReferenceLookup;

class StructureProtectionTest {
    @Test
    void noStructuresAtAllResolvesToAnEmptyBoundsList() {
        Optional<List<StructureBounds>> result = StructureProtection.resolve(List.of(), List.of());
        assertTrue(result.isPresent());
        assertEquals(List.of(), result.get());
    }

    @Test
    void aResolvedNeighbouringStartContributesItsBounds() {
        StructureBounds local = new StructureBounds(0, 0, 15, 15);
        StructureBounds neighbour = new StructureBounds(16, 0, 31, 15);
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(local), List.of(ReferenceLookup.resolved(Optional.of(neighbour))));
        assertTrue(result.isPresent());
        assertEquals(List.of(local, neighbour), result.get());
    }

    @Test
    void aReferenceThatCheckedOutWithNoStartContributesNothing() {
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(), List.of(ReferenceLookup.resolved(Optional.empty())));
        assertTrue(result.isPresent());
        assertEquals(List.of(), result.get());
    }

    @Test
    void anUnresolvedReferenceFailsClosedEvenWithOtherResolvedBounds() {
        StructureBounds local = new StructureBounds(0, 0, 15, 15);
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(local), List.of(ReferenceLookup.resolved(Optional.empty()), ReferenceLookup.unresolved()));
        assertTrue(result.isEmpty(), "an unresolved reference must protect the whole chunk, not just drop silently");
    }
}
