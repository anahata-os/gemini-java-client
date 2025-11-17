package uno.anahata.ai.tools;

import com.google.genai.types.FunctionDeclaration;
import java.lang.reflect.Method;
import lombok.AllArgsConstructor;

/**
 * Maps a FunctionDeclaration to its declaring method.
 *
 * @author pablo-ai
 */
@AllArgsConstructor
public class FunctionInfo {
    public final FunctionDeclaration declaration;
    public final Method method;
}
