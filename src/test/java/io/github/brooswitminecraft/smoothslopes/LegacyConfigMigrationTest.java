package io.github.brooswitminecraft.smoothslopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyConfigMigrationTest {
    @Test
    void missingLegacyFileReadsAsNull(@TempDir Path dir) {
        assertNull(LegacyConfigMigration.readLegacyWorldgenSmoothing(dir.resolve("dynamicterrain-server.toml")));
    }

    @Test
    void legacyFileWithoutTheKeyReadsAsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicterrain-server.toml");
        Files.writeString(file, "erosionEnabled = false\n");
        assertNull(LegacyConfigMigration.readLegacyWorldgenSmoothing(file));
    }

    @Test
    void legacyFileWithExplicitFalseReadsAsFalse(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicterrain-server.toml");
        Files.writeString(file, "erosionEnabled = false\n\tworldgenSmoothing = false\n");
        assertEquals(Boolean.FALSE, LegacyConfigMigration.readLegacyWorldgenSmoothing(file));
    }

    @Test
    void legacyFileWithExplicitTrueReadsAsTrue(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicterrain-server.toml");
        Files.writeString(file, "worldgenSmoothing = true\n");
        assertEquals(Boolean.TRUE, LegacyConfigMigration.readLegacyWorldgenSmoothing(file));
    }

    @Test
    void aDirectoryWhereTheFileShouldBeReadsAsNullRatherThanThrowing(@TempDir Path dir) throws IOException {
        Path asDirectory = dir.resolve("dynamicterrain-server.toml");
        Files.createDirectory(asDirectory);
        assertNull(LegacyConfigMigration.readLegacyWorldgenSmoothing(asDirectory));
    }
}
