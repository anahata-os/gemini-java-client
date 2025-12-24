/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionDeclaration;
import java.lang.reflect.Method;
import lombok.AllArgsConstructor;
import uno.anahata.ai.internal.GsonUtils;

/**
 * Maps a FunctionDeclaration to its declaring method.
 *
 * @author pablo-ai
 */
@AllArgsConstructor
public class FunctionInfo {
    public final FunctionDeclaration declaration;
    public final Method method;
    
    public long getSize() {
        // The declaration.toJson() method returns a Map object. 
        // We must serialize it to a JSON string to get its actual character length.
        return declaration.toJson().length();
    }
}