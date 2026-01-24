package com.ctos.trafficlight.state;

import com.ctos.trafficlight.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Tracks the state of a player's intersection setup session
 */
public class SetupSession {
    private final UUID playerId;
    private SetupStep currentStep;
    private Intersection intersectionInProgress;
    private TrafficLightSide currentSideInProgress;
    private LightPhase currentPhaseBeingConfigured;
    private List<BlockPosition> selectedBlocksBuffer;
    private List<BlockStateData> selectedStatesBuffer;
    private List<TrafficLightElement> selectedElementsBuffer; // New: for element-based selection
    private long lastInteractionTime;

    public SetupSession(UUID playerId) {
        this.playerId = playerId;
        this.currentStep = SetupStep.NONE;
        this.selectedBlocksBuffer = new ArrayList<>();
        this.selectedStatesBuffer = new ArrayList<>();
        this.selectedElementsBuffer = new ArrayList<>();
        this.lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Starts the setup process for a new intersection
     */
    public void start() {
        this.intersectionInProgress = new Intersection(UUID.randomUUID(), "");
        advanceToNextStep();
    }

    /**
     * Starts editing an existing intersection
     */
    public void startEdit(Intersection existing) {
        this.intersectionInProgress = existing;
        this.currentStep = SetupStep.EDIT_MENU;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Checks if this session is in edit mode
     */
    public boolean isEditMode() {
        return currentStep == SetupStep.EDIT_MENU ||
               (intersectionInProgress != null && intersectionInProgress.getSides().size() > 0 && currentStep == SetupStep.CONFIRM_SIDE);
    }

    /**
     * Removes a side from the intersection being edited
     */
    public boolean removeSide(int sideIndex) {
        if (intersectionInProgress == null) {
            return false;
        }
        List<TrafficLightSide> sides = intersectionInProgress.getSides();
        if (sideIndex < 0 || sideIndex >= sides.size()) {
            return false;
        }
        sides.remove(sideIndex);
        intersectionInProgress.setSides(sides);
        return true;
    }

    /**
     * Advances to the next step in the setup flow
     */
    public void advanceToNextStep() {
        currentStep = currentStep.getNext();
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Adds a block to the current selection buffer
     */
    public void addToBuffer(BlockPosition position, BlockStateData state) {
        if (!selectedBlocksBuffer.contains(position)) {
            selectedBlocksBuffer.add(position);
            selectedStatesBuffer.add(state);
            lastInteractionTime = System.currentTimeMillis();
        }
    }

    /**
     * Adds an element (block or item frame) to the current selection buffer
     */
    public void addElementToBuffer(TrafficLightElement element) {
        // Check if position already exists in buffer
        ElementPosition pos = element.getPosition();
        for (TrafficLightElement existing : selectedElementsBuffer) {
            if (existing.getPosition().equals(pos)) {
                return; // Already in buffer
            }
        }
        selectedElementsBuffer.add(element);
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Confirms the current selection and stores it in the appropriate place
     */
    public void confirmCurrentSelection() {
        if (currentSideInProgress == null) {
            return;
        }

        // Handle element-based selection
        if (!selectedElementsBuffer.isEmpty()) {
            switch (currentStep) {
                case SELECT_RED_BLOCKS:
                    currentSideInProgress.setLightElements(LightPhase.RED, selectedElementsBuffer);
                    break;
                case SELECT_ORANGE_BLOCKS:
                    currentSideInProgress.setLightElements(LightPhase.ORANGE, selectedElementsBuffer);
                    break;
                case SELECT_GREEN_BLOCKS:
                    currentSideInProgress.setLightElements(LightPhase.GREEN, selectedElementsBuffer);
                    break;
                case SELECT_PEDESTRIAN_GREEN:
                    currentSideInProgress.setPedestrianGreenElements(selectedElementsBuffer);
                    break;
                case SELECT_PEDESTRIAN_RED:
                    currentSideInProgress.setPedestrianRedElements(selectedElementsBuffer);
                    break;
            }
        }

        // Handle legacy block-based selection
        if (!selectedBlocksBuffer.isEmpty()) {
            switch (currentStep) {
                case SELECT_RED_BLOCKS:
                    currentSideInProgress.setLightBlocks(LightPhase.RED, selectedBlocksBuffer, selectedStatesBuffer);
                    break;
                case SELECT_ORANGE_BLOCKS:
                    currentSideInProgress.setLightBlocks(LightPhase.ORANGE, selectedBlocksBuffer, selectedStatesBuffer);
                    break;
                case SELECT_GREEN_BLOCKS:
                    currentSideInProgress.setLightBlocks(LightPhase.GREEN, selectedBlocksBuffer, selectedStatesBuffer);
                    break;
                case SELECT_PEDESTRIAN_GREEN:
                    currentSideInProgress.setPedestrianGreenBlocks(selectedBlocksBuffer, selectedStatesBuffer);
                    break;
                case SELECT_PEDESTRIAN_RED:
                    currentSideInProgress.setPedestrianRedBlocks(selectedBlocksBuffer, selectedStatesBuffer);
                    break;
            }
        }

        clearBuffer();
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Clears the selection buffer
     */
    public void clearBuffer() {
        selectedBlocksBuffer.clear();
        selectedStatesBuffer.clear();
        selectedElementsBuffer.clear();
    }

    /**
     * Completes the current side and adds it to the intersection
     */
    public void completeSide() {
        if (currentSideInProgress != null &&
                (currentSideInProgress.isComplete() || currentSideInProgress.isElementsComplete())) {
            intersectionInProgress.addSide(currentSideInProgress);
            currentSideInProgress = null;
        }
    }

    /**
     * Starts configuring a new side with auto-detected direction
     */
    public void startNewSide(String direction) {
        currentSideInProgress = new TrafficLightSide(direction);
        currentStep = SetupStep.START_SIDE;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Detects direction based on player's yaw (where they're looking)
     */
    public static String detectDirection(float yaw) {
        // Normalize yaw to 0-360 range
        yaw = (yaw % 360 + 360) % 360;

        // Determine direction based on yaw
        if (yaw >= 315 || yaw < 45) {
            return "South";
        } else if (yaw >= 45 && yaw < 135) {
            return "West";
        } else if (yaw >= 135 && yaw < 225) {
            return "North";
        } else {
            return "East";
        }
    }

    /**
     * Sends a message to the player
     */
    public void sendMessage(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message).color(color));
    }

    /**
     * Sends the prompt for the current step to the player
     */
    public void sendPrompt(Player player) {
        String prompt = currentStep.getPrompt();
        sendMessage(player, "[ctOS] " + prompt, NamedTextColor.YELLOW);
    }

    /**
     * Checks if this session has expired (10 minutes of inactivity)
     */
    public boolean isExpired() {
        long currentTime = System.currentTimeMillis();
        long inactiveTime = currentTime - lastInteractionTime;
        long timeoutMillis = 10 * 60 * 1000; // 10 minutes

        return inactiveTime > timeoutMillis;
    }

    /**
     * Updates the last interaction time
     */
    public void touch() {
        this.lastInteractionTime = System.currentTimeMillis();
    }

    // Getters and setters

    public UUID getPlayerId() {
        return playerId;
    }

    public SetupStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(SetupStep currentStep) {
        this.currentStep = currentStep;
    }

    public Intersection getIntersectionInProgress() {
        return intersectionInProgress;
    }

    public TrafficLightSide getCurrentSideInProgress() {
        return currentSideInProgress;
    }

    public void setCurrentSideInProgress(TrafficLightSide currentSideInProgress) {
        this.currentSideInProgress = currentSideInProgress;
    }

    public List<BlockPosition> getSelectedBlocksBuffer() {
        return new ArrayList<>(selectedBlocksBuffer);
    }

    public int getBufferSize() {
        return selectedBlocksBuffer.size();
    }

    /**
     * Gets the number of elements in the selection buffer
     */
    public int getElementBufferSize() {
        return selectedElementsBuffer.size();
    }

    /**
     * Gets the total selection count (blocks + elements)
     */
    public int getTotalBufferSize() {
        return selectedBlocksBuffer.size() + selectedElementsBuffer.size();
    }

    /**
     * Gets the selected elements buffer
     */
    public List<TrafficLightElement> getSelectedElementsBuffer() {
        return new ArrayList<>(selectedElementsBuffer);
    }

    public int getSideCount() {
        return intersectionInProgress != null ? intersectionInProgress.getSides().size() : 0;
    }
}
