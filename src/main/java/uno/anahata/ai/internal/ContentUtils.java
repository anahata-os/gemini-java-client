/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.internal;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility methods for working with the Google GenAI {@link Content} object.
 * 
 * @author Anahata
 */
public final class ContentUtils {
    
    /**
     * Creates a new {@link Content} object that is a clone of the original, but with
     * a list of specific {@link Part}s removed.
     * <p>
     * This is useful for pruning operations where specific parts of a message
     * need to be discarded while preserving the rest of the content and the role.
     * </p>
     * <p>
     * <b>Note on Signatures:</b> This method does NOT migrate or preserve 
     * {@code thoughtSignature}s. If a part containing a signature is pruned, 
     * the signature is lost. This is intentional to avoid providing the model 
     * with an invalid or mutated reasoning state.
     * </p>
     *
     * @param original      The original Content object.
     * @param partsToRemove The list of Parts to exclude from the new object.
     * @return A new Content object without the specified parts, or the original
     *         if no parts were removed.
     */
    public static Content cloneAndRemoveParts(Content original, List<Part> partsToRemove) {
        if (original == null || !original.parts().isPresent() || partsToRemove == null || partsToRemove.isEmpty()) {
            return original;
        }

        List<Part> originalParts = original.parts().get();
        
        // Identify which parts we are keeping.
        List<Part> partsToKeep = new ArrayList<>();
        for (Part p : originalParts) {
            if (!partsToRemove.contains(p)) {
                partsToKeep.add(p);
            }
        }

        // If no parts were removed, return the original to save memory/cycles.
        if (partsToKeep.size() == originalParts.size()) {
            return original;
        }

        return original.toBuilder()
            .parts(partsToKeep)
            .build();
    }
}
