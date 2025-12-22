package uno.anahata.ai.context.pruning;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.gemini.GeminiAdapter;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.stateful.ResourceTracker;
import uno.anahata.ai.tools.ContextBehavior;
import uno.anahata.ai.tools.ToolManager;
import uno.anahata.ai.internal.ContentUtils;

@Slf4j
public class ContextPruner {

    private final ContextManager contextManager;

    public ContextPruner(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Prunes entire ChatMessage objects from the context based on their unique sequential IDs.
     * This method now only collects the initial parts to be removed and delegates
     * the full dependency traversal and removal to {@link #prunePartsByReference(java.util.List, java.lang.String)}.
     *
     * @param sequentialIds A list of message sequential IDs to remove.
     * @param reason        The reason for the pruning, for logging purposes.
     */
    public void pruneMessages(List<Long> sequentialIds, String reason) {
        log.info("PruneMessages called for Sequential IDs: {} with reason: {}", sequentialIds, reason);
        List<ChatMessage> context = contextManager.getContext();
        Set<Part> initialCandidates = new HashSet<>();
        Set<Long> idsToPrune = new HashSet<>(sequentialIds);

        // 1. Collect all parts from the targeted messages as initial candidates
        for (ChatMessage message : context) {
            if (idsToPrune.contains(message.getSequentialId())) {
                message.getContent().parts().ifPresent(initialCandidates::addAll);
            }
        }

        if (initialCandidates.isEmpty()) {
            log.warn("No parts found in messages with IDs {} to prune. Aborting.", sequentialIds);
            return;
        }

        log.info("Collected {} initial parts from {} messages.", initialCandidates.size(), sequentialIds.size());

        // 2. Delegate to the low-level method for full dependency resolution and removal
        prunePartsByReference(new ArrayList<>(initialCandidates), reason);
    }

    /**
     * Prunes specific parts from a single ChatMessage, identified by the message's sequential ID and the parts' indices.
     * This method now only collects the initial parts to be removed and delegates
     * the full dependency traversal and removal to {@link #prunePartsByReference(java.util.List, java.lang.String)}.
     *
     * @param messageSequentialId The sequential ID of the message containing the parts to prune.
     * @param partIndices         A list of zero-based indices of the parts to remove.
     * @param reason              The reason for the pruning, for logging purposes.
     */
    public void pruneParts(long messageSequentialId, List<Number> partIndices, String reason) {
        log.info("PruneParts called for message ID: {} with indices: {} and reason: {}", messageSequentialId, partIndices, reason);
        // Get a mutable reference to the context.
        List<ChatMessage> context = contextManager.getContext();
        // Find the specific message the user wants to modify.
        ChatMessage targetMessage = context.stream()
                .filter(m -> m.getSequentialId() == messageSequentialId)
                .findFirst()
                .orElse(null);

        // If the message doesn't exist, log a warning and exit.
        if (targetMessage == null) {
            log.warn("Could not find message with ID {} to prune parts. Aborting.", messageSequentialId);
            return;
        }

        // Get the list of parts from the target message.
        List<Part> originalParts = targetMessage.getContent().parts().orElse(Collections.emptyList());
        // Convert the numeric indices from the model into a Set of Longs for efficient lookup.
        Set<Long> indicesToPrune = partIndices.stream().map(Number::longValue).collect(Collectors.toSet());
        // This set will hold the actual Part objects that correspond to the user-provided indices.
        Set<Part> initialCandidates = new HashSet<>();

        // 1. Collect initial candidates
        for (int i = 0; i < originalParts.size(); i++) {
            if (indicesToPrune.contains((long) i)) {
                initialCandidates.add(originalParts.get(i));
            }
        }

        // If no valid parts were found for the given indices, log a warning and exit.
        if (initialCandidates.isEmpty()) {
            log.warn("None of the specified part indices {} were valid for message {}. Aborting.", partIndices, messageSequentialId);
            return;
        }

        log.info("Collected {} initial parts from message {}.", initialCandidates.size(), messageSequentialId);

        // 2. Delegate to the low-level method for full dependency resolution and removal
        prunePartsByReference(new ArrayList<>(initialCandidates), reason);
    }

    /**
     * The low-level workhorse method that removes a given set of Part objects from any message in the context.
     * This method now includes the full, recursive, cross-message dependency traversal logic and cleans the
     * dependency map of the resulting ChatMessage objects.
     *
     * @param initialCandidates A list of the actual Part objects to remove.
     * @param reason            The reason for the pruning.
     */
    public void prunePartsByReference(List<Part> initialCandidates, String reason) {
        if (initialCandidates == null || initialCandidates.isEmpty()) {
            return;
        }

        List<ChatMessage> context = contextManager.getContext();
        Set<Part> fullPruneSet = new HashSet<>(initialCandidates);
        int initialCount = initialCandidates.size();

        log.info("Starting prunePartsByReference. Initial candidates: {}. Reason: {}", initialCount, reason);

        // === Phase 1: Full Dependency Traversal (Cross-Message) ===
        // Loop until no new dependencies are found across all messages.
        boolean changed;
        int iteration = 0;
        do {
            changed = false;
            iteration++;
            int partsBeforeIteration = fullPruneSet.size();

            for (ChatMessage message : context) {
                if (message.getDependencies() != null) {
                    for (Map.Entry<Part, List<Part>> entry : message.getDependencies().entrySet()) {
                        Part sourcePart = entry.getKey();
                        List<Part> dependentParts = entry.getValue();

                        // Check if the source part is in the set (forward traversal)
                        if (fullPruneSet.contains(sourcePart)) {
                            for (Part dependentPart : dependentParts) {
                                if (fullPruneSet.add(dependentPart)) {
                                    log.info("Dependency found (Forward): Added part {} from message #{}", dependentPart.functionResponse().map(fr -> fr.name().orElse("?")).orElse("?"), message.getSequentialId());
                                    changed = true;
                                }
                            }
                        }

                        // Check if any dependent part is in the set (reverse traversal)
                        for (Part dependentPart : dependentParts) {
                            if (fullPruneSet.contains(dependentPart)) {
                                if (fullPruneSet.add(sourcePart)) {
                                    log.info("Dependency found (Reverse): Added part {} from message #{}", sourcePart.functionCall().map(fc -> fc.name().orElse("?")).orElse("?"), message.getSequentialId());
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
            log.info("Traversal iteration {}: Added {} new parts. Total parts: {}", iteration, fullPruneSet.size() - partsBeforeIteration, fullPruneSet.size());
        } while (changed);

        int dependencyCount = fullPruneSet.size() - initialCount;

        // Log the action before execution
        log.info("Pruning execution set finalized. Total parts: {} ({} initial candidates + {} from dependencies).",
                 fullPruneSet.size(), initialCount, dependencyCount);

        // === Phase 2: Execution (Removal) ===
        Set<Part> partsToPruneSet = fullPruneSet;
        boolean contextWasModified = false;

        // Use a ListIterator, which allows safe removal/replacement while iterating.
        ListIterator<ChatMessage> iterator = context.listIterator();
        while (iterator.hasNext()) {
            ChatMessage currentMessage = iterator.next();
            Content originalContent = currentMessage.getContent();
            if (originalContent == null || !originalContent.parts().isPresent()) {
                continue;
            }

            List<Part> originalParts = originalContent.parts().get();
            // Use Collections.disjoint for a highly efficient check to see if this message contains any parts we need to prune.
            if (Collections.disjoint(originalParts, partsToPruneSet)) {
                continue;
            }

            log.info("Modifying message #{} (Role: {}) to remove parts.", currentMessage.getSequentialId(), currentMessage.getContent().role().orElse("N/A"));

            // If we're here, the message needs to be modified.
            // Create a new list containing only the parts we want to keep.
            List<Part> partsToKeep = originalParts.stream()
                    .filter(p -> !partsToPruneSet.contains(p))
                    .collect(Collectors.toList());

            contextWasModified = true;
            // If, after removing parts, the message becomes empty...
            if (partsToKeep.isEmpty()) {
                // ...remove the entire message from the context.
                iterator.remove();
                log.info("Message #{} became empty and was removed from context.", currentMessage.getSequentialId());
            } else {
                // Otherwise, create a new Content object with the remaining parts...
                Content newContent = ContentUtils.cloneAndRemoveParts(originalContent, new ArrayList<>(partsToPruneSet));

                // CRITICAL STEP: Clean the dependency map before creating the replacement message.
                Map<Part, List<Part>> cleanedDependencies = cleanDependencies(currentMessage, new HashSet<>(partsToKeep), partsToPruneSet);

                // ...and replace the old message with a new, updated one.
                // NOTE: We must create a new ChatMessage because the Content field is immutable.
                ChatMessage replacement = currentMessage.toBuilder()
                        .content(newContent)
                        .dependencies(cleanedDependencies)
                        .build();
                iterator.set(replacement);
                log.info("Message #{} updated. Kept {} parts.", currentMessage.getSequentialId(), partsToKeep.size());
            }
        }

        // If any changes were made, log the action and notify the context manager.
        if (contextWasModified) {
            log.info("Context modification complete. Notifying listeners.");
            contextManager.setContext(context); // Notify change
        } else {
            log.info("Pruning completed, but no context modification was necessary.");
        }
    }
    
    public void pruneEphemeralToolCall(String toolCallId, String reason) {
        log.info("Pruning ephemeral tool call by ID: {}. Reason: {}", toolCallId, reason);
        if (contextManager.getResourceTracker().isStatefulToolCall(toolCallId)) {
            throw new IllegalArgumentException("Tool call ID '" + toolCallId + "' is associated with a STATEFUL resource. Use pruneStatefulResources instead.");
        }
        pruneToolCall(toolCallId, reason);
    }

    public void pruneOther(List<Part> parts, String reason) {
        log.info("Pruning other parts. Reason: {}", reason);
        for (Part part : parts) {
            if (part.functionCall().isPresent() || part.functionResponse().isPresent()) {
                throw new IllegalArgumentException("Generic pruning is not allowed for tool calls. Use pruneEphemeralToolCall or pruneStatefulResources.");
            }
        }
        prunePartsByReference(parts, reason);
    }

    public void pruneToolCall(String toolCallId, String reason) {
        log.info("Pruning tool call by ID: {}. Reason: {}", toolCallId, reason);
        List<Part> partsToPrune = new ArrayList<>();
        for (ChatMessage message : contextManager.getContext()) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                String id = GeminiAdapter.getToolCallId(part).orElse(null);
                if (toolCallId.equals(id)) {
                    partsToPrune.add(part);
                }
            }
        }
        
        if (partsToPrune.isEmpty()) {
            log.warn("No parts found with tool call ID '{}'. No action taken.", toolCallId);
            return;
        }
        
        log.info("Found {} parts with tool call ID '{}'. Delegating to prunePartsByReference.", partsToPrune.size(), toolCallId);
        prunePartsByReference(partsToPrune, reason);
    }

    /**
     * Cleans the dependency map of a ChatMessage to ensure it only contains valid links
     * after parts have been removed.
     *
     * @param message         The original message.
     * @param partsToKeep     The parts that remain in the message's content.
     * @param partsToPruneSet The global set of all parts being pruned from the context.
     * @return A new, cleaned dependency map.
     */
    private Map<Part, List<Part>> cleanDependencies(ChatMessage message, Set<Part> partsToKeep, Set<Part> partsToPruneSet) {
        Map<Part, List<Part>> oldDependencies = message.getDependencies();
        if (oldDependencies == null || oldDependencies.isEmpty()) {
            log.info("cleanDependencies: No dependencies to clean for message #{}", message.getSequentialId());
            return null;
        }

        Map<Part, List<Part>> newDependencies = new HashMap<>();

        for (Map.Entry<Part, List<Part>> entry : oldDependencies.entrySet()) {
            Part sourcePart = entry.getKey();
            List<Part> dependentParts = entry.getValue();

            // 1. Check if the source part (the key) is still in the message's content.
            if (partsToKeep.contains(sourcePart)) {

                // 2. Filter the dependent parts (the values) to remove any that are being pruned globally.
                List<Part> cleanedDependentParts = dependentParts.stream()
                        .filter(p -> !partsToPruneSet.contains(p))
                        .collect(Collectors.toList());

                // 3. Only keep the entry if there are still valid dependent parts.
                if (!cleanedDependentParts.isEmpty()) {
                    newDependencies.put(sourcePart, cleanedDependentParts);
                    log.info("cleanDependencies: Kept source part {} with {} remaining dependencies.", sourcePart.functionCall().map(fc -> fc.name().orElse("?")).orElse("?"), cleanedDependentParts.size());
                } else {
                    log.info("cleanDependencies: Removed source part {} as all its dependent parts were pruned.", sourcePart.functionCall().map(fc -> fc.name().orElse("?")).orElse("?"));
                }
            } else {
                log.info("cleanDependencies: Removed source part {} as it was pruned from the message content.", sourcePart.functionCall().map(fc -> fc.name().orElse("?")).orElse("?"));
            }
        }

        log.info("cleanDependencies: Final dependency map size for message #{}: {}", message.getSequentialId(), newDependencies.size());
        return newDependencies.isEmpty() ? null : newDependencies;
    }

    /**
     * Implements the new, more robust logic for automatically pruning old tool calls based on our expanded definition of "ephemeral".
     * This method now only collects the initial candidates and delegates the full dependency traversal and removal.
     *
     * @param functionManager The ToolManager, needed to check tool metadata.
     */
    public void pruneEphemeralToolCalls(ToolManager functionManager) {
        final int turnsToKeep = 5;
        log.info("Starting pruneEphemeralToolCalls (" + turnsToKeep + "-Turn Rule check).");
        if (functionManager == null) {
            log.warn("FunctionManager is null. Aborting ephemeral pruning.");
            return;
        }
        List<ChatMessage> context = contextManager.getContext();

        // Find the index of the message that marks our cutoff point (2 user turns ago).
        List<Integer> userMessageIndices = new ArrayList<>();
        for (int i = context.size() - 1; i >= 0; i--) {
            if (isUserMessage(context.get(i))) {
                userMessageIndices.add(i);
            }
        }

        // If there aren't enough user turns yet, there's nothing to prune.
        if (userMessageIndices.size() <= turnsToKeep) {
            log.info("Not enough user turns ({} <= {}). Aborting ephemeral pruning.", userMessageIndices.size(), turnsToKeep);
            return;
        }
        int pruneCutoffIndex = userMessageIndices.get(turnsToKeep);
        log.info("Pruning cutoff index determined: {}. Messages older than this index will be scanned.", pruneCutoffIndex);

        // === Phase 1: Comprehensive Context Scan ===
        // Build lookup maps in a single pass for efficiency.
        Map<Part, Part> callToResponseMap = new HashMap<>();      // Link calls to their responses to find orphans.
        log.info("Scanning context to build Call-to-Response map for orphan detection.");
        for (ChatMessage message : context) {
            // If this message has dependencies, inspect them to link calls and responses.
            if (message.getDependencies() != null) {
                for (Map.Entry<Part, List<Part>> entry : message.getDependencies().entrySet()) {
                    Part partA = entry.getKey();
                    for (Part partB : entry.getValue()) {
                        // We only care about the direct Call <-> Response link for orphan detection
                        if (partA.functionCall().isPresent() && partB.functionResponse().isPresent()) {
                            callToResponseMap.put(partA, partB);
                        } else if (partB.functionCall().isPresent() && partA.functionResponse().isPresent()) {
                            callToResponseMap.put(partB, partA);
                        }
                    }
                }
            }
        }
        log.info("Call-to-Response map built with {} entries.", callToResponseMap.size());

        // === Phase 2: Apply New Rules to find initial candidates ===
        // This set will hold all parts that are old and meet our new, expanded "ephemeral" criteria.
        Set<Part> initialCandidates = new HashSet<>();
        // Iterate only through the messages that are older than our cutoff.
        for (int i = 0; i < pruneCutoffIndex; i++) {
            ChatMessage message = context.get(i);
            if (message.getContent() == null || !message.getContent().parts().isPresent()) {
                continue;
            }

            for (Part part : message.getContent().parts().get()) {
                boolean isEphemeral = false;
                String reasonCode = "";

                // Condition 1: Is the tool explicitly marked as @AIToolMethod(behavior = EPHEMERAL)?
                if (isPartNaturallyEphemeral(part, functionManager)) {
                    isEphemeral = true;
                    reasonCode = "NATURALLY_EPHEMERAL";
                    // Condition 2: Is it a FunctionCall that has no corresponding FunctionResponse in our map? (Orphan)
                } else if (part.functionCall().isPresent() && !callToResponseMap.containsKey(part)) {
                    isEphemeral = true;
                    reasonCode = "ORPHANED_CALL";
                    // Condition 3: Is it a FunctionResponse from a STATEFUL tool that failed to produce a valid resource?
                } else if (part.functionResponse().isPresent() && isFailedStatefulResponse(part.functionResponse().get(), functionManager)) {
                    isEphemeral = true;
                    reasonCode = "FAILED_STATEFUL_RESPONSE";
                }

                if (isEphemeral) {
                    initialCandidates.add(part);
                    log.info("Candidate found for pruning: Message #{} Part {} (Reason: {})", message.getSequentialId(), part.functionCall().map(fc -> fc.name().orElse("?")).orElse(part.functionResponse().map(fr -> fr.name().orElse("?")).orElse("Text/Blob")), reasonCode);
                }
            }
        }

        if (initialCandidates.isEmpty()) {
            log.info("No ephemeral candidates found. Aborting pruning.");
            return;
        }

        // === Phase 3: Delegate to the low-level method for full dependency resolution and removal ===
        log.info("Found {} initial ephemeral candidates. Delegating to prunePartsByReference.", initialCandidates.size());
        prunePartsByReference(new ArrayList<>(initialCandidates), "Automatic pruning of old or orphaned tool calls.");
    }

    /**
     * Helper to check if a part is associated with a tool marked as EPHEMERAL.
     */
    private boolean isPartNaturallyEphemeral(Part part, ToolManager functionManager) {
        String toolName = "";
        if (part.functionCall().isPresent()) {
            toolName = part.functionCall().get().name().orElse("");
        } else if (part.functionResponse().isPresent()) {
            toolName = part.functionResponse().get().name().orElse("");
        }
        return !toolName.isEmpty() && functionManager.getContextBehavior(toolName) == ContextBehavior.EPHEMERAL;
    }

    /**
     * Helper to check if a FunctionResponse is from a STATEFUL_REPLACE tool but failed to return a valid resource ID.
     */
    private boolean isFailedStatefulResponse(FunctionResponse fr, ToolManager functionManager) {
        String toolName = fr.name().orElse("");
        // Only check if the tool was *supposed* to be stateful.
        if (functionManager.getContextBehavior(toolName) == ContextBehavior.STATEFUL_REPLACE) {
            // If it was stateful, but we can't get a resource ID from its response, it's a "failed" stateful response.
            return ResourceTracker.getResourceIdIfStateful(fr, functionManager).isEmpty();
        }
        return false;
    }

    /**
     * Helper to determine if a message is a user message.
     */
    private boolean isUserMessage(ChatMessage message) {
        return message.getContent() != null && "user".equals(message.getContent().role().orElse(null));
    }
}
