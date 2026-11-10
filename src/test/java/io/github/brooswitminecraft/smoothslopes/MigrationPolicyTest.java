package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MigrationPolicyTest {
    @Test
    void noLegacyFileDefaultsToEnabled() {
        assertTrue(MigrationPolicy.defaultWorldgenSmoothing(null));
    }

    @Test
    void legacyFileExplicitlyEnabledStaysEnabled() {
        assertTrue(MigrationPolicy.defaultWorldgenSmoothing(true));
    }

    @Test
    void legacyFileExplicitlyDisabledCarriesForwardAsDisabled() {
        // MINECRAFT-500: a server that had turned Dynamic Terrain's worldgen smoothing off before
        // the split must not have it silently re-enabled by this mod's own default.
        assertFalse(MigrationPolicy.defaultWorldgenSmoothing(false));
    }
}
