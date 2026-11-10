package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerWorldConfigMigrationTest {
    @Test
    void migratesWhenNoOwnOverrideExistsAndLegacySetTheKey() {
        assertTrue(PerWorldConfigMigration.shouldMigrate(false, false));
        assertTrue(PerWorldConfigMigration.shouldMigrate(false, true));
    }

    @Test
    void anExistingOwnOverrideAlwaysWinsEvenWithALegacyValue() {
        // MINECRAFT-500 review: existing-new-config precedence -- a per-world choice already made
        // for this mod, operator-authored or migrated on a previous start, must never be overwritten.
        assertFalse(PerWorldConfigMigration.shouldMigrate(true, false));
        assertFalse(PerWorldConfigMigration.shouldMigrate(true, true));
    }

    @Test
    void noLegacyValueMeansNothingToMigrate() {
        assertFalse(PerWorldConfigMigration.shouldMigrate(false, null));
    }

    @Test
    void writesTheMigratedValueWhenNoOwnOverrideExists(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("serverconfig/dynamicterrain-server.toml");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "worldgenSmoothing = false\n");
        Path ownFile = dir.resolve("serverconfig/smoothslopes-server.toml");

        assertTrue(PerWorldConfigMigration.migrate(legacyFile, ownFile));
        assertTrue(Files.exists(ownFile));
        assertEquals("worldgenSmoothing = false\n", Files.readString(ownFile));
    }

    @Test
    void doesNotOverwriteAnExistingOwnOverrideFile(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("serverconfig/dynamicterrain-server.toml");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "worldgenSmoothing = false\n");
        Path ownFile = dir.resolve("serverconfig/smoothslopes-server.toml");
        Files.writeString(ownFile, "worldgenSmoothing = true\n"); // the operator's own deliberate choice

        assertFalse(PerWorldConfigMigration.migrate(legacyFile, ownFile));
        assertEquals("worldgenSmoothing = true\n", Files.readString(ownFile));
    }

    @Test
    void noLegacyFileWritesNothing(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("serverconfig/dynamicterrain-server.toml");
        Path ownFile = dir.resolve("serverconfig/smoothslopes-server.toml");

        assertFalse(PerWorldConfigMigration.migrate(legacyFile, ownFile));
        assertFalse(Files.exists(ownFile));
    }
}
