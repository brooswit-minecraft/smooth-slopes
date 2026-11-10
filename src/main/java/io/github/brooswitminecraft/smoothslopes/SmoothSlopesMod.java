package io.github.brooswitminecraft.smoothslopes;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point and worldgen-feature registry. Extracted from Dynamic Terrain (MINECRAFT-500); the
 * layered block family itself stays in Layered Slopes (MINECRAFT-499), which this mod requires.
 */
@Mod(SmoothSlopesMod.MODID)
public class SmoothSlopesMod {
    public static final String MODID = "smoothslopes";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final LevelResource SERVER_CONFIG_DIR = new LevelResource("serverconfig");

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);

    public static final DeferredHolder<Feature<?>, SmoothSurfaceFeature> SMOOTH_SURFACE =
            FEATURES.register("smooth_surface", () -> new SmoothSurfaceFeature(NoneFeatureConfiguration.CODEC));

    public SmoothSlopesMod(IEventBus modEventBus, ModContainer modContainer) {
        migrateDedicatedServerPerWorldLegacyConfig();
        modContainer.registerConfig(ModConfig.Type.SERVER, SmoothSlopesConfig.SPEC);
        FEATURES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SmoothSlopesMod::onServerAboutToStart);
        LOGGER.info("Smooth Slopes loaded");
    }

    /**
     * Primary per-world migration path (see {@link DedicatedServerLevelName}'s own doc for why
     * {@link #onServerAboutToStart} below runs too late): reads {@code server.properties}
     * directly, at mod construction -- long before any server starts, and so before NeoForge's
     * own per-world config correction ever touches Dynamic Terrain's legacy file.
     */
    private static void migrateDedicatedServerPerWorldLegacyConfig() {
        String levelName = DedicatedServerLevelName.read(Path.of("server.properties"));
        Path worldConfigDir = Path.of(levelName, "serverconfig");
        Path legacyFile = worldConfigDir.resolve("dynamicterrain-server.toml");
        Path ownFile = worldConfigDir.resolve("smoothslopes-server.toml");
        try {
            PerWorldConfigMigration.migrate(legacyFile, ownFile);
        } catch (IOException e) {
            LOGGER.warn("Could not migrate per-world worldgenSmoothing override to {}: {}", ownFile, e.toString());
        }
    }

    /**
     * Fallback only, for launches {@link #migrateDedicatedServerPerWorldLegacyConfig} cannot
     * cover (e.g. an integrated/singleplayer server, which has no {@code server.properties} next
     * to its working directory at all): NeoForge itself resolves this world's {@code serverconfig}
     * override directory and reloads SERVER configs against it right before firing this same event
     * (see {@code ServerLifecycleHooks.handleServerAboutToStart}). By then Dynamic Terrain's own
     * legacy file may already have been corrected and the key lost (confirmed live for the
     * dedicated-server case the primary path above now handles), so this is a best effort, not the
     * mechanism this migration actually relies on. If a migration writes a new override here,
     * SERVER configs must be reloaded again so this session actually picks it up, the same call
     * NeoForge itself just made.
     */
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path worldConfigDir = server.getWorldPath(SERVER_CONFIG_DIR);
        Path legacyFile = worldConfigDir.resolve("dynamicterrain-server.toml");
        Path ownFile = worldConfigDir.resolve("smoothslopes-server.toml");
        try {
            if (PerWorldConfigMigration.migrate(legacyFile, ownFile)) {
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get(), worldConfigDir);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not migrate per-world worldgenSmoothing override to {}: {}", ownFile, e.toString());
        }
    }
}
