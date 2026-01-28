package com.ctos.trafficlight.state;

import com.ctos.CtOSPlugin;
import com.ctos.trafficlight.model.Intersection;
import com.ctos.traincarts.state.BartSetupSession;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages all active wand setup sessions
 */
public class WandStateManager {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final CtOSPlugin plugin;
    private final Map<UUID, SetupSession> activeSessions;
    private final Map<UUID, BartSetupSession> bartSessions;

    public WandStateManager(CtOSPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
        this.bartSessions = new HashMap<>();

        // Start cleanup task to remove expired sessions
        startCleanupTask();
    }

    /**
     * Starts a new setup session for a player
     */
    public SetupSession startSession(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(playerId)) {
            cancelSession(player);
        }

        SetupSession session = new SetupSession(playerId);
        session.start();
        activeSessions.put(playerId, session);

        LOGGER.info("Started setup session for player: " + player.getName());

        return session;
    }

    /**
     * Starts an edit session for an existing intersection
     */
    public SetupSession startEditSession(Player player, Intersection intersection) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(playerId)) {
            cancelSession(player);
        }

        SetupSession session = new SetupSession(playerId);
        session.startEdit(intersection);
        activeSessions.put(playerId, session);

        LOGGER.info("Started edit session for player: " + player.getName() + " on intersection: " + intersection.getName());

        return session;
    }

    /**
     * Gets the active session for a player
     */
    public Optional<SetupSession> getSession(Player player) {
        return Optional.ofNullable(activeSessions.get(player.getUniqueId()));
    }

    /**
     * Gets the active session for a player by UUID
     */
    public Optional<SetupSession> getSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Removes a session
     */
    public void removeSession(Player player) {
        SetupSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            LOGGER.info("Removed setup session for player: " + player.getName());
        }
    }

    /**
     * Removes a session by UUID
     */
    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    /**
     * Cancels a session and notifies the player
     */
    public void cancelSession(Player player) {
        SetupSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("[ctOS] Setup cancelled.")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            WandState.removeWandFromInventory(player);
            LOGGER.info("Cancelled setup session for player: " + player.getName());
        }
    }

    /**
     * Checks if a player has an active session
     */
    public boolean hasSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    // BART Session Methods

    /**
     * Starts a new BART setup session for a player
     */
    public BartSetupSession startBartSession(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(playerId)) {
            cancelSession(player);
        }
        if (bartSessions.containsKey(playerId)) {
            cancelBartSession(player);
        }

        BartSetupSession session = new BartSetupSession(playerId);
        session.start();
        bartSessions.put(playerId, session);

        LOGGER.info("Started BART setup session for player: " + player.getName());

        return session;
    }

    /**
     * Starts a BART edit session for an existing configuration
     */
    public BartSetupSession startBartEditSession(Player player, com.ctos.traincarts.model.BartStationConfig config) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(playerId)) {
            cancelSession(player);
        }
        if (bartSessions.containsKey(playerId)) {
            cancelBartSession(player);
        }

        BartSetupSession session = BartSetupSession.forEdit(playerId, config);
        bartSessions.put(playerId, session);

        LOGGER.info("Started BART edit session for player: " + player.getName() + " on station: " + config.getStationName());

        return session;
    }

    /**
     * Gets the active BART session for a player
     */
    public Optional<BartSetupSession> getBartSession(Player player) {
        return Optional.ofNullable(bartSessions.get(player.getUniqueId()));
    }

    /**
     * Gets the active BART session for a player by UUID
     */
    public Optional<BartSetupSession> getBartSession(UUID playerId) {
        return Optional.ofNullable(bartSessions.get(playerId));
    }

    /**
     * Removes a BART session
     */
    public void removeBartSession(Player player) {
        BartSetupSession session = bartSessions.remove(player.getUniqueId());
        if (session != null) {
            LOGGER.info("Removed BART setup session for player: " + player.getName());
        }
    }

    /**
     * Removes a BART session by UUID
     */
    public void removeBartSession(UUID playerId) {
        bartSessions.remove(playerId);
    }

    /**
     * Cancels a BART session and notifies the player
     */
    public void cancelBartSession(Player player) {
        BartSetupSession session = bartSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("[ctOS BART] Setup cancelled.")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            WandState.removeWandFromInventory(player);
            LOGGER.info("Cancelled BART setup session for player: " + player.getName());
        }
    }

    /**
     * Checks if a player has an active BART session
     */
    public boolean hasBartSession(Player player) {
        return bartSessions.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player has any active session (traffic light or BART)
     */
    public boolean hasAnySession(Player player) {
        return hasSession(player) || hasBartSession(player);
    }

    /**
     * Gets the number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Cleans up expired sessions
     */
    public void cleanupExpiredSessions() {
        List<UUID> expiredSessions = new ArrayList<>();

        for (Map.Entry<UUID, SetupSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredSessions.add(entry.getKey());
            }
        }

        for (UUID playerId : expiredSessions) {
            activeSessions.remove(playerId);
            LOGGER.info("Removed expired setup session for player: " + playerId);
        }

        // Cleanup expired BART sessions
        List<UUID> expiredBartSessions = new ArrayList<>();

        for (Map.Entry<UUID, BartSetupSession> entry : bartSessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredBartSessions.add(entry.getKey());
            }
        }

        for (UUID playerId : expiredBartSessions) {
            bartSessions.remove(playerId);
            LOGGER.info("Removed expired BART setup session for player: " + playerId);
        }
    }

    /**
     * Starts the periodic cleanup task
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Clears all sessions (for plugin shutdown)
     */
    public void clearAllSessions() {
        activeSessions.clear();
        bartSessions.clear();
        LOGGER.info("Cleared all setup sessions");
    }
}
