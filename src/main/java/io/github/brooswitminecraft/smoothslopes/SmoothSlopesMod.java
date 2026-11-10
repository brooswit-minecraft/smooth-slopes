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

    /**
     * Fully-qualified name of the MINECRAFT-500 FreeTerraForged acceptance fixture, looked up
     * reflectively below -- never imported, so this class carries no compile-time dependency on
     * it, and plain {@link ClassNotFoundException} (every build except the dedicated
     * {@code ftfPresetData} run, which is the only one that puts it on the classpath at all) is
     * indistinguishable from "this fixture doesn't exist for this run," not an error.
     */
    private static final String FTF_PRESET_FIXTURE_CLASS =
            "io.github.brooswitminecraft.smoothslopes.ftffixture.FreeTerraForgedPresetFixture";

    public SmoothSlopesMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SmoothSlopesConfig.SPEC);
        FEATURES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SmoothSlopesMod::onServerAboutToStart);
        registerFreeTerraForgedFixtureIfPresent(modEventBus);
        LOGGER.info("Smooth Slopes loaded");
    }

    /**
     * MINECRAFT-500 / Wroosbit review 52775: the FreeTerraForged acceptance fixture has no
     * {@code @EventBusSubscriber} and no mod file of its own (see its own class doc for why three
     * different attempts at that all failed) -- {@code main} looks it up by name and registers it
     * directly instead, the one mechanism unambiguously proven to work every run.
     */
    private static void registerFreeTerraForgedFixtureIfPresent(IEventBus modEventBus) {
        // Diagnostic (MINECRAFT-500 / Jira 52778/52780): kept permanently -- gating on a system
        // property this run alone sets means this block is normally silent, and a silent
        // ClassNotFoundException here would be indistinguishable from "additionalRuntimeClasspath
        // never reached the actual launched JVM at all" without logging the real classpath.
        if (System.getProperty("smoothslopes.ftfPresetFixture") != null) {
            LOGGER.info("SmoothSlopesMod: ftfPresetFixture run detected, java.class.path={}",
                    System.getProperty("java.class.path"));
        }
        try {
            Class<?> fixtureClass = Class.forName(FTF_PRESET_FIXTURE_CLASS);
            fixtureClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            LOGGER.info("FreeTerraForged acceptance fixture found and registered");
        } catch (ClassNotFoundException e) {
            if (System.getProperty("smoothslopes.ftfPresetFixture") != null) {
                LOGGER.warn("SmoothSlopesMod: expected {} on classpath for this run but it was not found: {}",
                        FTF_PRESET_FIXTURE_CLASS, e.toString());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Found " + FTF_PRESET_FIXTURE_CLASS
                    + " but could not register it", e);
        }
    }

    /**
     * NeoForge itself resolves this world's {@code serverconfig} override directory and reloads
     * SERVER configs against it right before firing this same event (see
     * {@code ServerLifecycleHooks.handleServerAboutToStart}) -- this is the earliest point this
     * world's own override directory is known at all, so it is also the earliest point a
     * per-world legacy migration ({@link PerWorldConfigMigration}) can run. If a migration writes
     * a new override here, SERVER configs must be reloaded again so this session actually picks
     * it up, the same call NeoForge itself just made.
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
