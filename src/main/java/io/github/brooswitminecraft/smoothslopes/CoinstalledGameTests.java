package io.github.brooswitminecraft.smoothslopes;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * MINECRAFT-500 acceptance evidence: coinstalled runtime proof, not just compile-time, that Smooth
 * Slopes and Layered Slopes actually boot and interoperate together (the run succeeding at all,
 * and the feature registration resolving, is the coinstalled-load evidence), and that
 * {@link SmoothMaterials} -- the actual boundary code {@link SmoothSurfaceFeature} uses to consume
 * the extracted block API -- resolves to real, running {@link LayeredBlock} instances. Mirrors
 * Dynamic Terrain's own {@code CoinstalledGameTests} for the MINECRAFT-499 split.
 */
@GameTestHolder(SmoothSlopesMod.MODID)
@PrefixGameTestTemplate(false)
public final class CoinstalledGameTests {
    private CoinstalledGameTests() {}

    /** SmoothMaterials' vanilla-block lookups must resolve to Layered Slopes' real registered blocks. */
    @GameTest(template = "flat_platform")
    public static void smoothMaterialsResolvesToTheRealLayeredSlopesBlocks(GameTestHelper helper) {
        helper.assertTrue(SmoothMaterials.layeredFor(Blocks.DIRT) == LayeredSlopesMod.LAYERED_DIRT.get(),
                "SmoothMaterials.layeredFor(DIRT) must resolve to Layered Slopes' own LAYERED_DIRT");
        helper.assertTrue(SmoothMaterials.layeredFor(Blocks.GRASS_BLOCK) == LayeredSlopesMod.LAYERED_GRASS.get(),
                "SmoothMaterials.layeredFor(GRASS_BLOCK) must resolve to Layered Slopes' own LAYERED_GRASS");
        helper.assertTrue(SmoothMaterials.layeredFor(Blocks.STONE) == LayeredSlopesMod.LAYERED_STONE.get(),
                "SmoothMaterials.layeredFor(STONE) must resolve to Layered Slopes' own LAYERED_STONE");
        helper.succeed();
    }

    /**
     * The worldgen feature this mod registers must actually be present in the real, live feature
     * registry with both mods installed -- proof the split's own registration wiring (not just the
     * datapack JSONs) actually resolves at runtime.
     */
    @GameTest(template = "flat_platform")
    public static void smoothSurfaceFeatureIsRegistered(GameTestHelper helper) {
        helper.assertTrue(SmoothSlopesMod.SMOOTH_SURFACE.isBound(),
                "smoothslopes:smooth_surface must be a bound, registered Feature");
        helper.assertTrue(SmoothSlopesMod.SMOOTH_SURFACE.get() instanceof SmoothSurfaceFeature,
                "the registered smooth_surface feature must be this mod's own SmoothSurfaceFeature");
        helper.succeed();
    }

    /**
     * MINECRAFT-500 nonblocking cleanup (53502): on this branch, {@code SmoothSurfaceFeature} no
     * longer calls {@code Structure.adjustBoundingBox} at all -- the structure-footprint guard it
     * backed has been removed entirely, per John's direction, not merely narrowed to per-piece
     * boxes. This test is inherited from the per-piece-guard candidate (PR #2,
     * minecraft-504-piece-structure-protection) where it verified that guard's real behavior
     * against the REAL registered vanilla structures. It is kept here only for parity with that
     * comparison candidate and to pin down actual vanilla registry behavior as a general
     * regression check; it no longer exercises anything this branch's own feature code does.
     */
    @GameTest(template = "flat_platform")
    public static void structureAdjustBoundingBoxVanillaBehaviorStaysAsExpected(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure outpost = structures.getOrThrow(BuiltinStructures.PILLAGER_OUTPOST);
        Structure mineshaft = structures.getOrThrow(BuiltinStructures.MINESHAFT);

        BoundingBox piece = new BoundingBox(0, 0, 0, 3, 3, 3);
        BoundingBox outpostAdjusted = outpost.adjustBoundingBox(piece);
        BoundingBox mineshaftAdjusted = mineshaft.adjustBoundingBox(piece);

        helper.assertTrue(!outpostAdjusted.equals(piece),
                "a pillager outpost's real terrain adaptation must inflate a piece's own box, not leave it unchanged");
        helper.assertTrue(outpostAdjusted.minX() == piece.minX() - 12 && outpostAdjusted.maxX() == piece.maxX() + 12,
                "the real inflation must be exactly 12 blocks on each side, per Structure.adjustBoundingBox");
        helper.assertTrue(mineshaftAdjusted.equals(piece),
                "a mineshaft declares no terrain adaptation, so adjustBoundingBox must be a true no-op");
        helper.succeed();
    }
}
