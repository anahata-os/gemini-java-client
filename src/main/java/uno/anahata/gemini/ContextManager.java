package uno.anahata.gemini;

import com.google.genai.types.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import uno.anahata.gemini.internal.PartUtils;

/**
 * V3: A stateful context manager using a rich ChatMessage model.
 * It handles intelligent pruning, workspace state management, and session persistence.
 * @author Anahata
 */
public class ContextManager {

    private static final Logger logger = Logger.getLogger(ContextManager.class.getName());

    private List<ChatMessage> context = new ArrayList<>();
    private final GeminiConfig config;
    private final ContextListener listener;
    private int totalTokenCount = 0;

    public ContextManager(GeminiConfig config, ContextListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public static ContextManager get() {
        return GeminiChat.get().getContextManager();
    }

    public GeminiConfig getConfig() {
        return config;
    }

    public synchronized int getTotalTokenCount() {
        return totalTokenCount;
    }

    public synchronized void add(ChatMessage message) {
        handleStatefulReplace(message);
        context.add(message);
        logEntryToFile(message);
        
        if (message.getUsageMetadata() != null) {
            this.totalTokenCount = message.getUsageMetadata().totalTokenCount().orElse(this.totalTokenCount);
        }
        listener.contentAdded(message);
    }
    
    private void handleStatefulReplace(ChatMessage newMessage) {
        extractResourceIdentifier(newMessage).ifPresent(newResourceId -> {
            Iterator<ChatMessage> iterator = context.iterator();
            while (iterator.hasNext()) {
                ChatMessage oldMessage = iterator.next();
                extractResourceIdentifier(oldMessage).ifPresent(oldResourceId -> {
                    if (newResourceId.equals(oldResourceId)) {
                        logger.log(Level.INFO, "STATEFUL_REPLACE: Pruning old message {0} for resource {1}", new Object[]{oldMessage.getId(), newResourceId});
                        iterator.remove();
                    }
                });
            }
        });
    }

    private Optional<String> extractResourceIdentifier(ChatMessage message) {
        if (message.getContent() == null || !message.getContent().parts().isPresent()) {
            return Optional.empty();
        }
        for (Part part : message.getContent().parts().get()) {
            if (part.functionResponse().isPresent()) {
                FunctionResponse fr = part.functionResponse().get();
                String toolName = fr.name().orElse("");

                if (toolName.equals("LocalFiles.readFile") || toolName.equals("LocalFiles.writeFile")) {
                    if (fr.response().isPresent() && fr.response().get() instanceof Map) {
                        Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
                        Object path = responseMap.get("path");
                        if (path instanceof String) {
                            return Optional.of((String) path);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void logEntryToFile(ChatMessage message) {
        try {
            Content content = message.getContent();
            if (content == null) return;

            File historyDir = GeminiConfig.getWorkingFolder("history");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String role = content.role().orElse("unknown");
            String modelId = message.getModelId() != null ? message.getModelId().replaceAll("[^a-zA-Z0-9.-]", "_") : "unknown_model";
            String filename = String.format("%s-%s-%s-%s.log", timestamp, role, modelId, getContextId());
            Path logFilePath = historyDir.toPath().resolve(filename);

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
                    return cm.getContent().toString().length() / 4; 
                }
                return 0;
            })
            .sum();
        logger.info("Context set. New token count: " + this.totalTokenCount);
        notifyHistoryChange();
    }
    
    public synchronized void pruneById(String id) {
        boolean removed = context.removeIf(message -> message.getId().equals(id));
        if (removed) {
            logger.info("Pruned message with ID: " + id);
            notifyHistoryChange();
        }
    }

    public void notifyHistoryChange() {
        listener.contextModified();
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
}
