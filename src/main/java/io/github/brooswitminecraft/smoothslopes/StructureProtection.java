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
 */
public final class StructureProtection {
    private StructureProtection() {}

    /** Whether, and with what bounds, a referenced structure's start was resolved. */
    public record ReferenceLookup(boolean resolved, Optional<StructureBounds> bounds) {
        public static ReferenceLookup unresolved() {
            return new ReferenceLookup(false, Optional.empty());
        }

        public static ReferenceLookup resolved(Optional<StructureBounds> bounds) {
            return new ReferenceLookup(true, bounds);
        }
    }

    /**
     * @param localStarts bounds of structures that start in this chunk (always resolvable, no lookup needed)
     * @param referencedStarts one lookup per structure the chunk merely references
     * @return the bounds that protect this chunk, or empty if any referenced structure could not be
     *         resolved — the caller should then treat the whole chunk as protected
     */
    public static Optional<List<StructureBounds>> resolve(List<StructureBounds> localStarts,
            List<ReferenceLookup> referencedStarts) {
        List<StructureBounds> bounds = new ArrayList<>(localStarts);
        for (ReferenceLookup lookup : referencedStarts) {
            if (!lookup.resolved()) {
                return Optional.empty();
            }
            lookup.bounds().ifPresent(bounds::add);
        }
        return Optional.of(bounds);
    }
}
