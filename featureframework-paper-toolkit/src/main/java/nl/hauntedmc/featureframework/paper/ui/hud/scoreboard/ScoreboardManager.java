package nl.hauntedmc.featureframework.paper.ui.hud.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Central manager for per-player scoreboards (sidebar) and glow teams.
 * Enforces one-team-per-entry-per-board. Nametags are hidden via the glow team itself.
 */
public class ScoreboardManager {

    private static final String GLOW_PREFIX = "sf_glow_";
    private static final String OBJ_NAME = "ServerSB";
    private static final long GLOW_CONFLICT_WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private static final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private static final Map<UUID, NamedTextColor> glowColors = new ConcurrentHashMap<>();
    private static final Map<GlowConflictKey, Long> glowConflictWarnings = new ConcurrentHashMap<>();

    /**
     * Called on PlayerJoinEvent
     */
    public static void onPlayerJoin(Player player) {
        // Create personal board
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);

        // Ensure all glow teams exist on this board
        ensureGlowTeamsRegistered(board);

        // Put current online players into their glow team on THIS board
        for (Player other : Bukkit.getOnlinePlayers()) {
            NamedTextColor c = glowColors.getOrDefault(other.getUniqueId(), NamedTextColor.WHITE);
            moveEntryToGlowTeam(board, other.getName(), c, "populate on " + player.getName() + "'s board");
        }

        // Give this board to the joining player
        player.setScoreboard(board);

        // Now inject the joining player into EVERYONE ELSE'S boards
        NamedTextColor myColor = glowColors.getOrDefault(player.getUniqueId(), NamedTextColor.WHITE);
        boards.forEach((uuid, otherBoard) -> {
            if (uuid.equals(player.getUniqueId())) return;
            moveEntryToGlowTeam(otherBoard, player.getName(), myColor, "inject into " + getName(uuid) + "'s board");
        });
    }


    /**
     * Called on PlayerQuitEvent
     */
    public static void onPlayerQuit(Player player) {
        internalQuitCleanup(player, /*resetSidebar=*/false);
    }

    /**
     * Call during plugin enable to attach personal boards for players already online.
     */
    public static void initializeOnlinePlayers(Logger logger) {
        int initialized = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                onPlayerJoin(player);
                initialized++;
            } catch (Throwable t) {
                if (logger != null) {
                    logger.info("Failed to initialize scoreboard for " + player.getName() + ": " + t.getMessage());
                }
            }
        }
        if (initialized > 0) {
            if (logger != null) {
                logger.info("Initialized scoreboards for " + initialized + " already online player(s).");
            }
        }
    }

    /**
     * Force-run quit cleanup for every online player.
     * Safe to call from onDisable(); also clears internal maps.
     */
    public static void cleanupOnlinePlayers(Logger logger) {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            try {
                internalQuitCleanup(p, /*resetSidebar=*/true);
            } catch (Throwable t) {
                if (logger != null) {
                    logger.warning("[ScoreboardManager] Shutdown cleanup failed for " + p.getName() + ": " + t.getMessage());
                }
            }
        }
        boards.clear();
        glowColors.clear();
        glowConflictWarnings.clear();
    }

    /**
     * Shared cleanup used by both real quits and plugin shutdown.
     */
    private static void internalQuitCleanup(Player player, boolean resetSidebar) {
        String entry = player.getName();

        // Remove this player's entry from our glow team on every personal board.
        boards.values().forEach(board -> {
            Team current = board.getEntryTeam(entry);
            if (current != null && isGlowTeam(current.getName())) {
                current.removeEntry(entry);
            }

            if (resetSidebar) {
                Objective obj = board.getObjective(OBJ_NAME);
                if (obj != null) obj.unregister();
            }
        });

        // Forget color and personal board
        glowColors.remove(player.getUniqueId());
        boards.remove(player.getUniqueId());
        glowConflictWarnings.keySet().removeIf(key -> key.entry().equals(entry));

        // Return to main board to avoid dangling personal boards
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Updates this player's sidebar lines.
     */
    public static void updateSidebar(Player player, Component title, List<Component> newLines, List<Component> oldLines) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY, title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.displayName(title);
        } else {
            obj.displayName(title);
        }

        for (int i = 0; i < newLines.size(); i++) {
            int scoreValue = newLines.size() - i;
            String lineKey = "line" + i;
            Score score = obj.getScore(lineKey);

            boolean needsUpdate = oldLines == null || i >= oldLines.size() || !oldLines.get(i).equals(newLines.get(i));
            if (needsUpdate) {
                score.setScore(scoreValue);
                score.customName(newLines.get(i));
                score.numberFormat(NumberFormat.blank());
            }
        }

        if (oldLines != null && oldLines.size() > newLines.size()) {
            for (int i = newLines.size(); i < oldLines.size(); i++) {
                String lineKey = "line" + i;
                board.resetScores(lineKey);
            }
        }
    }

    public static void removeSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        Objective obj = board.getObjective(OBJ_NAME);
        if (obj != null) obj.unregister();
    }

    /**
     * Apply glow color for everyone: moves {@code <player>} into the chosen glow team
     * on every personal scoreboard, and toggles their glowing flag.
     */
    public static void setGlow(Player player, NamedTextColor color) {
        applyGlowState(player, color, true);
    }

    private static void applyGlowState(Player player, NamedTextColor color, boolean glowing) {
        glowColors.put(player.getUniqueId(), color);
        String entry = player.getName();
        for (Scoreboard board : boards.values()) {
            moveEntryToGlowTeam(board, entry, color, "setGlow");
        }

        if (player.isGlowing() != glowing) {
            player.setGlowing(glowing);
        }
    }

    /**
     * Disable glow (fall back to WHITE)
     */
    public static void removeGlow(Player player) {
        applyGlowState(player, NamedTextColor.WHITE, false);
    }

    /* ---------- helpers ---------- */

    private static void ensureGlowTeamsRegistered(Scoreboard board) {
        for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            getOrCreateGlowTeam(board, color);
        }
    }

    static void moveEntryToGlowTeam(
            Scoreboard board,
            String entry,
            NamedTextColor targetColor,
            String context
    ) {
        String targetName = glowTeamName(targetColor);
        Team current = board.getEntryTeam(entry);
        if (current != null && !isGlowTeam(current.getName())) {
            warnGlowConflict(entry, current.getName(), context);
            return;
        }
        if (current != null && targetName.equals(current.getName())) {
            return;
        }
        if (current != null) {
            current.removeEntry(entry);
        }

        Team target = board.getTeam(targetName);
        if (target == null) {
            target = getOrCreateGlowTeam(board, targetColor);
        }
        target.addEntry(entry);
    }

    private static Team getOrCreateGlowTeam(Scoreboard board, NamedTextColor color) {
        String name = glowTeamName(color);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.color(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    private static String glowTeamName(NamedTextColor color) {
        return GLOW_PREFIX + color.toString();
    }

    private static boolean isGlowTeam(String teamName) {
        return teamName != null && teamName.startsWith(GLOW_PREFIX);
    }

    private static void warn(String msg, Object... args) {
        String out = msg;
        for (Object a : args) {
            out = out.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
        }
        Bukkit.getLogger().warning("[ScoreboardManager] " + out);
    }

    private static void warnGlowConflict(String entry, String teamName, String context) {
        GlowConflictKey key = new GlowConflictKey(entry, teamName);
        long now = System.nanoTime();
        Long previous = glowConflictWarnings.putIfAbsent(key, now);
        if (previous != null) {
            if (now - previous < GLOW_CONFLICT_WARNING_INTERVAL_NANOS
                    || !glowConflictWarnings.replace(key, previous, now)) {
                return;
            }
        }
        warn("Entry {} is already in non-glow team {} on a board during {}. Skipping our team change.",
                entry, teamName, context);
    }

    private static String getName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString();
    }

    private record GlowConflictKey(String entry, String teamName) {
    }
}
