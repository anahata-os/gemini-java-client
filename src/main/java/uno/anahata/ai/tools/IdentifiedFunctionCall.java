/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a FunctionCall that has been assigned a short, unique, turn-specific ID.
 * This provides a stable reference for tracking a call through the confirmation and execution lifecycle.
 *
 * @author anahata
 */
@Getter
@AllArgsConstructor
public class IdentifiedFunctionCall {
    /** The original FunctionCall object from the model. */
    private final FunctionCall call;
    /** The short, sequential ID assigned to this call for this turn. */
    private final String id;
    /** The message Part that this call originated from. */
    private final Part sourcePart;
}