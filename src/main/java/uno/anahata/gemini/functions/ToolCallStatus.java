package uno.anahata.gemini.functions;

/**
 * Represents the definitive final status of a tool call after user interaction and execution.
 * This provides a more granular and explicit status than the simple {@link FunctionConfirmation}.
 *
 * @author pablo-ai
 */
public enum ToolCallStatus {
    /** The user's preference for this tool is 'Always', and it was executed. */
    ALWAYS,
    /** The user explicitly approved this tool call for this turn, and it was executed. */
    YES,
    /** The user explicitly denied this tool call for this turn. */
    NO,
    /** The user's preference for this tool is 'Never', and it was not executed. */
    NEVER,
    /** The user cancelled the entire confirmation dialog, so no calls were executed. */
    CANCELLED,
    /** The model attempted to call a tool while the feature was disabled by the user. */
    DISABLED;
}
