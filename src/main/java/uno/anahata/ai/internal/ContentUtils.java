/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.internal;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility methods for working with the Google GenAI Content object.
 * @author Anahata
 */
public final class ContentUtils {
    
    /**
     * Creates a new Content object that is a clone of the original, but with
     * a list of specific Parts removed.
     *
     * @param original The original Content object.
     * @param partsToRemove The list of Parts to exclude from the new object.
     * @return A new Content object without the specified parts.
     */
    public static Content cloneAndRemoveParts(Content original, List<Part> partsToRemove) {
        if (original == null || !original.parts().isPresent() || partsToRemove == null || partsToRemove.isEmpty()) {
            return original;
        }

        List<Part> partsToKeep = original.parts().get().stream()
            .filter(p -> !partsToRemove.contains(p))
            .collect(Collectors.toList());

        return Content.builder()
            .role(original.role().orElse(null))
            .parts(partsToKeep)
            .build();
    }
}
