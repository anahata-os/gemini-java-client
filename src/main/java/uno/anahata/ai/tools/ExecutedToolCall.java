/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a tool call that has been successfully executed.
 * <p>
 * This class bundles all necessary information about a completed tool
 * execution, including the original call part, the generated response,
 * and the raw Java result.
 * </p>
 *
 * @author anahata
 */
@Getter
@AllArgsConstructor
public class ExecutedToolCall {
    /**
     * The original {@link Part} from the model's message that contained the
     * {@code FunctionCall}.
     */
    public final Part sourceCallPart;
    
    /**
     * The {@link FunctionResponse} object that will be sent back to the model.
     */
    public final FunctionResponse response;
    
    /**
     * The raw Java {@code Object} returned by the tool method before it was
     * converted to a JSON-compatible Map.
     */
    public final Object rawResult;
}