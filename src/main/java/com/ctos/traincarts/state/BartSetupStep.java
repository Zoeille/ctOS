package com.ctos.traincarts.state;

/**
 * Enumeration of all setup steps in the BART station configuration flow
 */
public enum BartSetupStep {
    // Initial state
    NONE("Not in setup", false),

    // Sign selection
    SELECT_SIGN("Right-click on the sf-bart-station sign", false),

    // Redstone position selection
    SELECT_REDSTONE("Right-click on the block where the redstone should be placed", false),

    // Delay configuration
    SET_DELAY("Enter the delay in seconds (how long the redstone stays active, 1-300)", true),

    // Confirmation
    CONFIRM("Type 'confirm' to save, or 'cancel' to discard", true),

    // Complete
    COMPLETE("Setup complete!", false);

    private final String prompt;
    private final boolean requiresTextInput;

    BartSetupStep(String prompt, boolean requiresTextInput) {
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
    public BartSetupStep getNext() {
        switch (this) {
            case NONE:
                return SELECT_SIGN;
            case SELECT_SIGN:
                return SELECT_REDSTONE;
            case SELECT_REDSTONE:
                return SET_DELAY;
            case SET_DELAY:
                return CONFIRM;
            case CONFIRM:
                return COMPLETE;
            default:
                return NONE;
        }
    }
}
