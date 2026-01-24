package com.ctos.trafficlight.state;

/**
 * Enumeration of all setup steps in the wand configuration flow
 */
public enum SetupStep {
    // Initial state
    NONE("Not in setup", false),

    // Edit mode menu
    EDIT_MENU("Edit mode: 'add', 'edit <n>', 'remove <n>', 'neutral', 'timing', 'done'", true),

    // Intersection naming
    NAME_INTERSECTION("Type the name of your intersection in chat", true),

    // Neutral block selection
    SELECT_NEUTRAL_BLOCK("Right-click a block or item frame to set as the neutral (OFF) state", false),

    // Side configuration
    START_SIDE("Starting configuration for a new side", false),
    CONFIRM_DIRECTION("Type 'ok' to confirm direction, or type 'north', 'south', 'east', 'west' to change", true),
    SELECT_RED_BLOCKS("Right-click blocks/item frames for RED light, then left-click to confirm", false),
    SELECT_ORANGE_BLOCKS("Right-click blocks/item frames for ORANGE light, then left-click to confirm", false),
    SELECT_GREEN_BLOCKS("Right-click blocks/item frames for GREEN light, then left-click to confirm", false),
    SELECT_PEDESTRIAN_GREEN("Right-click blocks/item frames for PEDESTRIAN GREEN (optional), then left-click to confirm or skip", false),
    SELECT_PEDESTRIAN_RED("Right-click blocks/item frames for PEDESTRIAN RED (optional), then left-click to confirm or skip", false),
    CONFIRM_SIDE("Side configured. Type 'next' to add another side, or 'done' to finish sides", true),

    // Timing configuration
    CONFIGURE_TIMING("Type timing in format: green,orange,pedestrian,gap (in seconds, e.g., '10,3,7,1')", true),

    // Final confirmation
    CONFIRM_COMPLETE("Type 'confirm' to save the intersection, or 'cancel' to discard", true),

    // Complete
    COMPLETE("Setup complete!", false);

    private final String prompt;
    private final boolean requiresTextInput;

    SetupStep(String prompt, boolean requiresTextInput) {
        this.prompt = prompt;
        this.requiresTextInput = requiresTextInput;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean requiresTextInput() {
        return requiresTextInput;
    }

    /**
     * Determines the next step in the flow
     */
    public SetupStep getNext() {
        switch (this) {
            case NONE:
                return NAME_INTERSECTION;
            case NAME_INTERSECTION:
                return SELECT_NEUTRAL_BLOCK;
            case SELECT_NEUTRAL_BLOCK:
                return START_SIDE;
            case START_SIDE:
                return CONFIRM_DIRECTION;
            case CONFIRM_DIRECTION:
                return SELECT_RED_BLOCKS;
            case SELECT_RED_BLOCKS:
                return SELECT_ORANGE_BLOCKS;
            case SELECT_ORANGE_BLOCKS:
                return SELECT_GREEN_BLOCKS;
            case SELECT_GREEN_BLOCKS:
                return SELECT_PEDESTRIAN_GREEN;
            case SELECT_PEDESTRIAN_GREEN:
                return SELECT_PEDESTRIAN_RED;
            case SELECT_PEDESTRIAN_RED:
                return CONFIRM_SIDE;
            case CONFIRM_SIDE:
                // This is handled specially - can go to START_SIDE or CONFIGURE_TIMING
                return CONFIGURE_TIMING;
            case CONFIGURE_TIMING:
                return CONFIRM_COMPLETE;
            case CONFIRM_COMPLETE:
                return COMPLETE;
            default:
                return NONE;
        }
    }
}
