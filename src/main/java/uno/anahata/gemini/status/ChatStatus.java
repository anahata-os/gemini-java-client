package uno.anahata.gemini.status;

import lombok.Getter;

/**
 * Defines the possible operational states of the GeminiChat, primarily for UI feedback.
 */
@Getter
public enum ChatStatus {
    /** A normal API call is in progress (e.g., waiting for a model response). */
    API_CALL_IN_PROGRESS("API Call in Progress...", "Waiting for a response from the model."), 
    
    /** Local tool (function) execution is in progress. */
    TOOL_EXECUTION_IN_PROGRESS("Tool Execution...", "Executing local Java tools (functions)."), 
    
    /** An API error occurred, and the system is in retry mode with exponential backoff. */
    WAITING_WITH_BACKOFF("Waiting with Backoff...", "An API error occurred. Retrying with exponential backoff."), 
    
    /** The model has finished processing and is waiting for the user's next input. */
    IDLE_WAITING_FOR_USER("Idle", "Waiting for user input.");

    private final String displayName;
    private final String description;

    ChatStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
