package uno.anahata.gemini.internal;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uno.anahata.gemini.context.StatefulResource;
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
    public static final class Fingerprint {
        private final String name;
        private final Map<String, Object> args;

        public Fingerprint(String name, Map<String, Object> args) {
            this.name = name;
            this.args = args;
        }

        public String name() {
            return name;
        }

        public Map<String, Object> args() {
            return args;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Fingerprint that = (Fingerprint) o;
            return Objects.equals(name, that.name) && Objects.equals(args, that.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, args);
        }
    }

    /**
     * Creates a fingerprint from a FunctionCall.
     */
    public static Fingerprint fingerprintOf(FunctionCall fc) {
        return new Fingerprint(fc.name().orElse(""), fc.args().orElse(Collections.emptyMap()));
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
