/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionCall;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.Chat;

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
     * @param chat The current Chat instance, providing access to context and configuration.
     * @return A result object containing the lists of approved and denied
     * functions, and any user comment.
     */
    PromptResult prompt(ChatMessage modelMessage, Chat chat);

    /**
     * A simple value object to hold the results from the prompt.
     */
    class PromptResult {

        public final Map<FunctionCall, FunctionConfirmation> functionConfirmations;
        public final String userComment;
        public final boolean cancelled;

        public PromptResult(Map<FunctionCall, FunctionConfirmation> functionConfirmations, String userComment, boolean cancelled) {
            this.functionConfirmations = Collections.unmodifiableMap(functionConfirmations);
            this.userComment = userComment;
            this.cancelled = cancelled;
        }
    }
}