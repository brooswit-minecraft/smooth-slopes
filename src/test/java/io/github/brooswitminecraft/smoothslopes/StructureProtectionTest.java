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
    void aResolvedNeighbouringStartContributesItsPieceBounds() {
        StructureBounds local = new StructureBounds(0, 0, 15, 15);
        StructureBounds neighbourPiece1 = new StructureBounds(16, 0, 19, 3);
        StructureBounds neighbourPiece2 = new StructureBounds(28, 12, 31, 15);
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(local), List.of(ReferenceLookup.resolved(List.of(neighbourPiece1, neighbourPiece2))));
        assertTrue(result.isPresent());
        assertEquals(List.of(local, neighbourPiece1, neighbourPiece2), result.get());
    }

    @Test
    void aReferenceThatCheckedOutWithNoStartContributesNothing() {
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(), List.of(ReferenceLookup.resolved(List.of())));
        assertTrue(result.isPresent());
        assertEquals(List.of(), result.get());
    }

    @Test
    void anUnresolvedReferenceFailsClosedEvenWithOtherResolvedBounds() {
        StructureBounds local = new StructureBounds(0, 0, 15, 15);
        Optional<List<StructureBounds>> result = StructureProtection.resolve(
                List.of(local), List.of(ReferenceLookup.resolved(List.of()), ReferenceLookup.unresolved()));
        assertTrue(result.isEmpty(), "an unresolved reference must protect the whole chunk, not just drop silently");
    }

    @Test
    void aStructuresWholeOuterBoxIsNeverUsedOnlyItsOwnPieces() {
        // MINECRAFT-500 live investigation: a structure whose pieces scatter across a much wider
        // area (a mineshaft's winding tunnels, e.g.) must not have the whole gap between its own
        // pieces treated as protected -- only each piece's own (tight) box.
        StructureBounds farNorthPiece = new StructureBounds(0, 0, 3, 3);
        StructureBounds farSouthPiece = new StructureBounds(0, 100, 3, 103);
        List<StructureBounds> bounds = StructureProtection.resolve(
                List.of(farNorthPiece, farSouthPiece), List.of()).orElseThrow();
        // A point that would fall inside the whole structure's outer bounding box (0,0)-(3,103)
        // but is not inside either individual piece must be left unprotected.
        assertTrue(!StructureBounds.containsAny(bounds, 1, 50),
                "a point between two scattered pieces must not be protected by their combined outer box");
        assertTrue(StructureBounds.containsAny(bounds, 1, 1), "a point inside a real piece stays protected");
        assertTrue(StructureBounds.containsAny(bounds, 1, 101), "a point inside the other real piece stays protected");
    }
}
