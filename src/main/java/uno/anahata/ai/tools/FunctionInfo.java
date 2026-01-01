/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionDeclaration;
import java.lang.reflect.Method;
import lombok.AllArgsConstructor;
import uno.anahata.ai.internal.GsonUtils;

/**
 * A metadata class that links a Gemini {@link FunctionDeclaration} to its
 * underlying Java {@link Method} implementation.
 * <p>
 * This is used internally by the {@link ToolManager} to manage the registry
 * of available tools and to perform the actual method invocation.
 * </p>
 *
 * @author anahata
 */
@AllArgsConstructor
public class FunctionInfo {
    /**
     * The Gemini API function declaration, including name, description, and schema.
     */
    public final FunctionDeclaration declaration;
    
    /**
     * The Java method that implements the tool's logic.
     */
    public final Method method;
    
    /**
     * Calculates the approximate size of the function declaration in characters
     * when serialized to JSON.
     *
     * @return The character length of the JSON representation.
     */
    public long getSize() {
        // The declaration.toJson() method returns a Map object. 
        // We must serialize it to a JSON string to get its actual character length.
        return declaration.toJson().length();
    }
}