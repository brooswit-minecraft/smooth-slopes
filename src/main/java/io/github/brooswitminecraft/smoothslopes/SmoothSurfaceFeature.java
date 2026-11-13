package io.github.brooswitminecraft.smoothslopes;

import io.github.brooswitminecraft.layeredslopes.Anchor;
import io.github.brooswitminecraft.layeredslopes.LayerMath;
import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * EXPERIMENTAL (MINECRAFT-500, branch minecraft-500-no-structure-guard): worldgen smoothing with
 * NO structure-footprint guard at all, for direct before/after comparison against the per-piece
 * guard on minecraft-504-piece-structure-protection. John never actually asked for structure
 * protection -- it was inherited unquestioned from Dynamic Terrain's own pre-extraction code; this
 * branch measures the real tradeoffs (exposed-slope improvement vs. structure/door/path/foundation
 * side effects) with it removed, before any decision on which (if either) ships.
 *
 * <p>Runs once per newly generated chunk in the top_layer_modification step and replaces 1-block
 * steps on the surface with layered blocks, so slopes are smoother. It only edits the chunk's own
 * columns. Its target comes from the generator's own natural heights ({@link SurfaceSmoother}),
 * so two neighbouring chunks agree along their edge.
 *
 * <p>It leaves a column alone unless the surface is plain natural ground: the top block must have
 * a layered form, the cell above it must be air (no trees, plants or water), and the cell below
 * must be solid. These material/cover/support checks are unchanged from the guarded version. A
 * thin vanilla snow layer counts as plain ground in its own right: unlike every other natural
 * ground top, it doesn't block motion ({@code Blocks.SNOW} is {@code forceSolidOff}), so it has to
 * be explicitly discovered in {@link #groundHeight} rather than simply being the block the usual
 * height scan stops on. Once discovered, smoothing can thicken or thin it exactly like any other
 * material, directly in its own cell, so the result always sits flush with whatever is solid
 * beneath it -- resolved to {@link LayeredSlopesMod#LAYERED_SNOW_BLOCK} directly here rather than
 * through {@link SmoothMaterials}, so this stays a worldgen-only behaviour and does not also make
 * thin snow erodible or hand-gradable like a registered material would. The one place that needs
 * its own case is a rising surface, where the snow's own cell has to become a genuinely full block
 * first -- unlike every other natural ground top, thin snow is not already a full cube, so a new
 * cell added above it would otherwise float. Roads and farms have no layered form at all, so they
 * are untouched for a different reason. Gated by {@code worldgenSmoothing}.
 */
public class SmoothSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    public SmoothSurfaceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!SmoothSlopesConfig.WORLDGEN_SMOOTHING.get()) {
            return false;
        }
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX();
        int z0 = context.origin().getZ();

        // EXPERIMENTAL (MINECRAFT-500): no structure-footprint guard at all on this branch. John
        // never actually asked for structure protection -- it was inherited unquestioned from
        // Dynamic Terrain's own pre-extraction code. This branch exists purely to measure the real
        // tradeoffs (exposed-slope improvement vs. structure/door/path/foundation side effects)
        // with the guard removed, against the per-piece-guard candidate on
        // minecraft-504-piece-structure-protection, before deciding which (if either) ships.
        // Material/cover/support checks (smoothColumn's own guards) are untouched.

        // Natural surface heights for the chunk plus a one-block border, all read from blocks. (Asking the
        // generator for heights costs milliseconds per column and made chunk generation several times slower.)
        // The border belongs to neighbouring chunks that may already have been smoothed, so for those the
        // fractional surface is rounded back to the nearest whole height; any error is at most a couple of
        // sixteenths at the seam.
        int[][] natural = new int[18][18];
        for (int dx = -1; dx <= 16; dx++) {
            for (int dz = -1; dz <= 16; dz++) {
                boolean own = dx >= 0 && dx <= 15 && dz >= 0 && dz <= 15;
                int x = x0 + dx;
                int z = z0 + dz;
                natural[dx + 1][dz + 1] = own || !level.hasChunk(x >> 4, z >> 4)
                        ? groundHeight(level, x, z, false)
                        : groundHeight(level, x, z, true);
            }
        }
        boolean changed = false;
        int[][] around = new int[3][3];
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        around[a][b] = natural[dx + a][dz + b];
                    }
                }
                changed |= smoothColumn(level, x0 + dx, z0 + dz, around);
            }
        }
        return changed;
    }

    /**
     * Top of the highest ground block in a column, looking down through trees, leaves and air. With
     * {@code roundLayered}, a floor-anchored layered block on top counts as its fractional height, rounded.
     *
     * <p>{@code Blocks.SNOW} (the thin vanilla snow layer) is registered {@code forceSolidOff}, so
     * {@code blocksMotion()} is false for it at any layer count and the loop below walks straight through
     * it to the real ground underneath, same as it walks through air. A thin snow layer directly on top
     * of that real ground counts as the natural surface anyway — it is itself plain, smoothable natural
     * ground (see the class doc) — so it is explicitly discovered and added back in afterwards.
     */
    private static int groundHeight(LevelAccessor level, int x, int z, boolean roundLayered) {
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, z);
        while (pos.getY() > minY) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                break;
            }
            pos.move(0, -1, 0);
        }
        if (level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            pos.move(0, 1, 0);
        }
        if (roundLayered && pos.getY() > minY) {
            BlockState ground = level.getBlockState(pos);
            if (ground.getBlock() instanceof LayeredBlock && !ground.getValue(LayeredBlock.ANCHOR).isCeiling()) {
                return (int) Math.round(pos.getY() + ground.getValue(LayeredBlock.LAYERS) / 16.0);
            }
        }
        return pos.getY() + 1;
    }

    private static boolean smoothColumn(LevelAccessor level, int x, int z, int[][] around) {
        int surface = around[1][1]; // top of the highest solid block
        BlockPos top = new BlockPos(x, surface - 1, z);
        BlockState topState = level.getBlockState(top);
        // A thin snow layer is resolved directly to the mod's layered-snow block rather than through
        // SmoothMaterials, which only maps base blocks for this worldgen-only lookup: registering it
        // there would make every thin snow layer look like a generic base block instead of the special
        // case it is.
        boolean isSnow = topState.is(Blocks.SNOW);
        LayeredBlock layered = isSnow ? LayeredSlopesMod.LAYERED_SNOW_BLOCK.get() : SmoothMaterials.layeredFor(topState.getBlock());
        SmoothColumnRules.TopKind kind = layered == null ? SmoothColumnRules.TopKind.NONE
                : isSnow ? SmoothColumnRules.TopKind.SNOW
                : topState.is(Blocks.GRASS_BLOCK) || topState.is(Blocks.MYCELIUM) || topState.is(Blocks.PODZOL) ? SmoothColumnRules.TopKind.GRASSY
                : SmoothColumnRules.TopKind.PLAIN;
        boolean coverIsAir = level.getBlockState(top.above()).isAir();
        boolean belowOccludes = level.getBlockState(top.below()).canOcclude();
        SurfaceSmoother.Cell cell = SurfaceSmoother.cellOf(SurfaceSmoother.target(around));
        SmoothColumnRules.Plan plan = SmoothColumnRules.plan(kind, coverIsAir, belowOccludes, surface, cell);

        switch (plan.outcome()) {
            case BLOCKED, UNCHANGED -> {
                return false;
            }
            case RISE, RISE_WITH_GRASS_CONVERSION, RISE_WITH_SNOW_CONVERSION -> {
                // The surface rises into the air above: add a layered block there.
                set(level, top.above(), partial(layered, plan.layers()));
                if (plan.outcome() == SmoothColumnRules.Outcome.RISE_WITH_GRASS_CONVERSION) {
                    set(level, top, Blocks.DIRT.defaultBlockState()); // a covered grass top turns to dirt
                } else if (plan.outcome() == SmoothColumnRules.Outcome.RISE_WITH_SNOW_CONVERSION) {
                    // Unlike every other natural ground top, a thin vanilla snow layer is not already a
                    // full cube — it can be as little as one of eight layers. The new partial cell rising
                    // above it needs genuinely full support, so the snow itself becomes a full layered-snow
                    // block first; leaving it as-is would float the new cell above whatever fraction of the
                    // block it filled.
                    set(level, top, partial(layered, LayerMath.MAX_LAYERS));
                }
            }
            case DROP_TO_EMPTY -> set(level, top, Blocks.AIR.defaultBlockState()); // blocks above it are air
            case DROP_TO_PARTIAL -> set(level, top, partial(layered, plan.layers()));
        }
        return true;
    }

    private static BlockState partial(LayeredBlock layered, int layers) {
        return layered.defaultBlockState()
                .setValue(LayeredBlock.LAYERS, Math.max(LayerMath.MIN_LAYERS, layers))
                .setValue(LayeredBlock.ANCHOR, Anchor.FLOOR);
    }

    private static void set(LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
