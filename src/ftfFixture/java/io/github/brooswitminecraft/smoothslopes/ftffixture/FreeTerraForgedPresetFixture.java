package io.github.brooswitminecraft.smoothslopes.ftffixture;

import etcodehome.freeterraforged.data.worldgen.preset.settings.Presets;
import etcodehome.freeterraforged.platform.DataGenUtil;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.IEventBus;
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
 * the separate {@code ftfFixture} sourceSet (see {@code build.gradle}), never {@code main}.
 *
 * <p><b>Why no {@code @EventBusSubscriber}/own mod file (three prior attempts, each confirmed
 * live against real CI runs):</b>
 * <ol>
 * <li>A sourceSet's own {@code runtimeOnly} dependency does not ride along with that sourceSet's
 * own {@code ModModel} at run time -- FreeTerraForged's jar was absent from the run's classpath
 * even though the fixture's own mod file loaded.</li>
 * <li>{@code RunModel.additionalRuntimeClasspathConfiguration} did not fix that either --
 * {@code "All providers took: 0 ms, total files: 0"}, zero providers ever ran.</li>
 * <li>Making {@code ftfFixture} the run's primary {@code sourceSet} got the mod file "found" and
 * even "Attempting to inject @EventBusSubscriber classes" logged for it, but the annotation scan
 * itself found nothing -- no "Scanning class FreeTerraForgedPresetFixture" line ever appeared, and
 * {@link #gatherData} itself never logged anything, in any of these attempts.</li>
 * </ol>
 * Given three different failures all specifically in getting this class discovered/scanned as its
 * own separate NeoForge mod, this class is no longer one: it has no {@code @EventBusSubscriber}
 * and no {@code META-INF/neoforge.mods.toml} of its own. {@link SmoothSlopesMod} (in {@code main},
 * where annotation scanning is unambiguously proven to work every single run) looks this class up
 * by name via plain reflection and calls {@link #register} directly -- ordinary JVM classloading,
 * not NeoForge's mod-file discovery, is the only thing this now depends on. {@code main} never
 * references this class directly (no compile-time dependency either direction), so a normal
 * build where this class is absent from the classpath entirely (every run except the dedicated
 * {@code ftfPresetData} one) is unaffected; {@code SmoothSlopesMod}'s lookup simply catches
 * {@code ClassNotFoundException} and does nothing.
 */
public final class FreeTerraForgedPresetFixture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK_NAME = "freeterraforged_default_preset";
    /** Must match build.gradle's {@code ftfPresetData} run's systemProperty key exactly. */
    private static final String FIXTURE_RUN_PROPERTY = "smoothslopes.ftfPresetFixture";

    private FreeTerraForgedPresetFixture() {}

    /** Called reflectively by {@code SmoothSlopesMod} -- see this class's own doc for why. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(FreeTerraForgedPresetFixture::gatherData);
    }

    static void gatherData(GatherDataEvent event) {
        // Diagnostic (MINECRAFT-500 / Jira 52778): kept permanently, not just while narrowing this
        // down -- "BUILD SUCCESSFUL" with zero providers run and no exception has already happened
        // three times, so the log itself must be able to show which branch gatherData took.
        LOGGER.info("FreeTerraForgedPresetFixture.gatherData: includeServer={} systemProperty({})={}",
                event.includeServer(), FIXTURE_RUN_PROPERTY, System.getProperty(FIXTURE_RUN_PROPERTY));
        if (!event.includeServer() || System.getProperty(FIXTURE_RUN_PROPERTY) == null) {
            return;
        }
        PackOutput output = event.getGenerator().getPackOutput(PACK_NAME);
        LOGGER.info("FreeTerraForgedPresetFixture.gatherData: registering providers, output={}",
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
