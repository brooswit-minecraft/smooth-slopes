package io.github.brooswitminecraft.smoothslopes;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Pairs each vanilla base block that can naturally generate at the surface with its layered
 * variant, so {@link SmoothSurfaceFeature} knows which columns have a layered form to smooth into.
 * Worldgen-only, and deliberately narrower than Dynamic Terrain's own {@code LayeredMaterials}
 * (which this mod must not depend on, per the Smooth-Slopes-runs-without-Dynamic-Terrain
 * boundary): no slab, group or disturbance mappings, since those only matter to the manual
 * grading tool and erosion, which stay in Dynamic Terrain. The base-block set mirrors Dynamic
 * Terrain's own registrations exactly, so nothing that was smoothable before this split stops
 * being smoothable after it.
 */
final class SmoothMaterials {
    private static final Map<Block, Supplier<? extends LayeredBlock>> LAYERED_BY_BASE = new HashMap<>();

    static {
        register(Blocks.DIRT, LayeredSlopesMod.LAYERED_DIRT);
        register(Blocks.SAND, LayeredSlopesMod.LAYERED_SAND);
        register(Blocks.GRAVEL, LayeredSlopesMod.LAYERED_GRAVEL);
        register(Blocks.STONE, LayeredSlopesMod.LAYERED_STONE);
        register(Blocks.COBBLESTONE, LayeredSlopesMod.LAYERED_COBBLESTONE);
        register(Blocks.SMOOTH_STONE, LayeredSlopesMod.LAYERED_SMOOTH_STONE);
        register(Blocks.DEEPSLATE, LayeredSlopesMod.LAYERED_DEEPSLATE);
        register(Blocks.COBBLED_DEEPSLATE, LayeredSlopesMod.LAYERED_COBBLED_DEEPSLATE);
        register(Blocks.TERRACOTTA, LayeredSlopesMod.LAYERED_TERRACOTTA);
        register(Blocks.RED_TERRACOTTA, LayeredSlopesMod.LAYERED_RED_TERRACOTTA);
        register(Blocks.ORANGE_TERRACOTTA, LayeredSlopesMod.LAYERED_ORANGE_TERRACOTTA);
        register(Blocks.YELLOW_TERRACOTTA, LayeredSlopesMod.LAYERED_YELLOW_TERRACOTTA);
        register(Blocks.BROWN_TERRACOTTA, LayeredSlopesMod.LAYERED_BROWN_TERRACOTTA);
        register(Blocks.WHITE_TERRACOTTA, LayeredSlopesMod.LAYERED_WHITE_TERRACOTTA);
        register(Blocks.LIGHT_GRAY_TERRACOTTA, LayeredSlopesMod.LAYERED_LIGHT_GRAY_TERRACOTTA);
        register(Blocks.MAGENTA_TERRACOTTA, LayeredSlopesMod.LAYERED_MAGENTA_TERRACOTTA);
        register(Blocks.LIGHT_BLUE_TERRACOTTA, LayeredSlopesMod.LAYERED_LIGHT_BLUE_TERRACOTTA);
        register(Blocks.LIME_TERRACOTTA, LayeredSlopesMod.LAYERED_LIME_TERRACOTTA);
        register(Blocks.PINK_TERRACOTTA, LayeredSlopesMod.LAYERED_PINK_TERRACOTTA);
        register(Blocks.GRAY_TERRACOTTA, LayeredSlopesMod.LAYERED_GRAY_TERRACOTTA);
        register(Blocks.CYAN_TERRACOTTA, LayeredSlopesMod.LAYERED_CYAN_TERRACOTTA);
        register(Blocks.PURPLE_TERRACOTTA, LayeredSlopesMod.LAYERED_PURPLE_TERRACOTTA);
        register(Blocks.BLUE_TERRACOTTA, LayeredSlopesMod.LAYERED_BLUE_TERRACOTTA);
        register(Blocks.GREEN_TERRACOTTA, LayeredSlopesMod.LAYERED_GREEN_TERRACOTTA);
        register(Blocks.BLACK_TERRACOTTA, LayeredSlopesMod.LAYERED_BLACK_TERRACOTTA);
        register(Blocks.RED_SAND, LayeredSlopesMod.LAYERED_RED_SAND);
        register(Blocks.ICE, LayeredSlopesMod.LAYERED_ICE);
        register(Blocks.PACKED_ICE, LayeredSlopesMod.LAYERED_PACKED_ICE);
        register(Blocks.SNOW_BLOCK, LayeredSlopesMod.LAYERED_SNOW_BLOCK);
        register(Blocks.COARSE_DIRT, LayeredSlopesMod.LAYERED_COARSE_DIRT);
        register(Blocks.PODZOL, LayeredSlopesMod.LAYERED_PODZOL);
        register(Blocks.MYCELIUM, LayeredSlopesMod.LAYERED_MYCELIUM);
        register(Blocks.MUD, LayeredSlopesMod.LAYERED_MUD);
        register(Blocks.CLAY, LayeredSlopesMod.LAYERED_CLAY);
        register(Blocks.SANDSTONE, LayeredSlopesMod.LAYERED_SANDSTONE);
        register(Blocks.RED_SANDSTONE, LayeredSlopesMod.LAYERED_RED_SANDSTONE);
        register(Blocks.TUFF, LayeredSlopesMod.LAYERED_TUFF);
        register(Blocks.ANDESITE, LayeredSlopesMod.LAYERED_ANDESITE);
        register(Blocks.GRANITE, LayeredSlopesMod.LAYERED_GRANITE);
        register(Blocks.DIORITE, LayeredSlopesMod.LAYERED_DIORITE);
        register(Blocks.NETHERRACK, LayeredSlopesMod.LAYERED_NETHERRACK);
        register(Blocks.BLACKSTONE, LayeredSlopesMod.LAYERED_BLACKSTONE);
        register(Blocks.GRASS_BLOCK, LayeredSlopesMod.LAYERED_GRASS);
        // Soil blocks with a different top layer grade into plain layered dirt: the podzol or mycelium
        // top is lost with the first pull. Dynamic Terrain's own LayeredMaterials registers the same
        // mapping for its grading tool/erosion; mirrored here only for worldgen smoothing's own lookup.
        register(Blocks.ROOTED_DIRT, LayeredSlopesMod.LAYERED_DIRT);
    }

    private SmoothMaterials() {}

    private static void register(Block base, Supplier<? extends LayeredBlock> layered) {
        LAYERED_BY_BASE.put(base, layered);
    }

    /** The layered variant of a base block, or null if it has none. */
    static LayeredBlock layeredFor(Block base) {
        Supplier<? extends LayeredBlock> layered = LAYERED_BY_BASE.get(base);
        return layered == null ? null : layered.get();
    }
}
