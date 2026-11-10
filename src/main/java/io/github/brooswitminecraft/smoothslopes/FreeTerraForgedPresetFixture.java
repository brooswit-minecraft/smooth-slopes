package io.github.brooswitminecraft.smoothslopes;

import etcodehome.freeterraforged.data.worldgen.preset.settings.Presets;
import etcodehome.freeterraforged.platform.DataGenUtil;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * MINECRAFT-500 acceptance evidence: generates FreeTerraForged's own default preset
 * ({@link Presets#makeFTFDefault()}) as a real, applied datapack -- the exact mechanism
 * {@code PresetConfigScreen.exportAsDatapack} uses client-side (Jira review 52703), reproduced
 * headlessly here via FreeTerraForged's own public, server-safe API ({@code Preset.buildPatch}
 * plus {@link DataGenUtil#createRegistryProvider}), so no GUI or live world is needed to produce
 * it. This listener is on this mod's own event bus, so it is present (but inert) on every Gradle
 * run's classpath, including the ordinary {@code data} run that feeds this mod's own shipped
 * {@code src/generated/resources} -- {@link #gatherData} must only ever act for the dedicated
 * {@code ftfPresetData} run (see {@code build.gradle}'s distinguishing {@code --mod
 * ftfpresetfixture}, checked via {@link GatherDataEvent#getMods()}), or this fixture would end up
 * generated straight into this mod's own shipped jar instead of {@code build/ftf-preset-data/}.
 * The result is test fixture input for
 * {@code tools/freeterraforged-acceptance/ftf_acceptance.py}, never shipped. FreeTerraForged
 * itself is a compileOnly/runtimeOnly dev-tooling dependency (see
 * {@code downloadFreeTerraForged}), never declared in {@code neoforge.mods.toml} and never a real
 * dependency of this mod.
 */
@EventBusSubscriber(modid = SmoothSlopesMod.MODID, bus = EventBusSubscriber.Bus.MOD)
final class FreeTerraForgedPresetFixture {
    private static final String PACK_NAME = "freeterraforged_default_preset";
    /** Must match build.gradle's {@code ftfPresetData} run's {@code --mod} argument exactly. */
    private static final String FIXTURE_RUN_MARKER = "ftfpresetfixture";

    private FreeTerraForgedPresetFixture() {}

    @SubscribeEvent
    static void gatherData(GatherDataEvent event) {
        // Temporary diagnostic (MINECRAFT-500): the first real CI run produced zero output with
        // no error, so log the exact gate inputs instead of guessing further from bytecode.
        SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: includeServer={} mods={}",
                event.includeServer(), event.getMods());
        if (!event.includeServer() || !event.getMods().contains(FIXTURE_RUN_MARKER)) {
            SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: gate failed, skipping");
            return;
        }
        SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: registering providers");
        PackOutput output = event.getGenerator().getPackOutput(PACK_NAME);
        SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: PackOutput folder={}",
                output.getOutputFolder());
        CompletableFuture<HolderLookup.Provider> patch = event.getLookupProvider().thenApply(vanilla -> {
            SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture: building FTF default preset patch");
            try {
                HolderLookup.Provider result = Presets.makeFTFDefault().buildPatch(vanilla);
                SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture: patch built successfully");
                return result;
            } catch (RuntimeException e) {
                SmoothSlopesMod.LOGGER.error("FreeTerraForgedPresetFixture: buildPatch threw", e);
                throw e;
            }
        });
        DataProvider registryProvider = DataGenUtil.createRegistryProvider(output, patch);
        event.getGenerator().addProvider(true, new LoggingProvider(registryProvider));
        event.getGenerator().addProvider(true, new LoggingProvider(new PackMcmetaProvider(output)));
    }

    /** Temporary diagnostic (MINECRAFT-500): confirms run() is actually invoked, and how it ends. */
    private record LoggingProvider(DataProvider delegate) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cachedOutput) {
            SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture: running provider {}", delegate.getName());
            CompletableFuture<?> future = delegate.run(cachedOutput);
            return future.whenComplete((result, error) -> {
                if (error != null) {
                    SmoothSlopesMod.LOGGER.error("FreeTerraForgedPresetFixture: provider {} failed",
                            delegate.getName(), error);
                } else {
                    SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture: provider {} completed",
                            delegate.getName());
                }
            });
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    /**
     * A standalone datapack folder (unlike a mod's own bundled resources) needs its own
     * {@code pack.mcmeta} to be recognized at all. {@link SharedConstants#DATA_PACK_FORMAT} is
     * read directly from this exact build's own Minecraft version, never hand-guessed.
     */
    private record PackMcmetaProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cachedOutput) {
            Path path = output.getOutputFolder().resolve("pack.mcmeta");
            String json = "{\"pack\":{\"pack_format\":" + SharedConstants.DATA_PACK_FORMAT
                    + ",\"description\":\"FreeTerraForged default preset fixture (MINECRAFT-500)\"}}";
            return DataProvider.saveStable(cachedOutput, JsonParser.parseString(json), path);
        }

        @Override
        public String getName() {
            return "FreeTerraForged default preset fixture pack.mcmeta";
        }
    }
}
