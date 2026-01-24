/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * Represents the definitive final status of a tool call after user interaction
 * and execution attempts.
 * <p>
 * This provides a more granular and explicit status than the simple
 * {@link FunctionConfirmation}, capturing states like cancellation or
 * feature disablement.
 * </p>
 *
 * @author anahata
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
    
    /** The user cancelled the entire confirmation dialog, so no calls in the batch were executed. */
    CANCELLED,
    
    /** The model attempted to call a tool while function calling was disabled by the user. */
    DISABLED,

    /** The tool was approved for execution, but the execution failed with an exception. */
    ERROR,

    /** The tool execution was explicitly killed/interrupted by the user. */
    KILLED;
}
