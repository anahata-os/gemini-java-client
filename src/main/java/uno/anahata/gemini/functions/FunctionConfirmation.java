/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini.functions;

/**
 *
 * @author pablo
 */
public enum FunctionConfirmation {
    YES,
    ALWAYS_YES_THIS_FUNCTION,//always run this function
    ALWAYS_YES_THESE_ARGS,//always run this function if it has the same parameters
    NO,//do not execute the function but let the model know we did not want to execute it
    CANCEL;//do not execute the function but dont tell the model we didn't´t
}
