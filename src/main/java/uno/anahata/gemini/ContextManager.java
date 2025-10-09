package uno.anahata.gemini;

import com.google.genai.types.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import uno.anahata.gemini.functions.ContextBehavior;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.ContentUtils;
import uno.anahata.gemini.internal.FunctionUtils;
import uno.anahata.gemini.internal.FunctionUtils.Fingerprint;
import uno.anahata.gemini.internal.PartUtils;

public class ContextManager {

    private static final Logger logger = Logger.getLogger(ContextManager.class.getName());

    private List<ChatMessage> context = new ArrayList<>();
    private final GeminiConfig config;
    private final ContextListener listener;
    private FunctionManager functionManager;
    private int totalTokenCount = 0;

    public ContextManager(GeminiConfig config, ContextListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    public static ContextManager get() {
        return GeminiChat.get().getContextManager();
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

        listener.contentAdded(message);
    }

    private void handleStatefulReplace(ChatMessage newMessage) {
        List<String> newResourceIds = newMessage.getContent().parts().orElse(Collections.emptyList()).stream()
            .filter(part -> part.functionResponse().isPresent())
            .map(part -> FunctionUtils.getResourceIdIfStateful(part.functionResponse().get(), functionManager))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());

        if (newResourceIds.isEmpty()) {
            return;
        }

        Set<Fingerprint> staleFingerprints = new HashSet<>();
        for (int i = 0; i < context.size(); i++) {
            ChatMessage currentMessage = context.get(i);
            if (!"tool".equals(currentMessage.getContent().role().orElse(""))) {
                continue;
            }

            for (Part part : currentMessage.getContent().parts().orElse(Collections.emptyList())) {
                if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    Optional<String> resourceIdOpt = FunctionUtils.getResourceIdIfStateful(fr, functionManager);

                    if (resourceIdOpt.isPresent() && newResourceIds.contains(resourceIdOpt.get())) {
                        //findAndFingerprintStaleCall(i - 1, fr, staleFingerprints);
                    }
                }
            }
        }

        if (staleFingerprints.isEmpty()) {
            return;
        }

        List<ChatMessage> newContext = new ArrayList<>();
        boolean contextWasModified = false;

        for (ChatMessage message : context) {
            List<Part> partsToRemove = new ArrayList<>();
            Content currentContent = message.getContent();
            
            for (Part part : currentContent.parts().orElse(Collections.emptyList())) {
                if (part.functionCall().isPresent() && staleFingerprints.contains(FunctionUtils.fingerprintOf(part.functionCall().get()))) {
                    partsToRemove.add(part);
                } else if (part.functionResponse().isPresent()) {
                    Optional<Fingerprint> callFingerprint = findCallFingerprintForResponse(message, part.functionResponse().get());
                    if (callFingerprint.isPresent() && staleFingerprints.contains(callFingerprint.get())) {
                        partsToRemove.add(part);
                    }
                }
            }

            if (partsToRemove.isEmpty()) {
                newContext.add(message);
            } else {
                contextWasModified = true;
                Content newContent = ContentUtils.cloneAndRemoveParts(currentContent, partsToRemove);
                if (newContent.parts().isPresent() && !newContent.parts().get().isEmpty()) {
                    ChatMessage replacement = new ChatMessage(
                        message.getId(), message.getModelId(), newContent, message.getParentId(),
                        message.getUsageMetadata(), message.getGroundingMetadata()
                    );
                    replacement.setFunctionResponses(message.getFunctionResponses());
                    newContext.add(replacement);
                    logger.log(Level.INFO, "Pruned {0} stale part(s) from message {1}", new Object[]{partsToRemove.size(), message.getId()});
                } else {
                    logger.log(Level.INFO, "Removed empty message {0} after pruning all its parts.", message.getId());
                }
            }
        }

        if (contextWasModified) {
            this.context = newContext;
            notifyHistoryChange();
        }
    }
    
    private Optional<Fingerprint> findCallFingerprintForResponse(ChatMessage toolMessage, FunctionResponse response) {
        int toolMessageIndex = context.indexOf(toolMessage);
        if (toolMessageIndex <= 0) return Optional.empty();
        
        ChatMessage modelMessage = context.get(toolMessageIndex - 1);
        if (!"model".equals(modelMessage.getContent().role().orElse(""))) return Optional.empty();

        return modelMessage.getContent().parts().orElse(Collections.emptyList()).stream()
            .filter(p -> p.functionCall().isPresent())
            .map(p -> p.functionCall().get())
            .filter(fc -> fc.name().orElse("").equals(response.name().orElse("")))
            .findFirst()
            .map(FunctionUtils::fingerprintOf);
    }

    private void findAndFingerprintStaleCall(int modelMessageIndex, FunctionResponse staleResponse, Set<Fingerprint> staleFingerprints) {
        if (modelMessageIndex < 0) {
            logger.log(Level.WARNING, "Found a stale FunctionResponse for tool ''{0}'' but it has no preceding message.", staleResponse.name());
            return;
        }

        ChatMessage modelMessage = context.get(modelMessageIndex);
        if (!"model".equals(modelMessage.getContent().role().orElse(""))) {
            logger.log(Level.WARNING, "Found a stale FunctionResponse for tool ''{0}'' but the preceding message is not from the model (role={1}).", new Object[]{staleResponse.name(), modelMessage.getContent().role().orElse("null")});
            return;
        }

        Optional<FunctionCall> matchingCall = modelMessage.getContent().parts().orElse(Collections.emptyList()).stream()
            .filter(p -> p.functionCall().isPresent())
            .map(p -> p.functionCall().get())
            .filter(fc -> fc.name().orElse("").equals(staleResponse.name().orElse("")))
            .findFirst();

        if (matchingCall.isPresent()) {
            staleFingerprints.add(FunctionUtils.fingerprintOf(matchingCall.get()));
        } else {
            logger.log(Level.WARNING, "Found a stale FunctionResponse for tool ''{0}'' but could not find the matching FunctionCall in the preceding model message (id={1}). The response will be pruned, but the call may remain.", new Object[]{staleResponse.name(), modelMessage.getId()});
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
            if (content == null) return;

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
            logger.log(Level.SEVERE, "Failed to write history content to log file", e);
        }
    }

    public synchronized void clear() {
        context.clear();
        totalTokenCount = 0;
        listener.contextCleared();
        logger.info("Chat history cleared.");
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
        logger.info("Context set. New token count: " + this.totalTokenCount);
        notifyHistoryChange();
    }

    public synchronized void pruneMessages(List<String> uids, String reason) {
        boolean removed = context.removeIf(message -> uids.contains(message.getId()));
        if (removed) {
            logger.log(Level.INFO, "Pruned {0} message(s). Reason: {1}", new Object[]{uids.size(), reason});
            notifyHistoryChange();
        }
    }
    
    public synchronized void pruneParts(String messageUID, List<Integer> partIndices, String reason) {
        for (int i = 0; i < context.size(); i++) {
            ChatMessage message = context.get(i);
            if (message.getId().equals(messageUID)) {
                Content originalContent = message.getContent();
                if (originalContent == null || !originalContent.parts().isPresent()) {
                    logger.log(Level.WARNING, "Message {0} has no parts to prune.", messageUID);
                    return;
                }

                List<Part> originalParts = originalContent.parts().get();
                List<Part> partsToKeep = new ArrayList<>();
                for (int j = 0; j < originalParts.size(); j++) {
                    if (!partIndices.contains(j)) {
                        partsToKeep.add(originalParts.get(j));
                    }
                }

                if (partsToKeep.size() == originalParts.size()) {
                     logger.log(Level.WARNING, "None of the specified part indices {0} found in message {1}", new Object[]{partIndices, messageUID});
                     return;
                }

                logger.log(Level.INFO, "Pruning {0} part(s) from message {1}. Reason: {2}", new Object[]{originalParts.size() - partsToKeep.size(), messageUID, reason});

                if (partsToKeep.isEmpty()) {
                    pruneMessages(Collections.singletonList(messageUID), "Message became empty after pruning parts. Original reason: " + reason);
                } else {
                    Content newContent = Content.builder()
                        .role(originalContent.role().orElse(null))
                        .parts(partsToKeep)
                        .build();
                    
                    ChatMessage replacement = new ChatMessage(
                        message.getId(), message.getModelId(), newContent, message.getParentId(),
                        message.getUsageMetadata(), message.getGroundingMetadata()
                    );
                    replacement.setFunctionResponses(message.getFunctionResponses());
                    context.set(i, replacement);
                    notifyHistoryChange();
                }
                return;
            }
        }
        logger.log(Level.WARNING, "Could not find message with ID {0} to prune parts.", messageUID);
    }

    public void notifyHistoryChange() {
        listener.contextModified();
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
        if (functionManager == null) return;
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

        List<String> idsToPrune = new ArrayList<>();
        for (int i = 0; i < pruneCutoffIndex; i++) {
            ChatMessage message = context.get(i);
            if (isEphemeralToolMessage(message)) {
                idsToPrune.add(message.getId());
            }
        }

        if (!idsToPrune.isEmpty()) {
            logger.log(Level.INFO, "Two-Turn Rule: Pruning {0} old ephemeral messages.", idsToPrune.size());
            pruneMessages(idsToPrune, "Automatic pruning of old ephemeral tool calls.");
        }
    }

    private boolean isEphemeralToolMessage(ChatMessage message) {
        if (functionManager == null || message.getContent() == null || !message.getContent().parts().isPresent()) {
            return false;
        }
        for (Part part : message.getContent().parts().get()) {
            String toolName = "";
            if (part.functionCall().isPresent()) {
                toolName = part.functionCall().get().name().orElse("");
            } else if (part.functionResponse().isPresent()) {
                toolName = part.functionResponse().get().name().orElse("");
            }
            
            if (!toolName.isEmpty() && functionManager.getContextBehavior(toolName) == ContextBehavior.EPHEMERAL) {
                return true;
            }
        }
        return false;
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
                    statusBlock.append(PartUtils.summarize(p));
                }
            } else {
                statusBlock.append("0 (No Parts)");
            }
        }
        statusBlock.append("\n");

        return statusBlock.toString();
    }
}
