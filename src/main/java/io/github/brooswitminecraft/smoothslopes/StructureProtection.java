package io.github.brooswitminecraft.smoothslopes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Policy for turning already-looked-up structure data into the bounds that protect a chunk from
 * worldgen smoothing. Pure data in and out (no Minecraft types), so the "fail closed, not open"
 * rule is directly testable: a structure merely referenced from the chunk (its start lives
 * elsewhere) can fail to resolve when that other chunk is outside the worldgen region's cache.
 * When that happens, the whole chunk is reported as protected instead of silently protecting
 * only the structures that happened to resolve.
 *
 * <p>MINECRAFT-500 live investigation: bounds are now PER PIECE, not one box per whole structure.
 * A structure's own outer bounding box (the union of every piece's box) can be dramatically
 * larger than anything actually built -- confirmed live with a real mineshaft, whose winding,
 * mostly-disconnected tunnel pieces left a single whole-structure box covering 70%+ of a sampled
 * area that had no mineshaft block anywhere near its surface. Protecting each piece's own
 * (Minecraft-vanilla-defined) box instead, the same granularity vanilla itself already uses for
 * placement/intersection checks, leaves the empty space between pieces eligible for smoothing.
 */
public final class StructureProtection {
    private StructureProtection() {}

    /** Whether, and with what per-piece bounds, a referenced structure's start was resolved. */
    public record ReferenceLookup(boolean resolved, List<StructureBounds> bounds) {
        public static ReferenceLookup unresolved() {
            return new ReferenceLookup(false, List.of());
        }

        public static ReferenceLookup resolved(List<StructureBounds> bounds) {
            return new ReferenceLookup(true, bounds);
        }
    }

    /**
     * @param localStarts per-piece bounds of structures that start in this chunk (always resolvable, no lookup needed)
     * @param referencedStarts one lookup per structure the chunk merely references
     * @return the per-piece bounds that protect this chunk, or empty if any referenced structure
     *         could not be resolved — the caller should then treat the whole chunk as protected
     */
    public static Optional<List<StructureBounds>> resolve(List<StructureBounds> localStarts,
            List<ReferenceLookup> referencedStarts) {
        List<StructureBounds> bounds = new ArrayList<>(localStarts);
        for (ReferenceLookup lookup : referencedStarts) {
            if (!lookup.resolved()) {
                return Optional.empty();
            }
            bounds.addAll(lookup.bounds());
        }
        return Optional.of(bounds);
    }
}
