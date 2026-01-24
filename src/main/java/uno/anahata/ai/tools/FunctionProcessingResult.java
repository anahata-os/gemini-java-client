/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Summarizes the outcome of the entire user interaction and execution phase
 * for a given turn where tool calls were proposed.
 * <p>
 * It contains the list of successfully executed calls, the definitive outcomes
 * for all proposed calls, and any feedback provided by the user.
 * </p>
 *
 * @author anahata
 */
@Getter
@AllArgsConstructor
public class FunctionProcessingResult {
    /**
     * A list of all tool calls that were approved and successfully executed
     * during this turn.
     */
    public final List<ExecutedToolCall> executedCalls;
    
    /**
     * A definitive list of outcomes for every tool call proposed by the model
     * in this turn, including those that were denied or cancelled.
     */
    public final List<ToolCallOutcome> outcomes;
    
    /**
     * Any text the user entered in the comment box of the confirmation dialog.
     */
    public final String userComment;

    /**
     * Indicates whether the tool confirmation dialog was actually displayed to the user.
     */
    public final boolean dialogShown;

    /**
     * The complete, system-generated user feedback message summarizing the 
     * outcomes of all proposed tool calls and any user comments.
     */
    public final String feedbackMessage;
}
