package com.ctos.traincarts.state;

import com.ctos.trafficlight.model.BlockPosition;
import com.ctos.traincarts.model.BartStationConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Tracks the state of a player's BART station setup session
 */
public class BartSetupSession {
    private final UUID playerId;
    private BartSetupStep currentStep;
    private String stationName;
    private BlockPosition redstonePosition;
    private int delaySeconds = 10; // Default 10 seconds
    private long lastInteractionTime;
    private UUID existingConfigId; // For edit mode
    private boolean isEditMode = false;

    public BartSetupSession(UUID playerId) {
        this.playerId = playerId;
        this.currentStep = BartSetupStep.NONE;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Creates a session for editing an existing config
     */
    public static BartSetupSession forEdit(UUID playerId, BartStationConfig config) {
        BartSetupSession session = new BartSetupSession(playerId);
        session.existingConfigId = config.getId();
        session.stationName = config.getStationName();
        session.redstonePosition = config.getRedstonePosition();
        session.delaySeconds = config.getDelayTicks() / 20;
        session.isEditMode = true;
        session.currentStep = BartSetupStep.SET_DELAY; // Jump directly to delay editing
        return session;
    }

    /**
     * Starts the setup process
     */
    public void start() {
        this.currentStep = BartSetupStep.SELECT_SIGN;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Advances to the next step in the setup flow
     */
    public void advanceToNextStep() {
        currentStep = currentStep.getNext();
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Checks if this session has expired (5 minutes of inactivity)
     */
    public boolean isExpired() {
        long currentTime = System.currentTimeMillis();
        long inactiveTime = currentTime - lastInteractionTime;
        long timeoutMillis = 5 * 60 * 1000; // 5 minutes

        return inactiveTime > timeoutMillis;
    }

    /**
     * Updates the last interaction time
     */
    public void touch() {
        this.lastInteractionTime = System.currentTimeMillis();
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
        sendMessage(player, "[ctOS BART] " + prompt, NamedTextColor.YELLOW);
    }

    /**
     * Creates a BartStationConfig from the current session data
     */
    public BartStationConfig toConfig() {
        if (stationName == null || stationName.isEmpty() || redstonePosition == null) {
            throw new IllegalStateException("Session is not complete");
        }
        BartStationConfig config;
        if (isEditMode && existingConfigId != null) {
            // Preserve existing ID when editing
            config = new BartStationConfig(existingConfigId, stationName, redstonePosition);
        } else {
            config = new BartStationConfig(stationName, redstonePosition);
        }
        config.setDelayTicks(delaySeconds * 20); // Convert seconds to ticks
        return config;
    }

    /**
     * Checks if the session has all required data
     */
    public boolean isComplete() {
        return stationName != null && !stationName.isEmpty() && redstonePosition != null && delaySeconds > 0;
    }

    // Getters and setters

    public UUID getPlayerId() {
        return playerId;
    }

    public BartSetupStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(BartSetupStep currentStep) {
        this.currentStep = currentStep;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public BlockPosition getRedstonePosition() {
        return redstonePosition;
    }

    public void setRedstonePosition(BlockPosition redstonePosition) {
        this.redstonePosition = redstonePosition;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = delaySeconds;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public UUID getExistingConfigId() {
        return existingConfigId;
    }
}
