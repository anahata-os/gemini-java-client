/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.status;

import lombok.Getter;

/**
 * Defines the possible operational states of a {@link uno.anahata.ai.Chat} session.
 * <p>
 * These states are primarily used to provide real-time feedback to the user
 * interface, indicating what the assistant is currently doing.
 * </p>
 */
@Getter
public enum ChatStatus {
    /** A normal API call is in progress (e.g., waiting for a model response). */
    API_CALL_IN_PROGRESS("API Call in Progress...", "Waiting for a response from the model."), 
    
    /** Local tool (function) execution is in progress. */
    TOOL_EXECUTION_IN_PROGRESS("Tool Execution...", "Executing local Java tools (functions)."), 
    
    /** An API error occurred, and the system is in retry mode with exponential backoff. */
    WAITING_WITH_BACKOFF("Waiting with Backoff...", "An API error occurred. Retrying with exponential backoff."), 
    
    /** The assistant has hit the maximum number of retries and has stopped. */
    MAX_RETRIES_REACHED("Max Retries Reached", "The assistant has stopped after hitting the maximum number of retries."),
    
    /** The model has finished processing and is waiting for the user's next input. */
    IDLE_WAITING_FOR_USER("Idle", "Waiting for user input.");

    private final String displayName;
    private final String description;

    ChatStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Checks if the current status represents a state that can be interrupted by the user.
     * @return true if interruptible, false otherwise.
     */
    public boolean isInterruptible() {
        return this == API_CALL_IN_PROGRESS || this == WAITING_WITH_BACKOFF || this == TOOL_EXECUTION_IN_PROGRESS;
    }

    @Override
    public String toString() {
        return displayName;
    }
}