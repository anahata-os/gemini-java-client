package uno.anahata.gemini;

/**
 * Listener interface for receiving real-time status updates from the GeminiChat.
 * This is primarily used by the UI to display a "traffic light" status indicator.
 */
public interface StatusListener {

    /**
     * Called when the chat's operational status changes.
     *
     * @param status The new operational state (e.g., API_CALL_IN_PROGRESS, IDLE_WAITING_FOR_USER).
     * @param lastExceptionToString The toString() of the last exception that occurred, or an empty string if none.
     */
    void statusChanged(ChatStatus status, String lastExceptionToString);
}
