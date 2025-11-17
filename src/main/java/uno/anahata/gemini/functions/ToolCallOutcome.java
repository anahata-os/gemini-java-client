package uno.anahata.gemini.functions;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data class that represents the final outcome of a single proposed tool call.
 * It links a uniquely identified function call to its definitive status for the turn.
 *
 * @author pablo-ai
 */
@Getter
@AllArgsConstructor
public class ToolCallOutcome {
    /** The function call, wrapped with its unique, turn-specific ID. */
    private final IdentifiedFunctionCall identifiedCall;
    /** The final status of the call after user interaction. */
    private final ToolCallStatus status;
}
