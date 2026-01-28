package com.ctos.traincarts.service;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.ctos.trafficlight.model.BlockPosition;
import com.ctos.traincarts.model.BartStationConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Controls the placement and removal of redstone blocks when trains stop at BART stations.
 * Uses polling to detect when trains reach speed 0.
 */
public class BartRedstoneController {
    private static final Logger LOGGER = Logger.getLogger("ctOS");
    private static final int POLL_INTERVAL_TICKS = 5; // Poll every 5 ticks (0.25 seconds)

    private final Plugin plugin;
    private final BartStationManager stationManager;
    private boolean debugEnabled = false;

    // Trains waiting to reach speed 0 (maps to station name)
    private final Map<MinecartGroup, String> waitingTrains = new ConcurrentHashMap<>();

    // Currently active redstone blocks (to prevent duplicate placements)
    private final Set<BlockPosition> activeRedstoneBlocks = Collections.synchronizedSet(new HashSet<>());

    // The polling task
    private BukkitTask pollingTask;

    public BartRedstoneController(Plugin plugin, BartStationManager stationManager) {
        this.plugin = plugin;
        this.stationManager = stationManager;
    }

    /**
     * Sets whether debug logging is enabled
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    /**
     * Called when a train enters a BART station sign.
     * If the train is already stopped, immediately places the redstone.
     * Otherwise, starts polling to wait for the train to stop.
     */
    public void onTrainEnter(MinecartGroup group, String stationName) {
        if (group == null || stationName == null || stationName.isEmpty()) {
            return;
        }

        if (debugEnabled) {
            LOGGER.info("[BART DEBUG] Train entered station '" + stationName + "' - Train: " + group.getProperties().getTrainName());
        }

        BartStationConfig config = stationManager.getByStationName(stationName);
        if (config == null) {
            // No redstone config for this station
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] No redstone config for station: " + stationName);
            }
            return;
        }

        if (debugEnabled) {
            LOGGER.info("[BART DEBUG] Config found for station '" + stationName + "' - Delay: " + (config.getDelayTicks() / 20) + "s");
        }

        // Check if there's already an active redstone block for this config
        if (activeRedstoneBlocks.contains(config.getRedstonePosition())) {
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] Redstone already active at " + config.getRedstonePosition() + ", skipping");
            }
            return;
        }

        // Check current speed
        double speed = getTrainSpeed(group);
        if (debugEnabled) {
            LOGGER.info("[BART DEBUG] Train speed: " + String.format("%.2f", speed) + " blocks/tick");
        }

        if (speed == 0) {
            // Train is already stopped, place redstone immediately
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] Train already stopped, placing redstone immediately");
            }
            placeRedstone(config);
        } else {
            // Train is still moving, add to waiting list and start polling
            waitingTrains.put(group, stationName);
            startPollingIfNeeded();
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] Train moving, added to waiting list. Will poll every " + (POLL_INTERVAL_TICKS / 20.0) + "s");
            }
        }
    }

    /**
     * Gets the current speed of a train
     */
    private double getTrainSpeed(MinecartGroup group) {
        try {
            if (group.isEmpty()) {
                return 0;
            }
            return group.head().getRealSpeed();
        } catch (Exception e) {
            LOGGER.warning("Failed to get train speed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Starts the polling task if it's not already running
     */
    private synchronized void startPollingIfNeeded() {
        if (pollingTask == null || pollingTask.isCancelled()) {
            pollingTask = new BukkitRunnable() {
                @Override
                public void run() {
                    pollTrainSpeeds();
                }
            }.runTaskTimer(plugin, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
            LOGGER.fine("Started polling task for train speeds");
        }
    }

    /**
     * Stops the polling task if no more trains are waiting
     */
    private synchronized void stopPollingIfEmpty() {
        if (waitingTrains.isEmpty() && pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel();
            pollingTask = null;
            LOGGER.fine("Stopped polling task - no more waiting trains");
        }
    }

    /**
     * Polls all waiting trains to check if they've stopped
     */
    private void pollTrainSpeeds() {
        Iterator<Map.Entry<MinecartGroup, String>> it = waitingTrains.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<MinecartGroup, String> entry = it.next();
            MinecartGroup group = entry.getKey();
            String stationName = entry.getValue();

            // Check if train is still valid
            if (group.isEmpty() || !group.getWorld().equals(group.head().getWorld())) {
                if (debugEnabled) {
                    LOGGER.info("[BART DEBUG] Train removed from waiting list (invalid) - Station: " + stationName);
                }
                it.remove();
                continue;
            }

            // Check speed
            double speed = getTrainSpeed(group);
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] Polling train at station '" + stationName + "' - Speed: " + String.format("%.2f", speed) + " blocks/tick");
            }

            if (speed == 0) {
                // Train has stopped
                if (debugEnabled) {
                    LOGGER.info("[BART DEBUG] Train stopped! Placing redstone for station '" + stationName + "'");
                }
                BartStationConfig config = stationManager.getByStationName(stationName);
                if (config != null) {
                    placeRedstone(config);
                }
                it.remove();
            }
        }

        // Stop polling if no more waiting trains
        stopPollingIfEmpty();
    }

    /**
     * Places a redstone block at the configured position and schedules its removal
     */
    private void placeRedstone(BartStationConfig config) {
        BlockPosition redstonePos = config.getRedstonePosition();
        int delaySeconds = config.getDelayTicks() / 20;

        // Prevent duplicate placements
        if (!activeRedstoneBlocks.add(redstonePos)) {
            if (debugEnabled) {
                LOGGER.info("[BART DEBUG] Redstone already active at " + redstonePos);
            }
            return;
        }

        if (debugEnabled) {
            LOGGER.info("[BART DEBUG] ===== REDSTONE ACTIVATION =====");
            LOGGER.info("[BART DEBUG] Station: " + config.getStationName());
            LOGGER.info("[BART DEBUG] Position: " + redstonePos);
            LOGGER.info("[BART DEBUG] Will stay active for: " + delaySeconds + " seconds");
        }

        // Place the redstone block on the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Location loc = redstonePos.toLocation();
                    loc.getBlock().setType(Material.REDSTONE_BLOCK);
                    if (debugEnabled) {
                        LOGGER.info("[BART DEBUG] Redstone block placed successfully at " + redstonePos);
                    }

                    // Schedule removal after delay
                    scheduleRedstoneRemoval(config);
                } catch (Exception e) {
                    LOGGER.warning("[BART DEBUG] Failed to place redstone block: " + e.getMessage());
                    activeRedstoneBlocks.remove(redstonePos);
                }
            }
        }.runTask(plugin);
    }

    /**
     * Schedules the removal of the redstone block after the configured delay
     */
    private void scheduleRedstoneRemoval(BartStationConfig config) {
        BlockPosition redstonePos = config.getRedstonePosition();
        int delayTicks = config.getDelayTicks();
        int delaySeconds = delayTicks / 20;

        // Log countdown at intervals (at 75%, 50%, 25% remaining time)
        if (debugEnabled) {
            int[] countdownPoints = {
                (int)(delayTicks * 0.75),  // 75% time
                (int)(delayTicks * 0.50),  // 50% time
                (int)(delayTicks * 0.25)   // 25% time
            };

            for (int delay : countdownPoints) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int secondsElapsed = delay / 20;
                        int secondsRemaining = delaySeconds - secondsElapsed;
                        LOGGER.info("[BART DEBUG] Countdown - Station '" + config.getStationName() + "': " +
                                  secondsRemaining + " seconds remaining until redstone removal");
                    }
                }.runTaskLater(plugin, delay);
            }
        }

        // Final removal
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Location loc = redstonePos.toLocation();
                    loc.getBlock().setType(Material.AIR);
                    if (debugEnabled) {
                        LOGGER.info("[BART DEBUG] ===== REDSTONE DEACTIVATION =====");
                        LOGGER.info("[BART DEBUG] Station: " + config.getStationName());
                        LOGGER.info("[BART DEBUG] Position: " + redstonePos);
                        LOGGER.info("[BART DEBUG] Redstone removed after " + delaySeconds + " seconds");
                    }
                } catch (Exception e) {
                    LOGGER.warning("[BART DEBUG] Failed to remove redstone block: " + e.getMessage());
                } finally {
                    activeRedstoneBlocks.remove(redstonePos);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Called when a train leaves the station area (optional cleanup)
     */
    public void onTrainLeave(MinecartGroup group) {
        if (group != null) {
            String stationName = waitingTrains.get(group);
            if (stationName != null && debugEnabled) {
                LOGGER.info("[BART DEBUG] Train left station area before stopping - Station: " + stationName);
            }
            waitingTrains.remove(group);
            stopPollingIfEmpty();
        }
    }

    /**
     * Cleans up all resources (called on plugin disable)
     */
    public void shutdown() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel();
            pollingTask = null;
        }
        waitingTrains.clear();

        // Remove any active redstone blocks
        for (BlockPosition pos : new ArrayList<>(activeRedstoneBlocks)) {
            try {
                Location loc = pos.toLocation();
                if (loc.getBlock().getType() == Material.REDSTONE_BLOCK) {
                    loc.getBlock().setType(Material.AIR);
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to clean up redstone block at " + pos);
            }
        }
        activeRedstoneBlocks.clear();

        LOGGER.info("BartRedstoneController shutdown complete");
    }

    /**
     * Gets the number of trains waiting to stop
     */
    public int getWaitingTrainCount() {
        return waitingTrains.size();
    }

    /**
     * Gets the number of active redstone blocks
     */
    public int getActiveRedstoneCount() {
        return activeRedstoneBlocks.size();
    }
}
