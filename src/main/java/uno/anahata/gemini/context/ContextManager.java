package uno.anahata.gemini.context;

import com.google.genai.types.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.ContentUtils;
import uno.anahata.gemini.internal.FunctionUtils;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.internal.PartUtils;

@Slf4j
public class ContextManager {

    private static final Gson GSON = GsonUtils.getGson();

    private List<ChatMessage> context = new ArrayList<>();
    private final GeminiChat chat;
    private final GeminiConfig config;
    private final List<ContextListener> listeners = new CopyOnWriteArrayList<>();
    private FunctionManager functionManager;
    private int totalTokenCount = 0;

    public ContextManager(GeminiChat chat, GeminiConfig config, ContextListener initialListener) {
        this.chat = chat;
        this.config = config;
        if (initialListener != null) {
            this.listeners.add(initialListener);
        }
    }

    /**
     * Gets the ContextManager for the currently executing tool.
     *
     * @return The active ContextManager.
     * @throws IllegalStateException if not called from a tool execution thread.
     */
    public static ContextManager get() {
        GeminiChat chat = GeminiChat.get();
        if (chat == null) {
            throw new IllegalStateException("ContextManager.get() can only be called from a thread where a tool is being executed by the model.");
        }
        return chat.getContextManager();
    }

    public void addListener(ContextListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ContextListener listener) {
        listeners.remove(listener);
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    public synchronized void add(ChatMessage message) {
        handleStatefulReplace(message);
        context.add(message);
        logEntryToFile(message);

        if (message.getUsageMetadata() != null) {
            this.totalTokenCount = message.getUsageMetadata().totalTokenCount().orElse(this.totalTokenCount);
        }

        if (isUserMessage(message)) {
            pruneOldEphemeralResults();
        }
        
        notifyHistoryChange();
    }

    private void handleStatefulReplace(ChatMessage newMessage) {
        if (functionManager == null) {
            return;
        }

        List<String> newResourceIds = newMessage.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionResponse().isPresent())
                .map(part -> FunctionUtils.getResourceIdIfStateful(part.functionResponse().get(), functionManager))
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());

        if (newResourceIds.isEmpty()) {
            return;
        }

        log.info("New stateful resource(s) detected: {}. Scanning for stale parts.", newResourceIds);

        Set<Part> partsToPrune = new HashSet<>();
        for (ChatMessage message : context) {
            for (Part part : message.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    Optional<String> resourceIdOpt = FunctionUtils.getResourceIdIfStateful(part.functionResponse().get(), functionManager);
                    if (resourceIdOpt.isPresent() && newResourceIds.contains(resourceIdOpt.get())) {
                        partsToPrune.add(part);
                        Part callPart = message.getFunctionCallForResponse(part);
                        if (callPart != null) {
                            partsToPrune.add(callPart);
                        }
                    }
                }
            }
        }

        if (!partsToPrune.isEmpty()) {
            prunePartsByReference(new ArrayList<>(partsToPrune), "Pruning stale stateful resource and its associated call.");
        }
    }

    public GeminiConfig getConfig() {
        return config;
    }

    public synchronized int getTotalTokenCount() {
        return totalTokenCount;
    }

    private void logEntryToFile(ChatMessage message) {
        try {
            Content content = message.getContent();
            if (content == null) {
                return;
            }

            Path historyDir = config.getWorkingFolder("history").toPath();
            Files.createDirectories(historyDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String role = content.role().orElse("unknown");
            String modelId = message.getModelId() != null ? message.getModelId().replaceAll("[^a-zA-Z0-9.-]", "_") : "unknown_model";
            String filename = String.format("%s-%s-%s-%s.log", timestamp, role, modelId, getContextId());
            Path logFilePath = historyDir.resolve(filename);

            StringBuilder logContent = new StringBuilder();
            logContent.append("--- HEADER ---\n");
            logContent.append("Message ID: ").append(message.getId()).append("\n");
            logContent.append("Context ID: ").append(getContextId()).append("\n");
            logContent.append("Timestamp: ").append(timestamp).append("\n");
            logContent.append("Role: ").append(role).append("\n");
            logContent.append("Model ID: ").append(message.getModelId()).append("\n");
            logContent.append("--- PARTS ---\n");

            if (content.parts().isPresent()) {
                int partIdx = 0;
                for (Part part : content.parts().get()) {
                    logContent.append("[").append(partIdx).append("] ").append(PartUtils.summarize(part)).append("\n");
                }
            } else {
                logContent.append("(No parts)\n");
            }

            Files.writeString(logFilePath, logContent.toString(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        } catch (Exception e) {
            log.error("Failed to write history content to log file", e);
        }
    }

    public synchronized void clear() {
        context.clear();
        totalTokenCount = 0;
        listeners.forEach(l -> l.contextCleared(chat));
        log.info("Chat history cleared.");
    }

    public synchronized List<ChatMessage> getContext() {
        return new ArrayList<>(context);
    }

    public synchronized void setContext(List<ChatMessage> newContext) {
        this.context = new ArrayList<>(newContext);
        this.totalTokenCount = this.context.stream()
                .mapToInt(cm -> {
                    if (cm.getUsageMetadata() != null) {
                        return cm.getUsageMetadata().totalTokenCount().orElse(0);
                    }
                    if (cm.getContent() != null) {
                        return cm.getContent().toString().length() / 4; // Rough estimate
                    }
                    return 0;
                })
                .sum();
        log.info("Context set. New token count: " + this.totalTokenCount);
        notifyHistoryChange();
    }

    public synchronized void pruneMessages(List<String> uids, String reason) {
        boolean removed = context.removeIf(message -> uids.contains(message.getId()));
        if (removed) {
            log.info("Pruned {} message(s). Reason: {}", uids.size(), reason);
            notifyHistoryChange();
        }
    }

    public synchronized void pruneParts(String messageUID, List<Number> partIndices, String reason) {
        for (int i = 0; i < context.size(); i++) {
            ChatMessage message = context.get(i);
            if (message.getId().equals(messageUID)) {
                Content originalContent = message.getContent();
                if (originalContent == null || !originalContent.parts().isPresent()) {
                    log.warn("Message {} has no parts to prune.", messageUID);
                    return;
                }

                List<Part> originalParts = originalContent.parts().get();
                List<Part> partsToKeep = new ArrayList<>();

                final Set<Long> indicesToPrune = partIndices.stream()
                        .map(Number::longValue)
                        .collect(Collectors.toSet());

                for (int j = 0; j < originalParts.size(); j++) {
                    if (!indicesToPrune.contains((long) j)) {
                        partsToKeep.add(originalParts.get(j));
                    }
                }

                if (partsToKeep.size() == originalParts.size()) {
                    log.warn("None of the specified part indices {} found in message {}", partIndices, messageUID);
                    return;
                }

                log.info("Pruning {} part(s) from message {}. Reason: {}", originalParts.size() - partsToKeep.size(), messageUID, reason);

                if (partsToKeep.isEmpty()) {
                    context.remove(i);
                    log.info("Message {} became empty and was removed after pruning parts.", messageUID);
                    notifyHistoryChange();
                } else {
                    Content newContent = Content.builder()
                            .role(originalContent.role().orElse(null))
                            .parts(partsToKeep)
                            .build();

                    ChatMessage replacement = new ChatMessage(
                            message.getId(), message.getModelId(), newContent, message.getParentId(),
                            message.getUsageMetadata(), message.getGroundingMetadata(), message.getPartLinks()
                    );
                    replacement.setFunctionResponses(message.getFunctionResponses());
                    context.set(i, replacement);
                    notifyHistoryChange();
                }
                return;
            }
        }
        log.warn("Could not find message with ID {} to prune parts.", messageUID);
    }

    public synchronized void prunePartsByReference(List<Part> partsToPrune, String reason) {
        if (partsToPrune == null || partsToPrune.isEmpty()) {
            return;
        }

        Set<Part> partsToPruneSet = new HashSet<>(partsToPrune);
        boolean contextWasModified = false;

        ListIterator<ChatMessage> iterator = context.listIterator();
        while (iterator.hasNext()) {
            ChatMessage currentMessage = iterator.next();
            Content originalContent = currentMessage.getContent();
            if (originalContent == null || !originalContent.parts().isPresent()) {
                continue;
            }

            List<Part> originalParts = originalContent.parts().get();
            if (Collections.disjoint(originalParts, partsToPruneSet)) {
                continue;
            }

            List<Part> partsToKeep = originalParts.stream()
                    .filter(p -> !partsToPruneSet.contains(p))
                    .collect(Collectors.toList());

            contextWasModified = true;
            if (partsToKeep.isEmpty()) {
                iterator.remove();
                log.info("Message {} became empty and was removed after pruning parts.", currentMessage.getId());
            } else {
                Content newContent = ContentUtils.cloneAndRemoveParts(originalContent, partsToPrune);
                ChatMessage replacement = new ChatMessage(
                        currentMessage.getId(), currentMessage.getModelId(), newContent, currentMessage.getParentId(),
                        currentMessage.getUsageMetadata(), currentMessage.getGroundingMetadata(), currentMessage.getPartLinks()
                );
                replacement.setFunctionResponses(currentMessage.getFunctionResponses());
                iterator.set(replacement);
            }
        }

        if (contextWasModified) {
            log.info("Pruned {} part(s) by reference. Reason: {}", partsToPrune.size(), reason);
            notifyHistoryChange();
        }
    }

    public void notifyHistoryChange() {
        listeners.forEach(l -> l.contextChanged(chat));
    }

    public String getContextId() {
        return config.getApplicationInstanceId() + "-" + System.identityHashCode(this);
    }

    public synchronized String saveSession(String name) throws IOException {
        throw new UnsupportedOperationException("Session saving is disabled until Kryo implementation.");
    }

    public synchronized List<String> listSavedSessions() throws IOException {
        throw new UnsupportedOperationException("Session listing is disabled until Kryo implementation.");
    }

    public synchronized void loadSession(String id) throws IOException {
        throw new UnsupportedOperationException("Session loading is disabled until Kryo implementation.");
    }

    private boolean isUserMessage(ChatMessage message) {
        return message.getContent() != null && "user".equals(message.getContent().role().orElse(null));
    }

    private void pruneOldEphemeralResults() {
        if (functionManager == null) {
            return;
        }
        final int turnsToKeep = 2;
        List<Integer> userMessageIndices = new ArrayList<>();
        for (int i = context.size() - 1; i >= 0; i--) {
            if (isUserMessage(context.get(i))) {
                userMessageIndices.add(i);
            }
        }

        if (userMessageIndices.size() <= turnsToKeep) {
            return;
        }

        int pruneCutoffIndex = userMessageIndices.get(turnsToKeep);
        Set<Part> partsToPrune = new HashSet<>();

        Map<Part, Part> callToResponseLink = new HashMap<>();
        for (ChatMessage msg : context) {
            if (msg.getPartLinks() != null) {
                for (Map.Entry<Part, Part> entry : msg.getPartLinks().entrySet()) {
                    callToResponseLink.put(entry.getValue(), entry.getKey());
                }
            }
        }

        for (int i = 0; i < pruneCutoffIndex; i++) {
            ChatMessage message = context.get(i);
            if (message.getContent() == null || !message.getContent().parts().isPresent()) {
                continue;
            }

            for (Part part : message.getContent().parts().get()) {
                if (isPartEphemeral(part)) {
                    partsToPrune.add(part);
                    if (part.functionCall().isPresent()) {
                        Part responsePart = callToResponseLink.get(part);
                        if (responsePart != null) {
                            partsToPrune.add(responsePart);
                        }
                    } else if (part.functionResponse().isPresent()) {
                        Part callPart = message.getFunctionCallForResponse(part);
                        if (callPart != null) {
                            partsToPrune.add(callPart);
                        }
                    }
                }
            }
        }

        if (!partsToPrune.isEmpty()) {
            log.info("Two-Turn Rule: Pruning {} old ephemeral parts.", partsToPrune.size());
            prunePartsByReference(new ArrayList<>(partsToPrune), "Automatic pruning of old ephemeral tool calls and responses.");
        }
    }

    private boolean isPartEphemeral(Part part) {
        if (functionManager == null) {
            return false;
        }
        String toolName = "";
        if (part.functionCall().isPresent()) {
            toolName = part.functionCall().get().name().orElse("");
        } else if (part.functionResponse().isPresent()) {
            toolName = part.functionResponse().get().name().orElse("");
        }
        return !toolName.isEmpty() && functionManager.getContextBehavior(toolName) == ContextBehavior.EPHEMERAL;
    }

    public String getSummaryAsString() {
        List<ChatMessage> historyCopy = getContext();
        StringBuilder statusBlock = new StringBuilder();
        statusBlock.append("\n#  Context entries: ").append(historyCopy.size()).append("\n");
        statusBlock.append("\n-----------------------------------\n");

        for (int i = 0; i < historyCopy.size(); i++) {
            ChatMessage message = historyCopy.get(i);
            Content content = message.getContent();
            String role = content != null && content.role().isPresent() ? content.role().get() : "system";

            statusBlock.append("\n[").append(i).append("][").append(role).append("] ");
            statusBlock.append("[id: ").append(message.getId()).append("] ");

            if (content != null && content.parts().isPresent()) {
                List<Part> parts = content.parts().get();
                statusBlock.append(parts.size()).append(" Parts");
                for (int j = 0; j < parts.size(); j++) {
                    Part p = parts.get(j);
                    statusBlock.append("\n\t[").append(i).append("/").append(j).append("] ");

                    String summary;
                    if (p.functionResponse().isPresent() && functionManager != null) {
                        FunctionResponse fr = p.functionResponse().get();
                        Optional<String> resourceIdOpt = FunctionUtils.getResourceIdIfStateful(fr, functionManager);
                        if (resourceIdOpt.isPresent()) {
                            summary = "[FunctionResponse]:STATEFUL:" + resourceIdOpt.get();
                        } else {
                            summary = PartUtils.summarize(p);
                        }
                    } else {
                        summary = PartUtils.summarize(p);
                    }
                    statusBlock.append(summary);
                }
            } else {
                statusBlock.append("0 (No Parts)");
            }
        }
        statusBlock.append("\n");

        return statusBlock.toString();
    }
    
    // --- Stateful Resource Management API ---

    public List<StatefulResourceStatus> getStatefulResourcesOverview(FunctionManager fm) {
        List<StatefulResourceStatus> statuses = new ArrayList<>();

        for (ChatMessage message : context) {
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

    private Optional<StatefulResourceStatus> getResourceStatus(FunctionResponse fr, FunctionManager fm) {
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
}
