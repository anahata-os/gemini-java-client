package uno.anahata.gemini;

import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.blob.PartUtils;
import uno.anahata.gemini.internal.GsonUtils;

public class ContextManager {

    private static final Logger logger = Logger.getLogger(ContextManager.class.getName());
    private static final Gson GSON = GsonUtils.getGson();

    private List<Content> context = new ArrayList<>();
    private final GeminiConfig config;
    private final ContextListener listener;
    private int totalTokenCount = 0;

    public ContextManager(GeminiConfig config, ContextListener listener) {
        this.config = config;
        this.listener = listener;
        //restoreSession();
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

    public synchronized void add(GenerateContentResponseUsageMetadata usage, Content c) {
        if (c != null) {
            context.add(c);
            logEntryToFile(c);
            backupSession();
        }
        if (usage != null) {
            usage.totalTokenCount().ifPresent(count -> this.totalTokenCount = count);
        }
        listener.contentAdded(usage, c);
    }

    public synchronized void addAll(List<Content> contents) {
        for (Content content : contents) {
            add(null, content);
        }
    }

    private void logEntryToFile(Content content) {
        try {
            File historyDir = GeminiConfig.getWorkingFolder("history");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String role = content.role().orElse("unknown");
            String modelId = config.getApi().getModelId().replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = String.format("%s-%s-%s-%s.log", timestamp, role, modelId, getContextId());
            Path logFilePath = historyDir.toPath().resolve(filename);

            StringBuilder logContent = new StringBuilder();
            logContent.append("--- HEADER ---\n");
            logContent.append("Context ID: ").append(getContextId()).append("\n");
            logContent.append("Timestamp: ").append(timestamp).append("\n");
            logContent.append("Role: ").append(role).append("\n");
            logContent.append("Model ID: ").append(config.getApi().getModelId()).append("\n");
            logContent.append("--- PARTS ---\n");

            if (content.parts().isPresent()) {
                for (Part part : content.parts().get()) {
                    if (part.text().isPresent()) {
                        logContent.append("[Text]:\n").append(part.text().get()).append("\n");
                    } else if (part.functionCall().isPresent()) {
                        FunctionCall fc = part.functionCall().get();
                        logContent.append("[FunctionCall]: ").append(fc.name().get()).append("\n");
                        if (fc.args().isPresent()) {
                            for (Map.Entry<String, Object> entry : fc.args().get().entrySet()) {
                                String argValue = String.valueOf(entry.getValue());
                                if (argValue.length() > 200) {
                                    logContent.append("  - ").append(entry.getKey()).append(": (bytes: ").append(argValue.getBytes(StandardCharsets.UTF_8).length).append(")\n");
                                } else {
                                    logContent.append("  - ").append(entry.getKey()).append(": ").append(argValue).append("\n");
                                }
                            }
                        }
                    } else if (part.functionResponse().isPresent()) {
                        FunctionResponse fr = part.functionResponse().get();
                        logContent.append("[FunctionResponse]: ").append(fr.name().get()).append("\n");
                        if (fr.response().isPresent()) {
                            String responseValue = String.valueOf(fr.response().get());
                            if (responseValue.length() > 200) {
                                logContent.append("  - response: (bytes: ").append(responseValue.getBytes(StandardCharsets.UTF_8).length).append(")\n");
                            } else {
                                logContent.append("  - response: ").append(responseValue).append("\n");
                            }
                        }
                    } else if (part.inlineData().isPresent()) {
                        logContent.append("[Blob]: ").append(PartUtils.toString(part.inlineData().get())).append("\n");
                    } else {
                        logContent.append("[Unknown Part Type]\n");
                    }
                    logContent.append("---\n");
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

    public synchronized List<Content> getContext() {
        return new ArrayList<>(context);
    }

    public synchronized void setContext(List<Content> newContext) {
        this.context = new ArrayList<>(newContext);
        this.totalTokenCount = this.context.stream().mapToInt(c -> c.toString().length() / 4).sum();
        logger.info("Estimated token count after setContext: " + this.totalTokenCount);
    }

    public void notifyHistoryChange() {
        listener.contextModified();
    }

    public String getSummaryAsString() {
        List<Content> historyCopy = getContext();
        StringBuilder statusBlock = new StringBuilder();
        statusBlock.append("\n#  Context entries: ").append(historyCopy.size()).append("\n");
        statusBlock.append("\n-----------------------------------\n");
        int contentIdx = 0;
        for (Content content : historyCopy) {
            int partIdx = 0;
            statusBlock.append("\n[").append(contentIdx).append("][").append(content.role().get()).append("]");
            if (content.parts().isPresent()) {
                List<Part> parts = content.parts().get();
                statusBlock.append(parts.size()).append(" Parts");

                for (Part p : content.parts().get()) {
                    statusBlock.append("\n\t[").append(contentIdx).append("/").append(partIdx).append("][");
                    if (p.text().isPresent()) {
                        statusBlock.append("Text]:").append(StringUtils.truncate(p.text().get(), 200));
                    } else if (p.functionCall().isPresent()) {
                        FunctionCall fc = p.functionCall().get();
                        String argsTruncated = StringUtils.truncate(fc.args().toString(), 100);
                        statusBlock.append("FunctionCall]:").append(fc.name().get()).append(":").append(argsTruncated);
                    } else if (p.functionResponse().isPresent()) {
                        FunctionResponse fr = p.functionResponse().get();
                        String responseTruncated = StringUtils.truncate(fr.response().toString(), 100);
                        statusBlock.append("FunctionResponse]:").append(fr.name().get()).append(":").append(responseTruncated);
                    } else if (p.inlineData().isPresent()) {
                        Blob b = p.inlineData().get();
                        statusBlock.append("Blob]:").append(PartUtils.toString(b));
                    } else {
                        statusBlock.append("Unknown part type]:").append(p);
                    }
                    partIdx++;
                }
            } else {
                statusBlock.append("0 (No Parts)");
            }
            contentIdx++;
        }
        statusBlock.append("\n");

        return statusBlock.toString();
    }
    
    public String getContextId() {
        return config.getApplicationInstanceId() + "-" + System.identityHashCode(this);
    }

    public synchronized String saveSession(String name) throws IOException {
        File sessionsDir = GeminiConfig.getWorkingFolder("sessions");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = String.format("session-%s-%s.json", name, timestamp);
        Path sessionPath = sessionsDir.toPath().resolve(filename);

        String json = GSON.toJson(context);
        Files.writeString(sessionPath, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        logger.log(Level.INFO, "Session saved to {0}", sessionPath);
        return StringUtils.removeEnd(filename, ".json");
    }

    public synchronized List<String> listSavedSessions() throws IOException {
        File sessionsDir = GeminiConfig.getWorkingFolder("sessions");
        if (!sessionsDir.exists()) {
            return Collections.emptyList();
        }
        return Arrays.stream(sessionsDir.listFiles((dir, name) -> name.endsWith(".json")))
                .map(file -> StringUtils.removeEnd(file.getName(), ".json"))
                .collect(Collectors.toList());
    }

    public synchronized void loadSession(String id) throws IOException {
        File sessionsDir = GeminiConfig.getWorkingFolder("sessions");
        Path sessionPath = sessionsDir.toPath().resolve(id + ".json");
        if (!Files.exists(sessionPath)) {
            throw new IOException("Session file not found: " + id);
        }

        String json = Files.readString(sessionPath, StandardCharsets.UTF_8);
        Type listType = new TypeToken<ArrayList<Content>>() {}.getType();
        List<Content> loadedContext = GSON.fromJson(json, listType);

        setContext(loadedContext);
        notifyHistoryChange();
        logger.log(Level.INFO, "Session loaded from {0}", sessionPath);
    }

    private Path getAutoBackupPath() throws IOException {
        File workDir = GeminiConfig.getWorkingFolder("sessions");
        String filename = "autobackup-" + getContextId() + ".json";
        return workDir.toPath().resolve(filename);
    }
    
    private void backupSession() {
        try {
            Path backupPath = getAutoBackupPath();
            String json = GSON.toJson(context);
            Files.writeString(backupPath, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create automatic session backup", e);
        }
    }
    /*
    private void restoreSession() {
        try {
            Path backupPath = getAutoBackupPath();
            if (Files.exists(backupPath)) {
                String json = Files.readString(backupPath, StandardCharsets.UTF_8);
                Type listType = new TypeToken<ArrayList<Content>>() {}.getType();
                List<Content> restoredContext = GSON.fromJson(json, listType);
                setContext(restoredContext);
                logger.log(Level.INFO, "Session restored from automatic backup: {0}", backupPath.getFileName());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to restore session from automatic backup", e);
        }
    }*/
}
