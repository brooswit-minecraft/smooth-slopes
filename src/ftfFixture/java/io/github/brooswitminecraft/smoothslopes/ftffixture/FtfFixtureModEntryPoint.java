package io.github.brooswitminecraft.smoothslopes.ftffixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * MINECRAFT-500 / Wroosbit review 52775: the real {@code @Mod} entry point for this fixture's own
 * separate mod ({@code smoothslopesftffixture}), confined to the {@code ftfFixture} sourceSet and
 * never referenced by {@code main} in any way, direct or reflective.
 *
 * <p>An earlier attempt had {@code SmoothSlopesMod} (in {@code main}) look this fixture up
 * reflectively via {@code Class.forName} once this became a real declared mod -- confirmed live
 * that this still fails with {@code ClassNotFoundException} even though the class is genuinely
 * present on the launched JVM's own classpath: FML loads each declared mod (this one included)
 * into its own isolated module, so a different mod's {@code Class.forName} can never see it
 * regardless of classpath presence. Registering {@link FreeTerraForgedPresetFixture} from here --
 * this mod's own constructor, with its own injected {@link IEventBus} -- needs no cross-module
 * visibility at all, and keeps {@code main} with zero knowledge of this fixture's existence,
 * which is strictly stronger isolation evidence for Wroosbit review 52775 than the reflective
 * lookup it replaces.
 */
@Mod(FtfFixtureModEntryPoint.MODID)
public final class FtfFixtureModEntryPoint {
    static final String MODID = "smoothslopesftffixture";

    public FtfFixtureModEntryPoint(IEventBus modEventBus) {
        FreeTerraForgedPresetFixture.register(modEventBus);
    }
}
