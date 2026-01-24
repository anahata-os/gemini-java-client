/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A rich, stateful representation of a single message in the chat history.
 * <p>
 * This class encapsulates the content of a message (text, blobs, function calls/responses),
 * along with metadata such as token usage, grounding information, and internal dependencies
 * between parts (e.g., which function response belongs to which function call).
 * </p>
 * <p>
 * It uses a dependency graph to track relationships between {@link Part} objects,
 * which is essential for precise context pruning and stateful resource management.
 * </p>
 * 
 * @author Anahata
 */
@Data
@Builder(toBuilder = true)
@Slf4j
public class ChatMessage {

    /**
     * A unique, sequential identifier for the message within a chat session.
     */
    private final long sequentialId;
    
    /**
     * The time elapsed in milliseconds between the previous message and this one.
     */
    @Setter
    private long elapsedTimeMillis;
    
    /**
     * The ID of the model that generated this message (if applicable).
     */
    private final String modelId;
    
    /**
     * The actual content of the message, consisting of one or more {@link Part} objects.
     */
    private final Content content;
    
    /**
     * Metadata regarding token usage for this message, as reported by the Gemini API.
     */
    private final GenerateContentResponseUsageMetadata usageMetadata;
    
    /**
     * Grounding metadata provided by the model, linking response parts to source information.
     */
    private final GroundingMetadata groundingMetadata;
    
    /**
     * A map representing the dependency graph between parts within this message.
     * Keys are source parts, and values are lists of parts that depend on them.
     */
    private Map<Part, List<Part>> dependencies;
    
    /**
     * The timestamp when this message was created.
     */
    @Builder.Default
    private final Instant createdOn = Instant.now();

    /**
     * Indicates if this message is a system-generated tool feedback message.
     * Tool feedback messages have the 'user' role but should not be counted
     * as actual user turns for pruning purposes.
     */
    private final boolean toolFeedback;

    /**
     * Performs a full graph traversal to find all parts connected to the startPart,
     * including the startPart itself. This is used to find the complete set of
     * interdependent parts for operations like pruning.
     *
     * @param startPart The part from which to start the dependency search.
     * @return A Set containing all parts in the same dependency group as the startPart.
     * @throws IllegalArgumentException if the startPart is not found within this message's dependency graph.
     */
    public Set<Part> getAllDependencies(Part startPart) {
        if (dependencies == null || !dependencies.containsKey(startPart)) {
            throw new IllegalArgumentException("The specified startPart is not a key in this message's dependency graph.");
        }

        Set<Part> visited = new HashSet<>();
        Queue<Part> toVisit = new ArrayDeque<>();

        visited.add(startPart);
        toVisit.add(startPart);

        while (!toVisit.isEmpty()) {
            Part current = toVisit.poll();
            List<Part> directDependencies = dependencies.getOrDefault(current, Collections.emptyList());

            for (Part neighbor : directDependencies) {
                if (visited.add(neighbor)) { // If the neighbor hasn't been visited yet
                    toVisit.add(neighbor);
                }
            }
        }
        return visited;
    }

    /**
     * Gets a set of all parts that are involved in any dependency relationship
     * within this message.
     *
     * @return A Set containing every part that is either a source or a target of a dependency.
     */
    public Set<Part> getAllDependencies() {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Part> allDependentParts = new HashSet<>(dependencies.keySet());
        for (List<Part> parts : dependencies.values()) {
            allDependentParts.addAll(parts);
        }
        return allDependentParts;
    }
    
    /**
     * Adds a list of dependent parts to a source part's dependency list,
     * merging with any existing dependencies for that source part.
     *
     * @param sourcePart The part that is the source of the dependency (the key).
     * @param dependentParts The parts that depend on the source part (the values).
     */
    public void addDependencies(Part sourcePart, List<Part> dependentParts) {
        if (dependencies == null) {
            dependencies = new HashMap<>();
            log.info("Dependencies map initialized for message {}.", sequentialId);
        }
        
        List<Part> existing = dependencies.computeIfAbsent(sourcePart, k -> new ArrayList<>());
        int initialSize = existing.size();
        existing.addAll(dependentParts);
        
        log.info("Added {} new dependencies to source part {} in message {}. Total dependencies for part: {}",
                dependentParts.size(), sourcePart.functionCall().map(fc -> fc.name().orElse("Text/Blob")).orElse("Text/Blob"), sequentialId, existing.size());
    }
}
