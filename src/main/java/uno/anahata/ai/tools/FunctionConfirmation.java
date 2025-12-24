/* Licensed under the Apache License, Version 2.0 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.ai.tools;

/**
 * Represents the user's confirmation status for a function call.
 * @author pablo
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
