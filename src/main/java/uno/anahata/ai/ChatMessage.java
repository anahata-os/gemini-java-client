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
 * This is the core data model for the new architecture.
 * @author Anahata
 */
@Data
@Builder(toBuilder = true)
@Slf4j
public class ChatMessage {

    private final long sequentialId;
    @Setter
    private long elapsedTimeMillis;
    private final String modelId;
    private final Content content;
    private final GenerateContentResponseUsageMetadata usageMetadata;
    private final GroundingMetadata groundingMetadata;
    // CHANGED: Removed 'final' to allow mutation after creation.
    private Map<Part, List<Part>> dependencies;
    
    @Builder.Default
    private final Instant createdOn = Instant.now();

    // This field is mutable and is set after the model's response is received.
    private List<FunctionResponse> functionResponses;
    
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
