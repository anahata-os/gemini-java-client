package uno.anahata.gemini.context.history;

import uno.anahata.gemini.ChatMessage;
import com.google.genai.types.Content;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.internal.PartUtils;

@Slf4j
public class HistoryLogger {

    private final ContextManager contextManager;

    public HistoryLogger(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void logEntryToFile(ChatMessage message) {
        uno.anahata.gemini.Executors.cachedThreadPool.submit(() -> {
            try {
                Content content = message.getContent();
                if (content == null) {
                    return;
                }

                Path historyDir = contextManager.getConfig().getWorkingFolder("history").toPath();
                Files.createDirectories(historyDir);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
                String role = content.role().orElse("unknown");
                String modelId = message.getModelId() != null ? message.getModelId().replaceAll("[^a-zA-Z0-9.-]", "_") : "unknown_model";
                String filename = String.format("%s-%s-%s-%s.log", timestamp, role, modelId, contextManager.getContextId());
                Path logFilePath = historyDir.resolve(filename);

                String summary = contextManager.getSessionManager().summarizeMessage(message);
                Files.writeString(logFilePath, summary, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);

            } catch (Exception e) {
                log.error("Failed to write history content to log file", e);
            }
        });
    }
}
