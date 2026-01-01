/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.status;

import lombok.Builder;
import lombok.Value;
import uno.anahata.ai.Chat;

/**
 * An event object that provides rich, contextual information about a change in the Chat's status.
 * <p>
 * This is used to notify {@link StatusListener}s of state transitions in a structured way,
 * including the duration of the previous state and any associated exceptions or tool names.
 * </p>
 */
@Value
@Builder
public class ChatStatusEvent {
    /** The Chat instance that is the source of the event. */
    Chat source;
    
    /** The status before the change. */
    ChatStatus oldStatus;
    
    /** The new, current status. */
    ChatStatus newStatus;
    
    /** The duration, in milliseconds, that the old status was active. */
    long durationMillis;
    
    /** The exception that triggered the status change, if any. This is typically present for error states. */
    Exception exception;
    
    /** The name of the tool currently being executed, if the status is TOOL_EXECUTION_IN_PROGRESS. */
    String executingToolName;
}