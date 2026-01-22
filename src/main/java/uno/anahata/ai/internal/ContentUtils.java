/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.internal;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Arrays;
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
     * <b>Signature Preservation:</b> This method is "Signature-Aware". It identifies 
     * the latest {@code thoughtSignature} in the original content. If the parts 
     * containing this signature are being pruned, the signature is migrated to the 
     * last remaining part (or a new minimal part) to ensure the model's state 
     * is preserved across turns.
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
        
        // 1. Find the "latest" (last occurring) signature in the original content.
        // This represents the most recent state of the model.
        byte[] latestSignature = null;
        for (Part p : originalParts) {
            if (p.thoughtSignature().isPresent()) {
                latestSignature = p.thoughtSignature().get();
            }
        }

        // 2. Identify which parts we are keeping.
        List<Part> partsToKeep = new ArrayList<>();
        for (Part p : originalParts) {
            if (!partsToRemove.contains(p)) {
                partsToKeep.add(p);
            }
        }

        // 3. Check if the latest signature is still present in the kept parts.
        boolean signaturePreserved = false;
        if (latestSignature != null) {
            for (Part p : partsToKeep) {
                if (p.thoughtSignature().isPresent() && Arrays.equals(p.thoughtSignature().get(), latestSignature)) {
                    signaturePreserved = true;
                    break;
                }
            }
        }

        // 4. If the latest signature was lost due to pruning, migrate it to the end of the kept parts.
        if (latestSignature != null && !signaturePreserved) {
            if (!partsToKeep.isEmpty()) {
                int lastIdx = partsToKeep.size() - 1;
                Part lastPart = partsToKeep.get(lastIdx);
                // Attach the signature to the last remaining part.
                partsToKeep.set(lastIdx, lastPart.toBuilder().thoughtSignature(latestSignature).build());
            } else {
                // If all parts were pruned, create a minimal "ghost" part to carry the state.
                partsToKeep.add(Part.builder().thoughtSignature(latestSignature).build());
            }
        }

        return original.toBuilder()
            .parts(partsToKeep)
            .build();
    }
}
