package io.github.brooswitminecraft.smoothslopes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.slf4j.Logger;

/**
 * Migrates every per-world legacy {@code worldgenSmoothing} override this JVM's own game
 * directory could possibly load, before any of them are actually loaded -- not just a single
 * save resolvable in advance by name.
 *
 * <p>MINECRAFT-500 / Wroosbit review 52785: a dedicated server's own save sits directly under the
 * game directory under whatever name {@code level-name} gives it, known statically before the
 * server starts. A SINGLEPLAYER/integrated-server save has no such advance name at all -- it is
 * chosen interactively, well after mod construction -- so a migration keyed to one resolved save
 * name can only ever cover the dedicated-server case. NeoForge's own per-world config correction
 * strips the legacy key before any {@code ServerAboutToStartEvent} listener can read it regardless
 * of which save is eventually chosen (confirmed live), so the only way to beat that race for every
 * possible save is to migrate all of them up front, at mod construction, long before any server
 * (dedicated or integrated) actually starts for any one of them.
 *
 * <p>Scans both {@code <gameDir>/<name>/serverconfig/} (a dedicated server's own save) and
 * {@code <gameDir>/saves/<name>/serverconfig/} (every singleplayer/integrated-server save). Both
 * client and dedicated-server launches run with the game directory as the process's own working
 * directory, so {@code gameDir} is always {@code Path.of(".")} in practice.
 */
final class EarlyPerWorldLegacyMigration {
    private EarlyPerWorldLegacyMigration() {}

    static void migrateAll(Path gameDir, Logger logger) {
        migrateCandidates(gameDir, logger);
        Path saves = gameDir.resolve("saves");
        if (Files.isDirectory(saves)) {
            migrateCandidates(saves, logger);
        }
    }

    private static void migrateCandidates(Path parent, Logger logger) {
        try (Stream<Path> children = Files.list(parent)) {
            children.filter(Files::isDirectory).forEach(world -> migrateOne(world, logger));
        } catch (IOException e) {
            // parent (gameDir or saves/) itself unreadable -- nothing to migrate, not fatal.
        }
    }

    private static void migrateOne(Path world, Logger logger) {
        Path worldConfigDir = world.resolve("serverconfig");
        Path legacyFile = worldConfigDir.resolve("dynamicterrain-server.toml");
        Path ownFile = worldConfigDir.resolve("smoothslopes-server.toml");
        try {
            PerWorldConfigMigration.migrate(legacyFile, ownFile);
        } catch (IOException e) {
            logger.warn("Could not migrate per-world worldgenSmoothing override to {}: {}", ownFile, e.toString());
        }
    }
}
