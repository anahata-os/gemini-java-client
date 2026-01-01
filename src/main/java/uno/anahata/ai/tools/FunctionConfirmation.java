/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * Represents the user's choice when prompted to confirm the execution of one
 * or more tool calls.
 * 
 * @author anahata
 */
public enum FunctionConfirmation {
    /** The user approved this specific execution for the current turn. */
    YES,
    
    /** The user denied this specific execution for the current turn. */
    NO,
    
    /** The user wants to always approve this tool in the future without prompting. */
    ALWAYS,
    
    /** The user wants to never allow this tool to be executed. */
    NEVER;
}