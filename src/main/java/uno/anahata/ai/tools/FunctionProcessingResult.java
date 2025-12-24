/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The result of a function call prompt containing the prompt for several functions.
 * It summarizes the outcome of the entire user interaction and execution phase for a given turn.
 *
 * @author pablo-ai
 */
@Getter
@AllArgsConstructor
public class FunctionProcessingResult {
    /** A list of all the successfully executed tool calls for the turn. */
    public final List<ExecutedToolCall> executedCalls;
    /** A definitive list of outcomes for every tool call proposed by the model in this turn. */
    public final List<ToolCallOutcome> outcomes;
    /** Any text the user entered in the comment box of the confirmation dialog. */
    public final String userComment;
}