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
 * {@code PackMcmetaProvider}, {@link FtfFixtureModEntryPoint}, and the FreeTerraForged dependency
 * it compiles against all live in the separate {@code ftfFixture} sourceSet (see
 * {@code build.gradle}), never {@code main}. {@code main} has no compile-time or reflective
 * reference to anything in this sourceSet at all.
 *
 * <p><b>Why this is its own separate mod, registered from its own constructor (four prior attempts
 * at other mechanisms, each confirmed live against real CI/local runs):</b>
 * <ol>
 * <li>A sourceSet's own {@code runtimeOnly} dependency does not ride along with that sourceSet's
 * own {@code ModModel} at run time -- FreeTerraForged's jar was absent from the run's classpath
 * even though the fixture's own mod file loaded.</li>
 * <li>{@code RunModel.additionalRuntimeClasspathConfiguration} did not fix that either --
 * {@code "All providers took: 0 ms, total files: 0"}, zero providers ever ran. Decompiling
 * moddev-gradle 2.0.147 later confirmed why: that configuration never feeds
 * {@code RunGameTask.classpathProvider}, the only file collection actually concatenated onto the
 * real launched JVM's classpath.</li>
 * <li>Making {@code ftfFixture} the run's primary {@code sourceSet} got the mod file "found" and
 * even "Attempting to inject @EventBusSubscriber classes" logged for it, but the annotation scan
 * itself found nothing.</li>
 * <li>Putting this class's compiled output directly on {@code classpathProvider} (confirmed
 * present in a live dump of {@code System.getProperty("java.class.path")}) and having
 * {@code SmoothSlopesMod} (in {@code main}) reflectively {@code Class.forName} it still threw
 * {@code ClassNotFoundException} -- FML loads each declared mod into its own isolated module, so a
 * different mod's {@code Class.forName} can never see this class regardless of raw classpath
 * presence.</li>
 * </ol>
 * The only mechanism that actually works: this sourceSet is a real, separate NeoForge mod
 * ({@code smoothslopesftffixture}, see its own {@code neoforge.mods.toml}), and
 * {@link FtfFixtureModEntryPoint} -- that mod's own {@code @Mod} constructor, in its own module --
 * registers {@link #register} on its own injected event bus. No cross-module visibility is needed
 * at all, and {@code main} is left with zero knowledge of this fixture's existence.
 */
public final class FreeTerraForgedPresetFixture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK_NAME = "freeterraforged_default_preset";

    private FreeTerraForgedPresetFixture() {}

    /** Called by {@link FtfFixtureModEntryPoint}'s own constructor -- see this class's own doc. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(FreeTerraForgedPresetFixture::gatherData);
    }

    static void gatherData(GatherDataEvent event) {
        LOGGER.info("FreeTerraForgedPresetFixture.gatherData: includeServer={}", event.includeServer());
        if (!event.includeServer()) {
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
