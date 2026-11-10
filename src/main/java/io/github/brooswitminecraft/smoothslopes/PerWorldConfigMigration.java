package io.github.brooswitminecraft.smoothslopes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Migrates Dynamic Terrain's pre-split PER-WORLD {@code worldgenSmoothing} override: a world's
 * own {@code <world>/serverconfig/dynamicterrain-server.toml}, which NeoForge lets a server
 * operator use to override the global server config for just that one world (see
 * {@code ServerLifecycleHooks.getServerConfigPath}/{@code handleServerAboutToStart} in NeoForge
 * itself, which resolves this directory only once a specific world/server is actually starting).
 * The global-only migration ({@link LegacyConfigMigration}/{@link MigrationPolicy}) misses this: a
 * world could have had worldgen smoothing explicitly disabled only for itself, with the global
 * default left enabled (MINECRAFT-500 review, Tayne/John 52752).
 *
 * File IO and the write/skip decision only — the NeoForge-specific glue (resolving the real
 * per-world directory from a live {@code MinecraftServer}, and forcing SERVER configs to reload
 * against it) lives in {@link SmoothSlopesMod}'s own {@code ServerAboutToStartEvent} listener,
 * which this class has no dependency on, so this stays reachable from the plain headless test
 * sourceSet.
 */
final class PerWorldConfigMigration {
    private PerWorldConfigMigration() {}

    /**
     * @param ownOverrideExists whether this world already has its own Smooth Slopes per-world
     *     override file (operator-authored, or already migrated on a previous start) — an
     *     existing one always wins and is never overwritten.
     * @param legacyValue Dynamic Terrain's own per-world override value for this world, or
     *     {@code null} if that world has no such file or it never set the key.
     * @return true if a per-world override should be migrated now.
     */
    static boolean shouldMigrate(boolean ownOverrideExists, Boolean legacyValue) {
        return !ownOverrideExists && legacyValue != null;
    }

    /**
     * Reads {@code legacyFile} and, per {@link #shouldMigrate}, writes {@code ownFile} with the
     * migrated value.
     *
     * @return true if a file was written — the caller is responsible for making NeoForge actually
     *     reload SERVER configs against this world's override directory afterwards.
     */
    static boolean migrate(Path legacyFile, Path ownFile) throws IOException {
        Boolean legacyValue = LegacyConfigMigration.readLegacyWorldgenSmoothing(legacyFile);
        if (!shouldMigrate(Files.exists(ownFile), legacyValue)) {
            return false;
        }
        Files.createDirectories(ownFile.getParent());
        Files.writeString(ownFile, "worldgenSmoothing = " + legacyValue + "\n");
        return true;
    }
}
