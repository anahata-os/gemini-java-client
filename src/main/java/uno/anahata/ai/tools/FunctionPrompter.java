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
 * function calls. 
 * <p>
 * This decouples the core function-calling logic from any specific UI 
 * implementation (e.g., Swing, command-line), allowing the framework to be 
 * embedded in different environments.
 * </p>
 *
 * @author anahata
 */
public interface FunctionPrompter {

    /**
     * Prompts the user to confirm a list of function calls from the model's
     * response.
     * <p>
     * This method is responsible for displaying the proposed calls and their
     * arguments to the user and collecting their approval or denial for each.
     * </p>
     *
     * @param modelMessage The full {@link ChatMessage} from the model, containing
     *                     one or more function calls.
     * @param chat         The current {@link Chat} instance, providing access to
     *                     context and configuration.
     * @return A {@link PromptResult} object containing the user's choices and comments.
     */
    PromptResult prompt(ChatMessage modelMessage, Chat chat);

    /**
     * A value object holding the results of a user confirmation prompt.
     */
    class PromptResult {

        /**
         * A map linking each proposed {@link FunctionCall} to the user's
         * {@link FunctionConfirmation}.
         */
        public final Map<FunctionCall, FunctionConfirmation> functionConfirmations;
        
        /**
         * Any text the user entered in the comment box of the dialog.
         */
        public final String userComment;
        
        /**
         * Flag indicating if the user cancelled the entire dialog.
         */
        public final boolean cancelled;

        /**
         * Constructs a new PromptResult.
         *
         * @param functionConfirmations The map of confirmations.
         * @param userComment           The user's comment.
         * @param cancelled             Whether the prompt was cancelled.
         */
        public PromptResult(Map<FunctionCall, FunctionConfirmation> functionConfirmations, String userComment, boolean cancelled) {
            this.functionConfirmations = Collections.unmodifiableMap(functionConfirmations);
            this.userComment = userComment;
            this.cancelled = cancelled;
        }
    }
}