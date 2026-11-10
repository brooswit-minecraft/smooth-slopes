package io.github.brooswitminecraft.smoothslopes;

/**
 * Pure decision for migrating Dynamic Terrain's pre-split {@code worldgenSmoothing} choice into
 * this mod's own config default, free of file IO (see {@link LegacyConfigMigration} for the
 * actual read). MINECRAFT-500: upgrading a server that had explicitly disabled worldgen smoothing
 * must not silently re-enable it just because the key now lives in a different mod's config file.
 */
final class MigrationPolicy {
    private MigrationPolicy() {}

    /**
     * @param legacyValue Dynamic Terrain's own {@code worldgenSmoothing} value read from its
     *     pre-split config file, or {@code null} if that file is missing or never set the key
     *     (a fresh install, or a server that never touched the default).
     * @return the default this mod's own {@code worldgenSmoothing} key should take on first write.
     */
    static boolean defaultWorldgenSmoothing(Boolean legacyValue) {
        return legacyValue == null || legacyValue;
    }
}
