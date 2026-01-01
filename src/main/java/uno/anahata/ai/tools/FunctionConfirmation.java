/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * Represents the user's confirmation status for a function call.
 * @author anahata
 */
public enum FunctionConfirmation {
    /** The user approved this specific execution. */
    YES,
    /** The user denied this specific execution. */
    NO,
    /** The user wants to always approve this function in the future. */
    ALWAYS,
    /** The user wants to never approve this function in the future. */
    NEVER;
}