package io.github.brooswitminecraft.smoothslopes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);

    public static final DeferredHolder<Feature<?>, SmoothSurfaceFeature> SMOOTH_SURFACE =
            FEATURES.register("smooth_surface", () -> new SmoothSurfaceFeature(NoneFeatureConfiguration.CODEC));

    public SmoothSlopesMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SmoothSlopesConfig.SPEC);
        FEATURES.register(modEventBus);
        LOGGER.info("Smooth Slopes loaded");
    }
}
