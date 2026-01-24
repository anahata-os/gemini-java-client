/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * A data class that represents the final outcome of a single proposed tool call.
 * <p>
 * It links a uniquely identified function call to its definitive status for
 * the turn, providing a clear record of what was proposed and what actually
 * happened.
 * </p>
 *
 * @author anahata
 */
@Getter
@AllArgsConstructor
public class ToolCallOutcome {
    /**
     * The function call, wrapped with its unique, turn-specific ID.
     */
    private final IdentifiedFunctionCall identifiedCall;
    
    /**
     * The final status of the call after user interaction (e.g., YES, NO, ALWAYS).
     */
    private final ToolCallStatus status;

    /**
     * Optional feedback or comments collected during the tool's execution 
     * (e.g., from a specialized dialog like a diff viewer).
     */
    private final String executionFeedback;

    /**
     * Generates a concise, bracketed string summarizing the outcome of this tool call.
     * @param dialogShown Whether the general tool confirmation dialog was displayed.
     * @return The formatted feedback string.
     */
    public String toFeedbackString(boolean dialogShown) {
        String toolName = identifiedCall.getCall().name().orElse("unknown");
        String id = identifiedCall.getId();
        
        String statusLabel;
        switch(status) {
            case ALWAYS: 
                statusLabel = dialogShown ? "EXECUTED (User-confirmed)" : "EXECUTED (Auto-approved)"; 
                break;
            case YES: 
                statusLabel = "EXECUTED (User-confirmed)"; 
                break;
            case NO: 
                statusLabel = "NOT_EXECUTED (User-denied)"; 
                break;
            case NEVER: 
                statusLabel = "NOT_EXECUTED (Auto-denied)"; 
                break;
            case CANCELLED: 
                statusLabel = "NOT_EXECUTED (Dialog-cancelled)"; 
                break;
            default: 
                statusLabel = "NOT_EXECUTED";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(toolName).append(" id=").append(id).append(" ").append(statusLabel);
        if (StringUtils.isNotBlank(executionFeedback)) {
            sb.append(" User Feedback: **'").append(executionFeedback).append("'**");
        }
        sb.append("]");
        return sb.toString();
    }
}
