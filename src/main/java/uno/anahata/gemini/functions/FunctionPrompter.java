package uno.anahata.gemini.functions;

import com.google.genai.types.FunctionCall;
import java.util.List;
import java.util.Set;
import uno.anahata.gemini.ChatMessage;

/**
 * An interface for UI components that can prompt the user to confirm a batch of
 * function calls. This decouples the core function-calling logic from any
 * specific UI implementation (e.g., Swing, command-line).
 *
 * @author pablo-ai
 */
public interface FunctionPrompter {    

    /**
     * Prompts the user to confirm a list of function calls from the model's
     * response.
     *
     * @param modelMessage The full ChatMessage from the model, containing one or
     * more function calls.
     * @return A result object containing the lists of approved and denied
     * functions, and any user comment.
     */
    PromptResult prompt(ChatMessage modelMessage, int contentIdx, Set<String> alwaysApprove, Set<String> alwaysDeny);

    /**
     * A simple value object to hold the results from the prompt.
     */
    class PromptResult {

        public final List<FunctionCall> approvedFunctions;
        public final List<FunctionCall> deniedFunctions;
        public final String userComment;

        public PromptResult(List<FunctionCall> approvedFunctions, List<FunctionCall> deniedFunctions, String userComment) {
            this.approvedFunctions = approvedFunctions;
            this.deniedFunctions = deniedFunctions;
            this.userComment = userComment;
        }
    }
}
