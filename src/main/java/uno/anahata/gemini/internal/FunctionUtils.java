package uno.anahata.gemini.internal;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.Value;
import uno.anahata.gemini.context.stateful.StatefulResource;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.FunctionManager;

/**
 * Utility methods for working with FunctionCall and FunctionResponse objects.
 * @author Anahata
 */
public final class FunctionUtils {

    private static final Gson GSON = GsonUtils.getGson();

    /**
     * An immutable class to represent the unique fingerprint of a tool call (name + arguments).
     * This is the Java 8 compatible version of a record.
     */
    @Value
    public static final class Fingerprint {
        String name;
        Map<String, Object> args;
    }

    /**
     * Checks a FunctionResponse to see if it's from a STATEFUL_REPLACE tool,
     * and if so, extracts the resource ID.
     *
     * @param fr The FunctionResponse to check.
     * @param functionManager The FunctionManager to query for tool behavior.
     * @return An Optional containing the resource ID if the response is stateful,
     * otherwise an empty Optional.
     */
    public static Optional<String> getResourceIdIfStateful(FunctionResponse fr, FunctionManager functionManager) {
        String toolName = fr.name().orElse("");
        if (functionManager.getContextBehavior(toolName) != ContextBehavior.STATEFUL_REPLACE) {
            return Optional.empty();
        }

        try {
            Method toolMethod = functionManager.getToolMethod(toolName);
            if (toolMethod != null && StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
                Object pojo = GSON.fromJson(GSON.toJsonTree(fr.response().get()), toolMethod.getReturnType());
                String resourceId = ((StatefulResource) pojo).getResourceId();
                return Optional.ofNullable(resourceId);
            }
        } catch (Exception e) {
            // Log this? For now, returning empty is safe.
        }
        
        return Optional.empty();
    }
}
