package io.github.brooswitminecraft.smoothslopes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Dynamic Terrain's pre-split {@code worldgenSmoothing} choice directly out of its own
 * server config file on disk. Dynamic Terrain no longer defines this key itself after MINECRAFT-500
 * extracted worldgen smoothing into this mod, but a server upgrading from before the split may
 * still have {@code config/dynamicterrain-server.toml} on disk with it explicitly set — that file
 * is never rewritten or deleted by this migration, only read.
 */
final class LegacyConfigMigration {
    private static final Pattern WORLDGEN_SMOOTHING = Pattern.compile("(?m)^\\s*worldgenSmoothing\\s*=\\s*(true|false)\\s*$");

    private LegacyConfigMigration() {}

    /** @return the legacy value, or {@code null} if the file is missing, unreadable, or doesn't set the key. */
    static Boolean readLegacyWorldgenSmoothing(Path legacyConfigFile) {
        if (!Files.isRegularFile(legacyConfigFile)) {
            return null;
        }
        try {
            Matcher matcher = WORLDGEN_SMOOTHING.matcher(Files.readString(legacyConfigFile));
            return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
        } catch (IOException e) {
            // An unreadable legacy file falls back to the un-migrated default rather than crashing
            // mod startup over a config file this mod does not own.
            return null;
        }
    }
}
