package io.github.brooswitminecraft.smoothslopes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a dedicated server's own per-world save directory from {@code server.properties}
 * ({@code level-name}, default {@code "world"} -- vanilla's own default) so the per-world legacy
 * migration can run before NeoForge's own {@code ConfigTracker} ever loads/corrects that world's
 * override files.
 *
 * <p>MINECRAFT-500 / Wroosbit review 52775 finding 3: confirmed live (via the real paired
 * acceptance test this finding required) that a {@code ServerAboutToStartEvent} listener runs too
 * late for this. NeoForge resolves this world's {@code serverconfig} override directory and
 * loads/corrects EVERY registered mod's per-world override file against that mod's current spec
 * immediately before firing that event ({@code ServerLifecycleHooks.handleServerAboutToStart}) --
 * including Dynamic Terrain's own file, against its current spec, which no longer defines
 * {@code worldgenSmoothing} after MINECRAFT-500. That correction strips the legacy key before any
 * listener on that event, regardless of priority, ever has a chance to read it -- confirmed live:
 * the per-world file this migration tried to read had already been rewritten to Dynamic Terrain's
 * other, unrelated config keys with their defaults, worldgenSmoothing simply gone.
 *
 * <p>Mod construction happens long before any server starts, so reading {@code server.properties}
 * directly here -- the same file, and the same key, NeoForge/Minecraft itself reads to resolve
 * level-name for a dedicated server launch -- is early enough to read Dynamic Terrain's legacy
 * per-world file before NeoForge ever touches it.
 */
final class DedicatedServerLevelName {
    private static final Pattern LEVEL_NAME = Pattern.compile("(?m)^\\s*level-name\\s*=\\s*(\\S+)\\s*$");

    private DedicatedServerLevelName() {}

    /**
     * @return the level-name this dedicated server will use, or {@code "world"} if
     *     {@code serverPropertiesFile} is missing, unreadable, or doesn't set the key.
     */
    static String read(Path serverPropertiesFile) {
        if (!Files.isRegularFile(serverPropertiesFile)) {
            return "world";
        }
        try {
            Matcher matcher = LEVEL_NAME.matcher(Files.readString(serverPropertiesFile));
            return matcher.find() ? matcher.group(1) : "world";
        } catch (IOException e) {
            return "world";
        }
    }
}
