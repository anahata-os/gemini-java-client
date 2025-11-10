package uno.anahata.gemini.status;

import java.awt.Color;

/**
 * Defines the possible operational states of the GeminiChat, primarily for UI feedback.
 * The colors are suggestions for a "traffic light" status indicator.
 */
public enum ChatStatus {
    /** A normal API call is in progress (e.g., waiting for a model response). */
    API_CALL_IN_PROGRESS(new Color(0, 123, 255)), // BLUE
    
    /** Local tool (function) execution is in progress. */
    TOOL_EXECUTION_IN_PROGRESS(new Color(128, 0, 128)), // PURPLE
    
    /** An API error occurred, and the system is in retry mode. */
    API_ERROR_RETRYING(new Color(255, 0, 0)), // RED
    
    /** The model has finished processing and is waiting for the user's next input. */
    IDLE_WAITING_FOR_USER(new Color(0, 128, 0)); // GREEN

    private final Color color;

    ChatStatus(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}
