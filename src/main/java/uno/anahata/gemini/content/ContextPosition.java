package uno.anahata.gemini.content;

/**
 * Defines the possible locations where a {@link ContextProvider} can inject its content.
 */
public enum ContextPosition {
    /**
     * The context is injected as a permanent system instruction at the beginning of the chat.
     * This context should be concise and is typically text-only.
     */
    SYSTEM_INSTRUCTIONS,

    /**
     * The context is injected as a temporary, just-in-time user message at the end of the prompt.
     * This makes the information highly salient for the model's next turn and is not saved
     * in the permanent chat history. This position supports rich content like images and JSON.
     */
    AUGMENTED_WORKSPACE;
}
