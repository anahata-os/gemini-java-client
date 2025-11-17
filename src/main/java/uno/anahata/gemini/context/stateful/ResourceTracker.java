package uno.anahata.gemini.context.stateful;

import uno.anahata.gemini.ChatMessage;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.ToolManager;
import uno.anahata.gemini.internal.GsonUtils;

@Slf4j
public class ResourceTracker {

    private static final Gson GSON = GsonUtils.getGson();
    private final ContextManager contextManager;

    public ResourceTracker(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Checks a FunctionResponse to see if it's from a STATEFUL_REPLACE tool,
     * and if so, extracts the resource ID.
     *
     * @param fr The FunctionResponse to check.
     * @param functionManager The ToolManager to query for tool behavior.
     * @return An Optional containing the resource ID if the response is stateful,
     * otherwise an empty Optional.
     */
    public static Optional<String> getResourceIdIfStateful(FunctionResponse fr, ToolManager functionManager) {
        String toolName = fr.name().orElse("");
        if (functionManager.getContextBehavior(toolName) != ContextBehavior.STATEFUL_REPLACE) {
            return Optional.empty();
        }

        try {
            Method toolMethod = functionManager.getToolMethod(toolName);
            if (toolMethod != null && StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
                JsonElement jsonTree = GSON.toJsonTree(fr.response().get());
                Object pojo = GSON.fromJson(jsonTree, toolMethod.getReturnType());
                String resourceId = ((StatefulResource) pojo).getResourceId();
                return Optional.ofNullable(resourceId);
            }
        } catch (Exception e) {
            // Log this? For now, returning empty is safe.
        }
        
        return Optional.empty();
    }

    public void handleStatefulReplace(ChatMessage newMessage, ToolManager functionManager) {
        if (functionManager == null) {
            return;
        }

        List<String> newResourceIds = newMessage.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionResponse().isPresent())
                .map(part -> ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), functionManager))
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
                    Optional<String> resourceIdOpt = ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), functionManager);
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
        ToolManager fm = contextManager.getFunctionManager();

        for (ChatMessage message : contextManager.getContext()) {
            if (message.getContent() == null || !message.getContent().parts().isPresent()) {
                continue;
            }

            for (Part part : message.getContent().parts().get()) {
                if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    getResourceStatus(fr, fm).ifPresent(statuses::add);
                }
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
        return getResourceStatus(fr, contextManager.getFunctionManager());
    }

    private Optional<StatefulResourceStatus> getResourceStatus(FunctionResponse fr, ToolManager fm) {
        String toolName = fr.name().orElse("");
        Method toolMethod = fm.getToolMethod(toolName);

        if (toolMethod == null || !StatefulResource.class.isAssignableFrom(toolMethod.getReturnType())) {
            return Optional.empty();
        }

        try {
            // Deserialize the response payload into the expected POJO which is a StatefulResource
            StatefulResource resource = (StatefulResource) GSON.fromJson(GSON.toJsonTree(fr.response().get()), toolMethod.getReturnType());

            String resourceId = resource.getResourceId();
            if (resourceId == null) {
                return Optional.empty();
            }

            return Optional.of(checkDiskStatus(resource));

        } catch (Exception e) {
            log.warn("Failed to deserialize stateful resource from tool response: " + toolName, e);
            return Optional.empty();
        }
    }

    private StatefulResourceStatus checkDiskStatus(StatefulResource resource) {
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

        return new StatefulResourceStatus(resourceId, contextLastModified, contextSize, diskLastModified, diskSize, status, resource);
    }

    public void pruneStatefulResources(List<String> resourceIds) {
        ToolManager functionManager = contextManager.getFunctionManager();
        if (resourceIds == null || resourceIds.isEmpty() || functionManager == null) {
            return;
        }

        log.info("Attempting to prune stateful resources: {}.", resourceIds);

        Set<Part> partsToPrune = new HashSet<>();
        for (ChatMessage message : contextManager.getContext()) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    Optional<String> resourceIdOpt = ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), functionManager);
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
            contextManager.prunePartsByReference(new ArrayList<>(partsToPrune), "User requested pruning of stateful resources.");
        } else {
            log.warn("No parts found for the specified stateful resource IDs: {}", resourceIds);
        }
    }
}
