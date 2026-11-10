package io.github.brooswitminecraft.smoothslopes;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config. {@code worldgenSmoothing} mirrors the key Dynamic Terrain used to define itself
 * before MINECRAFT-500 extracted worldgen smoothing into this mod; its default on first write
 * migrates Dynamic Terrain's own pre-split choice (see {@link MigrationPolicy}/{@link LegacyConfigMigration})
 * so a server that had explicitly disabled it is not silently re-enabled by the upgrade.
 */
public final class SmoothSlopesConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue WORLDGEN_SMOOTHING;

    static {
        Boolean legacyValue = LegacyConfigMigration.readLegacyWorldgenSmoothing(
                FMLPaths.CONFIGDIR.get().resolve("dynamicterrain-server.toml"));
        boolean migratedDefault = MigrationPolicy.defaultWorldgenSmoothing(legacyValue);

        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        WORLDGEN_SMOOTHING = builder
                .comment("Smooth newly generated overworld terrain with layered blocks (1-block steps become fractional ramps). "
                        + "Existing chunks are never changed. At the border between old and new chunks the old side keeps its steps. "
                        + "Extracted from Dynamic Terrain (MINECRAFT-500): if this server previously had Dynamic Terrain's own "
                        + "worldgenSmoothing explicitly set to false, that choice is carried over as this key's default on first write.")
                .define("worldgenSmoothing", migratedDefault);
        SPEC = builder.build();
    }

    private SmoothSlopesConfig() {}
}
