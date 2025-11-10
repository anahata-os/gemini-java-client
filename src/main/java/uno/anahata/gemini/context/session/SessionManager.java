package uno.anahata.gemini.context.session;

import uno.anahata.gemini.ChatMessage;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.internal.KryoUtils;
import com.google.genai.types.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.gemini.context.stateful.ResourceTracker;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.PartUtils;

@Slf4j
public class SessionManager {

    private static final Gson GSON = GsonUtils.getGson();
    private final ContextManager contextManager;

    public SessionManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public String saveSession(String name) throws IOException {
        Path sessionsDir = contextManager.getConfig().getWorkingFolder("sessions").toPath();
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve(name + ".kryo");
        byte[] bytes = KryoUtils.serialize(contextManager.getContext());
        Files.write(sessionFile, bytes);
        log.info("Session '{}' saved successfully to {}", name, sessionFile);
        return "Session saved as " + name;
    }

    public List<String> listSavedSessions() throws IOException {
        Path sessionsDir = contextManager.getConfig().getWorkingFolder("sessions").toPath();
        if (!Files.exists(sessionsDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".kryo"))
                    .map(p -> p.getFileName().toString().replace(".kryo", ""))
                    .collect(Collectors.toList());
        }
    }

    public void loadSession(String id) throws IOException {
        Path sessionsDir = contextManager.getConfig().getWorkingFolder("sessions").toPath();
        Path sessionFile = sessionsDir.resolve(id + ".kryo");
        if (!Files.exists(sessionFile)) {
            throw new IOException("Session file not found: " + sessionFile);
        }
        byte[] bytes = Files.readAllBytes(sessionFile);
        List<ChatMessage> loadedContext = KryoUtils.deserialize(bytes, ArrayList.class);
        contextManager.setContext(loadedContext);
        log.info("Session '{}' loaded successfully.", id);
    }

    public void triggerAutobackup() {
        final List<ChatMessage> contextCopy = contextManager.getContext();
        final File autobackupFile = contextManager.getConfig().getAutobackupFile();

        uno.anahata.gemini.Executors.cachedThreadPool.submit(() -> {
            try {
                if (contextCopy.isEmpty()) {
                    if (Files.exists(autobackupFile.toPath())) {
                        Files.delete(autobackupFile.toPath());
                        log.info("Autobackup: Context is empty, deleted autobackup file.");
                    }
                    return;
                }

                byte[] bytes = KryoUtils.serialize(contextCopy);
                Files.write(autobackupFile.toPath(), bytes);
                log.debug("Autobackup: Session automatically saved to {}", autobackupFile.getName());
            } catch (Exception e) {
                log.error("Autobackup: Failed to save session automatically.", e);
            }
        });
    }

    public String getSummaryAsString() {
        List<ChatMessage> historyCopy = contextManager.getContext();
        StringBuilder sb = new StringBuilder();
        sb.append("\n# Context Entries: ").append(historyCopy.size()).append("\n");
        sb.append("| Nº | UUID | Created On | Name | Role | Elapsed | Content Summary |\n");
        sb.append("|---:|:---|:---|:---|:---|---:|:---|\n");

        Instant previousTimestamp = null;

        for (int i = 0; i < historyCopy.size(); i++) {
            ChatMessage msg = historyCopy.get(i);
            sb.append(summarizeMessage(msg, i, previousTimestamp));
            previousTimestamp = msg.getCreatedOn();
        }
        return sb.toString();
    }

    public String summarizeMessage(ChatMessage msg) {
        return summarizeMessage(msg, -1, null);
    }

    private String summarizeMessage(ChatMessage msg, int index, Instant previousTimestamp) {
        StringBuilder sb = new StringBuilder();
        Content content = msg.getContent();
        String role = content != null ? content.role().orElse("system") : "system";
        String name;

        switch (role) {
            case "model":
                name = msg.getModelId();
                break;
            case "tool":
                name = contextManager.getConfig().getSessionId();
                break;
            case "user":
            default:
                name = System.getProperty("user.name");
                break;
        }

        String createdOn = DateTimeFormatter.ISO_INSTANT.format(msg.getCreatedOn()).replace("T", " ").substring(0, 19);

        String elapsedStr = "N/A";
        if (previousTimestamp != null) {
            long elapsedMillis = Duration.between(previousTimestamp, msg.getCreatedOn()).toMillis();
            elapsedStr = formatDuration(elapsedMillis);
        }
        
        String indexStr = (index == -1) ? "" : String.valueOf(index);

        if (content != null && content.parts().isPresent()) {
            List<Part> parts = content.parts().get();
            if (parts.isEmpty()) {
                sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n", indexStr, msg.getId(), createdOn, name, role, elapsedStr, "(No Parts)"));
            } else {
                for (int j = 0; j < parts.size(); j++) {
                    String prefix = String.format("[%d/%d] ", j, parts.size());
                    String summary = prefix + summarizePart(parts.get(j));
                    if (j == 0) {
                        sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n", indexStr, msg.getId(), createdOn, name, role, elapsedStr, summary));
                    } else {
                        sb.append(String.format("| | | | | | | %s |\n", summary));
                    }
                }
            }
        } else {
            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n", indexStr, msg.getId(), createdOn, name, role, elapsedStr, "(No Content)"));
        }
        return sb.toString();
    }

    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        if (millis < 60000) {
            return String.format("%.2fs", millis / 1000.0);
        }
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        return String.format("%dm %ds", minutes, seconds);
    }

    private String summarizePart(Part p) {
        final int MAX_LENGTH = 128;
        StringBuilder sb = new StringBuilder();
        FunctionManager fm = contextManager.getFunctionManager();

        if (p.text().isPresent()) {
            sb.append("[Text]:").append(p.text().get());
        } else if (p.functionCall().isPresent()) {
            FunctionCall fc = p.functionCall().get();
            sb.append("[FunctionCall]:").append(fc.name().get());

            if (fc.args().isPresent()) {
                Map<String, Object> args = fc.args().get();
                String argsString = args.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + StringUtils.truncate(String.valueOf(entry.getValue()), MAX_LENGTH / 2))
                        .collect(Collectors.joining(", ", "(", ")"));
                sb.append(argsString);
            }

        } else if (p.functionResponse().isPresent()) {
            FunctionResponse fr = p.functionResponse().get();
            String toolName = fr.name().orElse("unknown");
            sb.append("[FunctionResponse][").append(toolName).append("]");
            
            Optional<String> resourceIdOpt = ResourceTracker.getResourceIdIfStateful(fr, fm);
            if (resourceIdOpt.isPresent()) {
                sb.append(":STATEFUL:").append(resourceIdOpt.get());
            } else {
                sb.append(": ").append(fr.response().get().toString());
            }
        } else if (p.inlineData().isPresent()) {
            sb.append("[Blob]:").append(PartUtils.toString(p.inlineData().get()));
        } else if (p.codeExecutionResult().isPresent()) {
            CodeExecutionResult cer = p.codeExecutionResult().get();
            sb.append("[CodeExecutionResult]:").append(cer.outcome().get()).append(":").append(cer.output().get());
        } else if (p.executableCode().isPresent()) {
            ExecutableCode ec = p.executableCode().get();
            sb.append("[ExecutableCode]:").append(ec.code().get());
        } else {
            sb.append("[Unknown part type]:").append(p.toString());
        }

        // Sanitize and truncate the final string
        String finalString = sb.toString().replace("\n", "\\n").replace("\r", "").replace("|", "\\|");
        return StringUtils.truncate(finalString, MAX_LENGTH);
    }
}
