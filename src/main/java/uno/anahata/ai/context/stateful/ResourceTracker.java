/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.stateful;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.internal.JacksonUtils;
import uno.anahata.ai.tools.ContextBehavior;
import uno.anahata.ai.tools.ToolManager;
import uno.anahata.ai.gemini.GeminiAdapter;

@Slf4j
public class ResourceTracker {

    private final ContextManager contextManager;

    public ResourceTracker(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Checks a FunctionResponse to see if it's from a STATEFUL_REPLACE tool,
     * and if so, extracts the resource ID.
     *
     * @param fr The FunctionResponse to check.
     * @param toolManager The ToolManager to query for tool behavior.
     * @return An Optional containing the resource ID if the response is stateful,
     * otherwise an empty Optional.
     */
    public static Optional<String> getResourceIdIfStateful(FunctionResponse fr, ToolManager toolManager) {
        String toolName = fr.name().orElse("");
        if (toolManager.getContextBehavior(toolName) != ContextBehavior.STATEFUL_REPLACE) {
            return Optional.empty();
        }

        try {
            Method toolMethod = toolManager.getToolMethod(toolName);
            if (toolMethod != null && StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
                Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
                
                // If the "output" key exists, it's a wrapped primitive/array, not a complex StatefulResource object.
                if (responseMap.size() == 1 && responseMap.containsKey("output")) {
                    return Optional.empty();
                }
                
                // There was an exception
                if (responseMap.size() == 1 && responseMap.containsKey("error")) {
                    return Optional.empty();
                }

                // DIAGNOSTIC LOGGING
                log.info("Attempting to deserialize for resource tracking. Response map: {}", responseMap.keySet());

                // The entire map is the serialized object.
                StatefulResource pojo = JacksonUtils.convertMapToObject(responseMap, (Class<StatefulResource>) toolMethod.getReturnType());
                if (pojo == null) {
                    return Optional.empty();
                }
                
                String resourceId = pojo.getResourceId();
                return Optional.ofNullable(resourceId);
            }
        } catch (Exception e) {
            log.warn("Could not determine resource ID for stateful tool: " + toolName, e);
        }

        return Optional.empty();
    }

    /**
     * Checks if a specific tool call ID is associated with a stateful resource in the current context.
     * 
     * @param toolCallId The tool call ID to check.
     * @return true if the tool call produced a stateful resource.
     */
    public boolean isStatefulToolCall(String toolCallId) {
        if (toolCallId == null) return false;
        ToolManager fm = contextManager.getToolManager();
        for (ChatMessage message : contextManager.getContext()) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    if (toolCallId.equals(fr.id().orElse(null))) {
                        return getResourceIdIfStateful(fr, fm).isPresent();
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if a specific Part is associated with a stateful resource interaction.
     * This includes both the FunctionCall and the FunctionResponse.
     * 
     * @param part The part to check.
     * @param message The message the part belongs to.
     * @return true if the part is part of a stateful resource interaction.
     */
    public boolean isStatefulPart(Part part, ChatMessage message) {
        return getResourceStatus(part, message).isPresent();
    }

    /**
     * Gets the status of a stateful resource associated with a part.
     * This method handles both FunctionCall and FunctionResponse parts.
     * 
     * @param part The part to check.
     * @param message The message the part belongs to.
     * @return An Optional containing the status if the part is stateful.
     */
    public Optional<StatefulResourceStatus> getResourceStatus(Part part, ChatMessage message) {
        if (part == null || message == null) return Optional.empty();
        
        if (part.functionResponse().isPresent()) {
            return getResourceStatus(part.functionResponse().get());
        }
        
        if (part.functionCall().isPresent()) {
            String callId = part.functionCall().get().id().orElse(null);
            if (callId != null && message.getDependencies() != null) {
                // Robust lookup: find the entry in dependencies that matches this part's tool call ID.
                // This avoids issues with object identity after serialization/deserialization.
                List<Part> deps = null;
                for (Map.Entry<Part, List<Part>> entry : message.getDependencies().entrySet()) {
                    if (GeminiAdapter.getToolCallId(entry.getKey()).filter(callId::equals).isPresent()) {
                        deps = entry.getValue();
                        break;
                    }
                }
                
                if (deps != null) {
                    for (Part dep : deps) {
                        if (dep.functionResponse().isPresent() && callId.equals(dep.functionResponse().get().id().orElse(null))) {
                            return getResourceStatus(dep.functionResponse().get());
                        }
                    }
                }
            }
        }
        
        return Optional.empty();
    }

    public void handleStatefulReplace(ChatMessage newMessage, ToolManager toolManager) {
        if (toolManager == null) {
            return;
        }

        List<String> newResourceIds = newMessage.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionResponse().isPresent())
                .map(part -> ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), toolManager))
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());

        if (newResourceIds.isEmpty()) {
            return;
        }

        log.info("New stateful resource(s) detected: {}. Scanning for stale parts.", newResourceIds);

        Set<Part> partsToPrune = new HashSet<>();
        for (ChatMessage message : contextManager.getContext()) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    Optional<String> resourceIdOpt = ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), toolManager);
                    if (resourceIdOpt.isPresent() && newResourceIds.contains(resourceIdOpt.get())) {
                        partsToPrune.add(part);
                        if (message.getDependencies() != null) {
                            partsToPrune.addAll(message.getDependencies().getOrDefault(part, Collections.emptyList()));
                        }
                    }
                }
            }
        }

        if (!partsToPrune.isEmpty()) {
            contextManager.prunePartsByReference(new ArrayList<>(partsToPrune), "Pruning stale stateful resource and its associated call.");
        }
    }

    public List<StatefulResourceStatus> getStatefulResourcesOverview() {
        List<StatefulResourceStatus> statuses = new ArrayList<>();
        ToolManager fm = contextManager.getToolManager();

        for (ChatMessage message : contextManager.getContext()) {
            if (message.getContent() == null || !message.getContent().parts().isPresent()) {
                continue;
            }

            int partIndex = 0; // Initialize part index
            for (Part part : message.getContent().parts().get()) {
                if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    String toolCallId = fr.id().orElse(null); // Use orElse(null) for safety
                    String partId = message.getSequentialId() + "/" + partIndex; // Construct partId
                    getResourceStatus(fr, fm, toolCallId, partId).ifPresent(statuses::add);
                }
                partIndex++; // Increment part index
            }
        }

        // Simple deduplication based on resourceId, keeping the last one (most recent in context)
        Map<String, StatefulResourceStatus> uniqueStatuses = new java.util.LinkedHashMap<>();
        for (StatefulResourceStatus status : statuses) {
            uniqueStatuses.put(status.resource.getResourceId(), status);
        }

        return new ArrayList<>(uniqueStatuses.values());
    }
    
    /**
     * Gets the status of a single resource directly from its FunctionResponse.
     *
     * @param fr The FunctionResponse that created the stateful resource.
     * @return An Optional containing the status, or empty if the response is not a valid stateful resource.
     */
    public Optional<StatefulResourceStatus> getResourceStatus(FunctionResponse fr) {
        // This method is for external calls that don't have the toolCallId and partId readily available.
        // It will call the internal method with nulls.
        return getResourceStatus(fr, contextManager.getToolManager(), null, null);
    }

    private Optional<StatefulResourceStatus> getResourceStatus(FunctionResponse fr, ToolManager fm, String toolCallId, String partId) {
        String toolName = fr.name().orElse("");
        Method toolMethod = fm.getToolMethod(toolName);

        if (toolMethod == null || !StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
            return Optional.empty();
        }

        try {
            Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
            
            // If the "output" key exists, it's a wrapped primitive/array, not a complex StatefulResource object.
            if (responseMap.containsKey("output")) {
                return Optional.empty();
            }

            // The entire map is the serialized object.
            StatefulResource resource = JacksonUtils.convertMapToObject(responseMap, (Class<StatefulResource>) toolMethod.getReturnType());
            if (resource == null) {
                return Optional.empty();
            }

            String resourceId = resource.getResourceId();
            if (resourceId == null) {
                return Optional.empty();
            }

            return Optional.of(checkDiskStatus(resource, toolCallId, partId));

        } catch (Exception e) {
            log.warn("Failed to deserialize stateful resource from tool response: " + toolName, e);
            return Optional.empty();
        }
    }

    private StatefulResourceStatus checkDiskStatus(StatefulResource resource, String toolCallId, String partId) {
        String resourceId = resource.getResourceId();
        long contextLastModified = resource.getLastModified();
        long contextSize = resource.getSize();

        Path path = Paths.get(resourceId);
        long diskLastModified = 0;
        long diskSize = 0;
        ResourceStatus status;

        try {
            if (!Files.exists(path)) {
                status = ResourceStatus.DELETED;
            } else {
                diskLastModified = Files.getLastModifiedTime(path).toMillis();
                diskSize = Files.size(path);

                if (diskLastModified > contextLastModified) {
                    status = ResourceStatus.STALE;
                } else if (diskLastModified < contextLastModified) {
                    status = ResourceStatus.OLDER;
                } else if (diskSize != contextSize) {
                    status = ResourceStatus.STALE;
                } else {
                    status = ResourceStatus.VALID;
                }
            }
        } catch (Exception e) {
            log.warn("Error checking disk status for resource: " + resourceId, e);
            status = ResourceStatus.ERROR;
        }

        return new StatefulResourceStatus(resourceId, contextLastModified, contextSize, diskLastModified, diskSize, status, resource, partId, toolCallId);
    }

    public void pruneStatefulResources(List<String> resourceIds, String reason) {
        ToolManager toolManager = contextManager.getToolManager();
        if (resourceIds == null || resourceIds.isEmpty() || toolManager == null) {
            return;
        }

        log.info("Attempting to prune stateful resources: {}. Reason: {}", resourceIds, reason);

        Set<Part> partsToPrune = new HashSet<>();
        for (ChatMessage message : contextManager.getContext()) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    Optional<String> resourceIdOpt = ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), toolManager);
                    if (resourceIdOpt.isPresent() && resourceIds.contains(resourceIdOpt.get())) {
                        partsToPrune.add(part);
                        if (message.getDependencies() != null) {
                            partsToPrune.addAll(message.getDependencies().getOrDefault(part, Collections.emptyList()));
                        }
                    }
                }
            }
        }

        if (!partsToPrune.isEmpty()) {
            contextManager.prunePartsByReference(new ArrayList<>(partsToPrune), reason);
        }
    }
}
