/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * An interface for tool results that contain direct user feedback or comments
 * collected during the tool's execution (e.g., from a specialized dialog).
 * <p>
 * The orchestrator (Chat.java) will aggregate feedback from all executed tools
 * that implement this interface and include it in the system-generated user
 * feedback message.
 * </p>
 */
public interface UserFeedback {
    /**
     * Gets the feedback or comment provided by the user during tool execution.
     * @return The user feedback string, or null/empty if none provided.
     */
    String getUserFeedback();
}
