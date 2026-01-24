package com.ctos.trafficlight.model;

import java.util.*;

/**
 * Represents a complete traffic light intersection with all its sides
 */
public class Intersection {
    private UUID id;
    private String name;
    private List<TrafficLightSide> sides;
    private TimingConfiguration timing;
    private BlockStateData neutralState; // The "off" state block (legacy, for blocks)
    private TrafficLightElement neutralElement; // The "off" state element (for both blocks and item frames)
    private int currentPhaseIndex;
    private long lastPhaseChangeTime;

    public Intersection(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.sides = new ArrayList<>();
        this.timing = TimingConfiguration.getDefault();
        this.currentPhaseIndex = 0;
        this.lastPhaseChangeTime = System.currentTimeMillis();
    }

    /**
     * Adds a side to this intersection
     */
    public void addSide(TrafficLightSide side) {
        sides.add(side);
    }

    /**
     * Removes a side from this intersection
     */
    public void removeSide(TrafficLightSide side) {
        sides.remove(side);
    }

    /**
     * Checks if this intersection is fully configured and ready to operate
     */
    public boolean isComplete() {
        // Must have at least 2 sides
        if (sides.size() < 2) {
            return false;
        }

        // All sides must be complete (either block-based or element-based)
        for (TrafficLightSide side : sides) {
            if (!side.isComplete() && !side.isElementsComplete()) {
                return false;
            }
        }

        // Must have neutral state or neutral element defined
        if (neutralState == null && neutralElement == null) {
            return false;
        }

        return true;
    }

    /**
     * Gets all block positions managed by this intersection
     */
    public Set<BlockPosition> getAllBlocks() {
        Set<BlockPosition> allBlocks = new HashSet<>();

        for (TrafficLightSide side : sides) {
            allBlocks.addAll(side.getAllBlocks());
        }

        return allBlocks;
    }

    /**
     * Advances to the next phase
     */
    public void advancePhase() {
        // Calculate total number of phases
        // For a typical 4-way intersection: 6 phases
        // (NS_GREEN, NS_ORANGE, ALL_RED, EW_GREEN, EW_ORANGE, ALL_RED)
        int totalPhases = calculateTotalPhases();

        currentPhaseIndex = (currentPhaseIndex + 1) % totalPhases;
        lastPhaseChangeTime = System.currentTimeMillis();
    }

    /**
     * Calculates the total number of phases in the cycle
     */
    private int calculateTotalPhases() {
        // Number of phase groups (e.g., North-South, East-West)
        int groups = (int) Math.ceil(sides.size() / 2.0);

        // Each group has 3 phases: green, orange, all-red transition
        return groups * 3;
    }

    /**
     * Gets the sides that should be green in the current phase
     */
    public List<TrafficLightSide> getActiveSides() {
        List<TrafficLightSide> activeSides = new ArrayList<>();

        // Determine which group is active based on current phase
        int phasesPerGroup = 3; // green, orange, all-red
        int currentGroup = currentPhaseIndex / phasesPerGroup;

        // Group sides (e.g., North-South, East-West)
        if (sides.size() >= 2) {
            int sidesPerGroup = sides.size() / 2;
            int startIndex = currentGroup * sidesPerGroup;
            int endIndex = Math.min(startIndex + sidesPerGroup, sides.size());

            for (int i = startIndex; i < endIndex; i++) {
                activeSides.add(sides.get(i));
            }
        }

        return activeSides;
    }

    /**
     * Gets the current light phase (RED, ORANGE, or GREEN) for the active sides
     */
    public LightPhase getCurrentLightPhase() {
        int phaseInGroup = currentPhaseIndex % 3;

        switch (phaseInGroup) {
            case 0:
                return LightPhase.GREEN;
            case 1:
                return LightPhase.ORANGE;
            case 2:
            default:
                return LightPhase.RED; // All-red transition
        }
    }

    /**
     * Checks if enough time has passed to advance to the next phase
     */
    public boolean shouldAdvancePhase() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastChange = currentTime - lastPhaseChangeTime;
        long requiredDuration = getRequiredDurationForCurrentPhase();

        return timeSinceLastChange >= requiredDuration;
    }

    /**
     * Gets the required duration for the current phase in milliseconds
     */
    private long getRequiredDurationForCurrentPhase() {
        int phaseInGroup = currentPhaseIndex % 3;
        int ticks;

        switch (phaseInGroup) {
            case 0: // Green
                ticks = timing.getGreenDurationTicks();
                break;
            case 1: // Orange
                ticks = timing.getOrangeDurationTicks();
                break;
            case 2: // All-red
                ticks = timing.getAllRedGapTicks();
                break;
            default:
                ticks = 20;
        }

        // Convert ticks to milliseconds (1 tick = 50ms)
        return ticks * 50;
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TrafficLightSide> getSides() {
        return new ArrayList<>(sides);
    }

    public void setSides(List<TrafficLightSide> sides) {
        this.sides = new ArrayList<>(sides);
    }

    public TimingConfiguration getTiming() {
        return timing;
    }

    public void setTiming(TimingConfiguration timing) {
        this.timing = timing;
    }

    public BlockStateData getNeutralState() {
        return neutralState;
    }

    public void setNeutralState(BlockStateData neutralState) {
        this.neutralState = neutralState;
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    public void setCurrentPhaseIndex(int currentPhaseIndex) {
        this.currentPhaseIndex = currentPhaseIndex;
    }

    public long getLastPhaseChangeTime() {
        return lastPhaseChangeTime;
    }

    public void setLastPhaseChangeTime(long lastPhaseChangeTime) {
        this.lastPhaseChangeTime = lastPhaseChangeTime;
    }

    public TrafficLightElement getNeutralElement() {
        return neutralElement;
    }

    public void setNeutralElement(TrafficLightElement neutralElement) {
        this.neutralElement = neutralElement;
    }

    /**
     * Gets all element positions managed by this intersection
     */
    public Set<ElementPosition> getAllElementPositions() {
        Set<ElementPosition> allPositions = new HashSet<>();

        for (TrafficLightSide side : sides) {
            allPositions.addAll(side.getAllElementPositions());
        }

        return allPositions;
    }

    /**
     * Checks if this intersection has any element-based configuration
     */
    public boolean hasElements() {
        for (TrafficLightSide side : sides) {
            if (side.hasElements()) {
                return true;
            }
        }
        return neutralElement != null;
    }
}
