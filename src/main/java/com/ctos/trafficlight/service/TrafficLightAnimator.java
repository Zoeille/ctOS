package com.ctos.trafficlight.service;

import com.ctos.CtOSPlugin;
import com.ctos.trafficlight.cycle.CyclePhase;
import com.ctos.trafficlight.cycle.TrafficCycle;
import com.ctos.trafficlight.model.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the animation/cycling of all traffic lights
 * This is the core engine that makes traffic lights work
 */
public class TrafficLightAnimator {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final CtOSPlugin plugin;
    private final IntersectionManager intersectionManager;
    private final Map<Intersection, TrafficCycle> cycles;
    private final Map<BlockPosition, LightPhase> currentBlockStates;
    private final Map<ElementPosition, LightPhase> currentElementStates; // For element-based tracking
    private BukkitTask animationTask;
    private int tickInterval;

    public TrafficLightAnimator(CtOSPlugin plugin, IntersectionManager intersectionManager) {
        this.plugin = plugin;
        this.intersectionManager = intersectionManager;
        this.cycles = new HashMap<>();
        this.currentBlockStates = new HashMap<>();
        this.currentElementStates = new HashMap<>();
        this.tickInterval = plugin.getConfig().getInt("animation.tick-interval", 10);
    }

    /**
     * Starts the animation engine
     */
    public void start() {
        if (animationTask != null) {
            LOGGER.warning("Animation task is already running!");
            return;
        }

        // Initialize cycles for all intersections
        for (Intersection intersection : intersectionManager.getAllIntersections()) {
            if (intersection.isComplete()) {
                cycles.put(intersection, new TrafficCycle(intersection));
            }
        }

        // Start the repeating task
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 0L, tickInterval);

        LOGGER.info("Traffic light animator started (tick interval: " + tickInterval + " ticks)");
    }

    /**
     * Stops the animation engine
     */
    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }

        cycles.clear();
        currentBlockStates.clear();
        currentElementStates.clear();

        LOGGER.info("Traffic light animator stopped");
    }

    /**
     * Registers a new intersection for animation
     */
    public void registerIntersection(Intersection intersection) {
        if (intersection.isComplete()) {
            cycles.put(intersection, new TrafficCycle(intersection));
            LOGGER.info("Registered intersection for animation: " + intersection.getName());
        }
    }

    /**
     * Unregisters an intersection from animation
     */
    public void unregisterIntersection(Intersection intersection) {
        cycles.remove(intersection);

        // Clear block states for this intersection
        for (BlockPosition pos : intersection.getAllBlocks()) {
            currentBlockStates.remove(pos);
        }

        // Clear element states for this intersection
        for (ElementPosition pos : intersection.getAllElementPositions()) {
            currentElementStates.remove(pos);
        }

        LOGGER.info("Unregistered intersection from animation: " + intersection.getName());
    }

    /**
     * Main tick method - called every tickInterval ticks
     */
    private void tick() {
        for (Map.Entry<Intersection, TrafficCycle> entry : cycles.entrySet()) {
            Intersection intersection = entry.getKey();
            TrafficCycle cycle = entry.getValue();

            // Tick the cycle (returns true if phase changed)
            boolean phaseChanged = cycle.tick();

            if (phaseChanged) {
                updateIntersection(intersection, cycle);
            }
        }
    }

    /**
     * Logs a debug message if debug mode is enabled
     */
    private void debug(String message) {
        if (plugin.isDebugEnabled()) {
            LOGGER.info("[DEBUG] " + message);
        }
    }

    /**
     * Updates all blocks for an intersection based on the current cycle phase
     */
    private void updateIntersection(Intersection intersection, TrafficCycle cycle) {
        CyclePhase currentPhase = cycle.getCurrentPhase();
        BlockStateData neutralState = intersection.getNeutralState();
        TrafficLightElement neutralElement = intersection.getNeutralElement();
        List<TrafficLightSide> sides = intersection.getSides();

        debug("=== Updating Intersection: " + intersection.getName() + " ===");
        debug("Current Phase: " + currentPhase);
        debug("Number of sides: " + sides.size());

        if (neutralState == null && neutralElement == null) {
            LOGGER.severe("ERROR: No neutral state defined for intersection " + intersection.getName());
        }

        // Update each side
        for (int i = 0; i < sides.size(); i++) {
            TrafficLightSide side = sides.get(i);

            // Determine phase based on actual direction, not index
            LightPhase lightPhase;
            boolean pedestrianGreen;

            TrafficLightSide.DirectionGroup group = side.getDirectionGroup();
            debug("Side " + i + " (" + side.getDirection() + ") has DirectionGroup: " + group);

            if (group == TrafficLightSide.DirectionGroup.NORTH_SOUTH) {
                lightPhase = currentPhase.getNsPhase();
                pedestrianGreen = currentPhase.isNsPedestrianGreen();
                debug("  -> Using NS phase: " + lightPhase);
            } else if (group == TrafficLightSide.DirectionGroup.EAST_WEST) {
                lightPhase = currentPhase.getEwPhase();
                pedestrianGreen = currentPhase.isEwPedestrianGreen();
                debug("  -> Using EW phase: " + lightPhase);
            } else {
                // Fallback to index-based for unknown directions
                lightPhase = currentPhase.getPhaseForSide(i, sides.size());
                pedestrianGreen = currentPhase.isPedestrianGreen(i, sides.size());
                LOGGER.warning("  -> Unknown direction, falling back to index-based: " + lightPhase);
            }

            // Update element-based lights (if present)
            if (side.hasElements()) {
                debug("  - RED elements: " + side.getLightElements(LightPhase.RED).size());
                debug("  - ORANGE elements: " + side.getLightElements(LightPhase.ORANGE).size());
                debug("  - GREEN elements: " + side.getLightElements(LightPhase.GREEN).size());

                updateSideElements(side, lightPhase, neutralElement, neutralState);

                if (side.hasPedestrianElements()) {
                    updatePedestrianElements(side, pedestrianGreen, neutralElement, neutralState);
                }
            }

            // Update legacy block-based lights
            debug("  - RED blocks: " + side.getLightBlocks(LightPhase.RED).size());
            debug("  - ORANGE blocks: " + side.getLightBlocks(LightPhase.ORANGE).size());
            debug("  - GREEN blocks: " + side.getLightBlocks(LightPhase.GREEN).size());

            updateSideLights(side, lightPhase, neutralState);

            if (side.hasPedestrianLights()) {
                updatePedestrianLights(side, pedestrianGreen, neutralState);
            }
        }
    }

    /**
     * Updates the lights for one side of an intersection
     */
    private void updateSideLights(TrafficLightSide side, LightPhase activePhase, BlockStateData neutralState) {
        debug("Updating side " + side.getDirection() + " to phase " + activePhase);
        int blocksUpdated = 0;

        // For each possible phase, update blocks
        for (LightPhase phase : LightPhase.values()) {
            List<BlockPosition> blocks = side.getLightBlocks(phase);

            for (BlockPosition blockPos : blocks) {
                // Determine what to display
                BlockStateData targetState;
                if (phase == activePhase) {
                    // This phase is active, show the configured block
                    targetState = side.getBlockState(blockPos);

                    // Check if target state is same as neutral (no visual change will occur)
                    if (plugin.isDebugEnabled() && neutralState != null && targetState != null &&
                        targetState.getMaterial() == neutralState.getMaterial() &&
                        targetState.getBlockDataString().equals(neutralState.getBlockDataString())) {
                        LOGGER.warning("  WARNING: Active block at " + blockPos + " is identical to neutral state! No visual change.");
                    }
                } else {
                    // This phase is inactive, show neutral state
                    targetState = neutralState;
                }

                // Only update if the block state has changed (optimization)
                // Use containsKey to distinguish "not initialized" from "set to neutral (null)"
                LightPhase currentState = currentBlockStates.get(blockPos);
                LightPhase newState = (phase == activePhase) ? phase : null; // null = neutral state
                boolean isFirstUpdate = !currentBlockStates.containsKey(blockPos);

                if (isFirstUpdate || currentState != newState) {
                    debug("  Setting block at " + blockPos + " to " +
                               (targetState != null ? targetState.getMaterial() : "null") +
                               " (phase: " + phase + ", active: " + (phase == activePhase) +
                               ", first: " + isFirstUpdate + ", changing from: " + currentState + " to: " + newState + ")");
                    applyBlockState(blockPos, targetState, side.getDirection());
                    currentBlockStates.put(blockPos, newState);
                    blocksUpdated++;
                }
            }
        }

        debug("  Total blocks updated for " + side.getDirection() + ": " + blocksUpdated);
    }

    /**
     * Updates pedestrian lights for a side
     * Note: Pedestrian lights preserve their original captured orientation
     * Neutral blocks copy the facing from the block they replace
     */
    private void updatePedestrianLights(TrafficLightSide side, boolean isGreen, BlockStateData neutralState) {
        List<BlockPosition> pedestrianGreenBlocks = side.getPedestrianGreenBlocks();
        List<BlockPosition> pedestrianRedBlocks = side.getPedestrianRedBlocks();

        if (isGreen) {
            // Show pedestrian green blocks (keep original orientation)
            for (BlockPosition blockPos : pedestrianGreenBlocks) {
                BlockStateData greenState = side.getBlockState(blockPos);
                applyBlockState(blockPos, greenState, null);
            }

            // Show neutral state on red blocks (copy facing from the red block)
            for (BlockPosition blockPos : pedestrianRedBlocks) {
                BlockStateData redState = side.getBlockState(blockPos);
                applyBlockStateWithFacing(blockPos, neutralState, redState);
            }
        } else {
            // Show neutral state on green blocks (copy facing from the green block)
            for (BlockPosition blockPos : pedestrianGreenBlocks) {
                BlockStateData greenState = side.getBlockState(blockPos);
                applyBlockStateWithFacing(blockPos, neutralState, greenState);
            }

            // Show pedestrian red blocks (keep original orientation)
            for (BlockPosition blockPos : pedestrianRedBlocks) {
                BlockStateData redState = side.getBlockState(blockPos);
                applyBlockState(blockPos, redState, null);
            }
        }
    }

    /**
     * Updates element-based lights for one side of an intersection
     */
    private void updateSideElements(TrafficLightSide side, LightPhase activePhase,
                                     TrafficLightElement neutralElement, BlockStateData neutralState) {
        debug("Updating side " + side.getDirection() + " elements to phase " + activePhase);
        int elementsUpdated = 0;

        // For each possible phase, update elements
        for (LightPhase phase : LightPhase.values()) {
            List<TrafficLightElement> elements = side.getLightElements(phase);

            for (TrafficLightElement element : elements) {
                ElementPosition pos = element.getPosition();

                // Determine what to display
                TrafficLightElement targetElement;
                if (phase == activePhase) {
                    targetElement = element;
                } else {
                    targetElement = neutralElement;
                }

                // Only update if the element state has changed
                LightPhase currentState = currentElementStates.get(pos);
                LightPhase newState = (phase == activePhase) ? phase : null;
                boolean isFirstUpdate = !currentElementStates.containsKey(pos);

                if (isFirstUpdate || currentState != newState) {
                    debug("  Setting element at " + pos + " (phase: " + phase + ", active: " + (phase == activePhase) + ")");
                    applyElementState(element, targetElement, side.getDirection(), neutralState);
                    currentElementStates.put(pos, newState);
                    elementsUpdated++;
                }
            }
        }

        debug("  Total elements updated for " + side.getDirection() + ": " + elementsUpdated);
    }

    /**
     * Updates pedestrian elements for a side
     */
    private void updatePedestrianElements(TrafficLightSide side, boolean isGreen,
                                           TrafficLightElement neutralElement, BlockStateData neutralState) {
        List<TrafficLightElement> greenElements = side.getPedestrianGreenElements();
        List<TrafficLightElement> redElements = side.getPedestrianRedElements();

        if (isGreen) {
            // Show pedestrian green elements
            for (TrafficLightElement element : greenElements) {
                applyElementState(element, element, null, neutralState);
            }

            // Show neutral state on red elements
            for (TrafficLightElement element : redElements) {
                applyElementState(element, neutralElement, null, neutralState);
            }
        } else {
            // Show neutral state on green elements
            for (TrafficLightElement element : greenElements) {
                applyElementState(element, neutralElement, null, neutralState);
            }

            // Show pedestrian red elements
            for (TrafficLightElement element : redElements) {
                applyElementState(element, element, null, neutralState);
            }
        }
    }

    /**
     * Applies an element state to the world
     * @param targetPosition The element whose position to use
     * @param stateToApply The element state to apply (or null for neutral)
     * @param direction Cardinal direction for rotation
     * @param neutralState Fallback neutral state for blocks
     */
    private void applyElementState(TrafficLightElement targetPosition, TrafficLightElement stateToApply,
                                    String direction, BlockStateData neutralState) {
        if (targetPosition == null) return;

        ElementPosition pos = targetPosition.getPosition();
        Location location;
        try {
            location = pos.toLocation();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get location for element at " + pos, e);
            return;
        }

        // Check if chunk is loaded
        if (!location.isChunkLoaded()) {
            return;
        }

        Chunk chunk = location.getChunk();
        if (!chunk.isEntitiesLoaded()) {
            return;
        }

        World world = location.getWorld();

        // Handle ItemFrameElements specially
        if (targetPosition instanceof ItemFrameElement) {
            ItemFrameElement frameElement = (ItemFrameElement) targetPosition;
            ItemFrame itemFrame = frameElement.findOrSpawnItemFrame(world);

            if (itemFrame == null) {
                LOGGER.warning("Could not find or spawn item frame at " + frameElement.getPosition());
                return;
            }

            if (stateToApply instanceof ItemFrameElement) {
                // Apply another item frame's state to this frame
                ItemFrameElement stateSource = (ItemFrameElement) stateToApply;
                if (stateSource.getFrameState() != null) {
                    stateSource.getFrameState().applyToItemFrame(itemFrame, false);
                    debug("Applied item frame state from " + stateSource.getPosition() + " to " + frameElement.getPosition());
                }
            } else {
                // No item frame neutral state - set to empty
                // Temporarily set fixed=true to prevent item drops
                boolean wasFixed = itemFrame.isFixed();
                itemFrame.setFixed(true);
                itemFrame.setItem(null, false);
                itemFrame.setFixed(wasFixed);
                debug("Set item frame to empty at " + frameElement.getPosition());
            }
            return;
        }

        // Handle BlockElements
        if (stateToApply != null) {
            // Apply the target state
            if (direction != null) {
                stateToApply.apply(world, direction);
            } else {
                stateToApply.applyWithFacingFrom(world, targetPosition);
            }
        } else if (targetPosition instanceof BlockElement) {
            // Fallback to legacy neutral state for blocks
            if (neutralState != null) {
                Block block = location.getBlock();
                neutralState.applyToBlockWithFacingFrom(block, ((BlockElement) targetPosition).getBlockStateData());
            }
        }
    }

    /**
     * Applies a block state copying the facing from another block state
     * Used for neutral blocks that need to match the orientation of what they replace
     */
    private void applyBlockStateWithFacing(BlockPosition position, BlockStateData state, BlockStateData facingSource) {
        if (state == null) {
            LOGGER.warning("Cannot apply null block state at " + position);
            return;
        }
        try {
            Location location = position.toLocation();

            // Check if chunk is loaded with active entities
            if (!location.isChunkLoaded()) {
                return; // Chunk not loaded, skip
            }

            Chunk chunk = location.getChunk();
            if (!chunk.isEntitiesLoaded()) {
                return; // Entities not loaded, skip
            }

            Block block = location.getBlock();
            state.applyToBlockWithFacingFrom(block, facingSource);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply block state at " + position, e);
        }
    }

    /**
     * Applies a block state to a block in the world
     * This is where the actual block replacement happens
     * @param position Position of the block
     * @param state Block state to apply
     * @param direction Cardinal direction (North/South/East/West) for automatic head rotation
     */
    private void applyBlockState(BlockPosition position, BlockStateData state, String direction) {
        if (state == null) {
            LOGGER.warning("Cannot apply null block state at " + position);
            return;
        }
        try {
            Location location = position.toLocation();

            // Check if chunk is loaded with active entities
            if (!location.isChunkLoaded()) {
                return; // Chunk not loaded, skip
            }

            Chunk chunk = location.getChunk();
            if (!chunk.isEntitiesLoaded()) {
                return; // Entities not loaded, skip
            }

            Block block = location.getBlock();
            state.applyToBlock(block, direction);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply block state at " + position, e);
        }
    }

    /**
     * Gets the current cycle for an intersection
     */
    public TrafficCycle getCycle(Intersection intersection) {
        return cycles.get(intersection);
    }

    /**
     * Checks if the animator is running
     */
    public boolean isRunning() {
        return animationTask != null;
    }

    /**
     * Gets the number of active cycles
     */
    public int getActiveCycleCount() {
        return cycles.size();
    }

    /**
     * Reloads the tick interval from config
     */
    public void reloadConfig() {
        this.tickInterval = plugin.getConfig().getInt("animation.tick-interval", 10);

        if (animationTask != null) {
            // Restart with new interval
            stop();
            start();
        }
    }
}
