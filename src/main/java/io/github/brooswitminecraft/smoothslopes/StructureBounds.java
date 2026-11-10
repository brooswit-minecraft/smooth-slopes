package io.github.brooswitminecraft.smoothslopes;

import java.util.List;

/**
 * A generated structure's footprint in the X/Z plane, used to keep worldgen smoothing off of
 * villages, outposts and other structures. Pure data (no Minecraft types), so the containment
 * check is unit-testable on its own; {@link SmoothSurfaceFeature} is responsible for building
 * the list from the chunk's actual structure starts.
 */
public record StructureBounds(int minX, int minZ, int maxX, int maxZ) {
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public static boolean containsAny(List<StructureBounds> bounds, int x, int z) {
        for (StructureBounds bound : bounds) {
            if (bound.contains(x, z)) {
                return true;
            }
        }
        return false;
    }
}
