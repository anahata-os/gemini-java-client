/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a FunctionCall that has been successfully executed.
 * It bundles all necessary information about a single, completed tool execution.
 *
 * @author anahata
 */
@Getter
@AllArgsConstructor
public class ExecutedToolCall {
    /** The original Part from the model's message that contained the FunctionCall. */
    public final Part sourceCallPart;
    /** The FunctionResponse object that will be sent back to the model. */
    public final FunctionResponse response;
    /** The raw Java Object returned by the tool method before JSON conversion. */
    public final Object rawResult;
}