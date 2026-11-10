package io.github.brooswitminecraft.smoothslopes.ftffixture;

import io.github.brooswitminecraft.smoothslopes.SmoothSlopesMod;

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
 * it.
 *
 * <p><b>Isolation from the shipped jar (Wroosbit review 52775):</b> this class, its
 * {@code PackMcmetaProvider}, and the FreeTerraForged dependency it compiles against all live in
 * the separate {@code ftfFixture} sourceSet (see {@code build.gradle}), never {@code main} --
 * reviewed evidence found this class and FreeTerraForged itself in the actual shipped
 * {@code smoothslopes-*.jar} and on every other run's classpath (including the coinstalled
 * GameTest, which was meant to prove Layered-Slopes-only coinstallation) when this lived in
 * {@code main} with FreeTerraForged as a global dev dependency. {@code ftfFixture} is registered
 * as its own NeoForge mod ({@code smoothslopesftffixture}, see its own
 * {@code META-INF/neoforge.mods.toml}), and only the dedicated {@code ftfPresetData} run's
 * {@code loadedMods} includes it; every other run (client/server/gameTestServer/the ordinary
 * {@code data} run) is restricted to just {@code smoothslopes} and never sees this class or
 * FreeTerraForged at all. This class lives in its own {@code .ftffixture} sub-package rather than
 * {@code io.github.brooswitminecraft.smoothslopes} itself -- confirmed live that NeoForge's
 * modular classloading (JPMS) refuses to boot at all ({@code ResolutionException: Modules
 * smoothslopes and smoothslopesftffixture export package ... to module mixin_synthetic}) if two
 * different mods' modules both export the exact same package, a "split package" the module system
 * forbids regardless of which actual classes end up in which jar.
 *
 * <p>{@link #gatherData} gates on the {@code smoothslopes.ftfPresetFixture} system property (set
 * only by {@code build.gradle}'s dedicated {@code ftfPresetData} run), not on {@code --mod}:
 * confirmed live against a real CI run that NeoForge's {@code DatagenModLoader} only adds a
 * container's {@code DataGenerator} to the set it actually runs when {@code --mod} contains that
 * container's REAL mod id ({@code dataGeneratorConfig.getMods().contains(container.getModId())}
 * gates {@code DataGeneratorConfig.makeGenerator}'s second, "will run" argument) -- a synthetic
 * {@code --mod} value here left every provider registered but never executed, with a clean
 * {@code BUILD SUCCESSFUL} and no error at all. {@code --mod} must stay this mod's real id so the
 * generator is actually in the run set; the system property is what tells this run apart from the
 * ordinary {@code data} run instead.
 */
@EventBusSubscriber(modid = SmoothSlopesMod.MODID, bus = EventBusSubscriber.Bus.MOD)
final class FreeTerraForgedPresetFixture {
    private static final String PACK_NAME = "freeterraforged_default_preset";
    /** Must match build.gradle's {@code ftfPresetData} run's systemProperty key exactly. */
    private static final String FIXTURE_RUN_PROPERTY = "smoothslopes.ftfPresetFixture";

    private FreeTerraForgedPresetFixture() {}

    @SubscribeEvent
    static void gatherData(GatherDataEvent event) {
        // Diagnostic (MINECRAFT-500 / Jira 52778): kept permanently, not just while narrowing this
        // down -- two prior CI attempts reported "BUILD SUCCESSFUL" with zero providers run and no
        // exception, so this is the only way to tell "gate failed" apart from "gate passed but
        // something downstream silently produced nothing" from the log alone.
        SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: includeServer={} systemProperty({})={}",
                event.includeServer(), FIXTURE_RUN_PROPERTY, System.getProperty(FIXTURE_RUN_PROPERTY));
        if (!event.includeServer() || System.getProperty(FIXTURE_RUN_PROPERTY) == null) {
            return;
        }
        PackOutput output = event.getGenerator().getPackOutput(PACK_NAME);
        SmoothSlopesMod.LOGGER.info("FreeTerraForgedPresetFixture.gatherData: registering providers, output={}",
                output.getOutputFolder());
        CompletableFuture<HolderLookup.Provider> patch = event.getLookupProvider()
                .thenApply(vanilla -> Presets.makeFTFDefault().buildPatch(vanilla));
        event.getGenerator().addProvider(true, DataGenUtil.createRegistryProvider(output, patch));
        event.getGenerator().addProvider(true, new PackMcmetaProvider(output));
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
