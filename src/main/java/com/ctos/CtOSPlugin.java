package com.ctos;

import com.ctos.commands.WandCommand;
import com.ctos.listeners.WandInteractionListener;
import com.ctos.trafficlight.model.BlockStateData;
import com.ctos.trafficlight.model.Intersection;
import com.ctos.trafficlight.service.IntersectionManager;
import com.ctos.trafficlight.service.IntersectionPersistence;
import com.ctos.trafficlight.service.TrafficLightAnimator;
import com.ctos.trafficlight.state.WandState;
import com.ctos.trafficlight.state.WandStateManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Main plugin class for ctOS Traffic Lights
 */
public class CtOSPlugin extends JavaPlugin {

    // Core managers and services
    private IntersectionManager intersectionManager;
    private IntersectionPersistence persistence;
    private TrafficLightAnimator animator;
    private WandStateManager wandStateManager;

    // Auto-save task
    private BukkitRunnable autoSaveTask;

    @Override
    public void onEnable() {
        getLogger().info("==============================================");
        getLogger().info("  ctOS Traffic Lights Plugin Starting");
        getLogger().info("==============================================");

        // 1. Save default configuration if it doesn't exist
        saveDefaultConfig();

        // 1.5 Set debug mode from config
        BlockStateData.setDebugEnabled(getConfig().getBoolean("debug", false));

        // 2. Create data directory for intersections
        File dataDirectory = new File(getDataFolder(), "intersections");
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
            getLogger().info("Created intersections data directory");
        }

        // 3. Initialize managers and services
        getLogger().info("Initializing managers...");
        intersectionManager = new IntersectionManager();
        persistence = new IntersectionPersistence(dataDirectory);
        animator = new TrafficLightAnimator(this, intersectionManager);
        wandStateManager = new WandStateManager(this);

        // 4. Initialize WandState
        WandState.initialize(this);

        // 5. Load intersections from disk
        getLogger().info("Loading intersections from disk...");
        loadIntersections();

        // 6. Register commands
        getLogger().info("Registering commands...");
        WandCommand wandCommand = new WandCommand(this, wandStateManager, intersectionManager, this.persistence);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (event ->
                event.registrar().register(wandCommand.buildCommand(), "Main ctOS command", List.of("tl", "trafficlights"))
        ));

        // 7. Register listeners
        getLogger().info("Registering event listeners...");
        WandInteractionListener listener = new WandInteractionListener(this, wandStateManager, intersectionManager, persistence);
        Bukkit.getPluginManager().registerEvents(listener, this);

        // 8. Start the traffic light animator
        getLogger().info("Starting traffic light animator...");
        animator.start();

        // 9. Start auto-save task
        startAutoSaveTask();

        getLogger().info("==============================================");
        getLogger().info("  ctOS Traffic Lights Plugin Enabled!");
        getLogger().info("  Loaded " + intersectionManager.getIntersectionCount() + " intersections");
        getLogger().info("  Active animations: " + animator.getActiveCycleCount());
        getLogger().info("==============================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("==============================================");
        getLogger().info("  ctOS Traffic Lights Plugin Shutting Down");
        getLogger().info("==============================================");

        // 1. Stop auto-save task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        // 2. Stop the animator
        if (animator != null) {
            getLogger().info("Stopping traffic light animator...");
            animator.stop();
        }

        // 3. Save all intersections
        getLogger().info("Saving all intersections...");
        saveAllIntersections();

        // 4. Clear all setup sessions
        if (wandStateManager != null) {
            getLogger().info("Clearing setup sessions...");
            wandStateManager.clearAllSessions();
        }

        // 5. Clear intersection manager
        if (intersectionManager != null) {
            intersectionManager.clear();
        }

        getLogger().info("==============================================");
        getLogger().info("  ctOS Traffic Lights Plugin Disabled");
        getLogger().info("==============================================");
    }

    /**
     * Loads all intersections from disk
     */
    private void loadIntersections() {
        try {
            List<Intersection> intersections = persistence.loadAll();

            for (Intersection intersection : intersections) {
                // Register with manager
                intersectionManager.registerIntersection(intersection);

                // Register with animator if complete
                if (intersection.isComplete()) {
                    animator.registerIntersection(intersection);
                }
            }

            getLogger().info("Successfully loaded " + intersections.size() + " intersections");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading intersections", e);
        }
    }

    /**
     * Saves all intersections to disk
     */
    private void saveAllIntersections() {
        int saved = 0;
        int failed = 0;

        for (Intersection intersection : intersectionManager.getAllIntersections()) {
            try {
                persistence.saveIntersection(intersection);
                saved++;
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save intersection: " + intersection.getName(), e);
                failed++;
            }
        }

        getLogger().info("Saved " + saved + " intersections" + (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    /**
     * Starts the auto-save task
     */
    private void startAutoSaveTask() {
        int interval = getConfig().getInt("storage.auto-save-interval", 6000);

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Auto-saving intersections...");
                saveAllIntersections();
            }
        };

        autoSaveTask.runTaskTimerAsynchronously(this, interval, interval);
        getLogger().info("Auto-save task started (interval: " + interval + " ticks)");
    }

    // Getters for other classes to access managers

    public IntersectionManager getIntersectionManager() {
        return intersectionManager;
    }

    public IntersectionPersistence getPersistence() {
        return persistence;
    }

    public TrafficLightAnimator getAnimator() {
        return animator;
    }

    public WandStateManager getWandStateManager() {
        return wandStateManager;
    }

    /**
     * Checks if debug mode is enabled in config
     */
    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug", false);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        // Update debug flag in BlockStateData
        BlockStateData.setDebugEnabled(getConfig().getBoolean("debug", false));
    }
}
