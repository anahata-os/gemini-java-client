/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.status;

/**
 * Listener interface for receiving real-time status updates from a {@link uno.anahata.ai.Chat} session.
 * <p>
 * This is primarily used by the UI to display a status indicator or "traffic light"
 * reflecting the assistant's current activity.
 * </p>
 */
@FunctionalInterface
public interface StatusListener {

    /**
     * Called when the chat's operational status changes.
     *
     * @param status                The new operational state (e.g., API_CALL_IN_PROGRESS).
     * @param lastExceptionToString The string representation of the last exception that occurred, or {@code null} if none.
     */
    void statusChanged(ChatStatus status, String lastExceptionToString);
}