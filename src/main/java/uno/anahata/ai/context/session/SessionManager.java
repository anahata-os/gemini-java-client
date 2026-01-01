/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.session;

import com.google.genai.types.*;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.internal.KryoUtils;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.internal.TextUtils;
import uno.anahata.ai.swing.TimeUtils;

/**
 * Manages the persistence and summarization of chat sessions.
 * <p>
 * This class provides functionality to:
 * <ul>
 *   <li>Save and load conversation history using Kryo serialization.</li>
 *   <li>List available saved sessions.</li>
 *   <li>Perform automatic background backups of the current session.</li>
 *   <li>Generate a human-readable Markdown summary of the conversation context.</li>
 * </ul>
 * </p>
 */
@Slf4j
public class SessionManager {

    private static final Gson GSON = GsonUtils.getGson();
    private final ContextManager contextManager;

    /**
     * Constructs a new SessionManager for the given ContextManager.
     *
     * @param contextManager The ContextManager to manage sessions for.
     */
    public SessionManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Saves the current conversation history to a file.
     *
     * @param name The name of the session (used as the filename).
     * @return A success message.
     * @throws IOException if an I/O error occurs during saving.
     */
    public String saveSession(String name) throws IOException {
        Path sessionsDir = contextManager.getConfig().getWorkingFolder("sessions").toPath();
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve(name + ".kryo");
        byte[] bytes = KryoUtils.serialize(contextManager.getContext());
        Files.write(sessionFile, bytes);
        log.info("Session '{}' saved successfully to {}", name, sessionFile);
        return "Session saved as " + name;
    }

    /**
     * Lists the names of all saved sessions in the sessions directory.
     *
     * @return A list of session names.
     * @throws IOException if an I/O error occurs while listing files.
     */
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

    /**
     * Loads a conversation history from a saved session file.
     * <p>
     * This method restores the context and resets the message and tool call ID counters
     * to ensure consistency in the resumed session.
     * </p>
     *
     * @param id The name of the session to load.
     * @throws IOException if the session file is not found or an error occurs during loading.
     */
    public void loadSession(String id) throws IOException {
        Path sessionsDir = contextManager.getConfig().getWorkingFolder("sessions").toPath();
        Path sessionFile = sessionsDir.resolve(id + ".kryo");
        if (!Files.exists(sessionFile)) {
            throw new IOException("Session file not found: " + sessionFile);
        }
        byte[] bytes = Files.readAllBytes(sessionFile);
        List<ChatMessage> loadedContext = KryoUtils.deserialize(bytes, ArrayList.class);
        contextManager.setContext(loadedContext);

        // Reset counters to avoid ID collisions
        if (!loadedContext.isEmpty()) {
            long highestMessageId = loadedContext.stream()
                .mapToLong(ChatMessage::getSequentialId)
                .max()
                .orElse(0L);
            contextManager.getChat().resetMessageCounter(highestMessageId + 1);

            int highestToolId = loadedContext.stream()
                .filter(msg -> msg.getContent() != null && msg.getContent().parts().isPresent())
                .flatMap(msg -> msg.getContent().parts().get().stream())
                .flatMap(part -> {
                    Stream<String> fcIds = part.functionCall().flatMap(FunctionCall::id).stream();
                    Stream<String> frIds = part.functionResponse().flatMap(FunctionResponse::id).stream();
                    return Stream.concat(fcIds, frIds);
                })
                .filter(StringUtils::isNumeric)
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
            contextManager.getChat().getToolManager().resetIdCounter(highestToolId + 1);
        }
        
        log.info("Session '{}' loaded successfully and counters have been reset.", id);
    }

    /**
     * Triggers an asynchronous automatic backup of the current session.
     */
    public void triggerAutobackup() {
        final List<ChatMessage> contextCopy = contextManager.getContext();
        final File autobackupFile = contextManager.getConfig().getAutobackupFile();

        uno.anahata.ai.Executors.cachedThreadPool.submit(() -> {
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

    /**
     * Generates a human-readable Markdown table summarizing the entire conversation context.
     *
     * @return A Markdown string containing the context summary.
     */
    public String getSummaryAsString() {
        List<ChatMessage> historyCopy = contextManager.getContext();
        StringBuilder sb = new StringBuilder();
        sb.append("\n# Context Entries: ").append(historyCopy.size()).append("\n");
        sb.append("| Message ID | Created On | Name | Role | Elapsed | Part Nº | Part Type | Dependencies | Content | Part Size | Tokens (≈) |\n");
        sb.append("|---:|:---|:---|:---|---:|:---|:---|:---|:---|---:|---:|\n");

        Instant previousTimestamp = null;

        for (ChatMessage msg : historyCopy) {
            sb.append(summarizeMessage(msg, previousTimestamp));
            previousTimestamp = msg.getCreatedOn();
        }
        return sb.toString();
    }

    /**
     * Summarizes a single ChatMessage into Markdown table rows.
     *
     * @param msg The message to summarize.
     * @return A Markdown string representing the message's rows in the summary table.
     */
    public String summarizeMessage(ChatMessage msg) {
        return summarizeMessage(msg, null);
    }

    private String summarizeMessage(ChatMessage msg, Instant previousTimestamp) {
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

        String createdOn = TimeUtils.formatSmartTimestamp(msg.getCreatedOn());
        String elapsedStr = TimeUtils.getElapsedString(previousTimestamp, msg.getCreatedOn());
        String seqIdStr = String.valueOf(msg.getSequentialId());

        if (content != null && content.parts().isPresent()) {
            List<Part> parts = content.parts().get();
            if (parts.isEmpty()) {
                sb.append(String.format("| %s | %s | %s | %s | %s | | | | (No Parts) | | |\n", seqIdStr, createdOn, name, role, elapsedStr));
            } else {
                for (int j = 0; j < parts.size(); j++) {
                    Part part = parts.get(j);
                    String partNo = String.valueOf(j);
                    long partSize = PartUtils.calculateSizeInBytes(part);
                    int approxTokens = PartUtils.calculateApproxTokenSize(part);
                    String[] summary = describePart(part); // Returns [type, content]
                    String dependencies = summarizeDependencies(part);

                    if (j == 0) {
                        sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %d | %d |\n", seqIdStr, createdOn, name, role, elapsedStr, partNo, summary[0], dependencies, summary[1], partSize, approxTokens));
                    } else {
                        sb.append(String.format("| | | | | | %s | %s | %s | %s | %d | %d |\n", partNo, summary[0], dependencies, summary[1], partSize, approxTokens));
                    }
                }
            }
        } else {
            sb.append(String.format("| %s | %s | %s | %s | %s | | | | (No Content) | | |\n", seqIdStr, createdOn, name, role, elapsedStr));
        }
        return sb.toString();
    }

    private String summarizeDependencies(Part part) {
        List<String> dependencySummaries = new ArrayList<>();
        List<ChatMessage> context = contextManager.getContext();

        for (ChatMessage scanMsg : context) {
            if (scanMsg.getDependencies() == null) {
                continue;
            }

            for (Map.Entry<Part, List<Part>> entry : scanMsg.getDependencies().entrySet()) {
                Part sourcePart = entry.getKey();
                List<Part> dependentParts = entry.getValue();

                // Forward dependency: This part is the source
                if (sourcePart == part) {
                    for (Part dependentPart : dependentParts) {
                        contextManager.getChatMessageForPart(dependentPart).ifPresent(depMsg -> {
                            int partIndex = depMsg.getContent().parts().get().indexOf(dependentPart);
                            String[] summary = describePart(dependentPart);
                            dependencySummaries.add(String.format("%d/%d (%s)", depMsg.getSequentialId(), partIndex, summary[0]));
                        });
                    }
                }

                // Reverse dependency: This part is a dependent
                if (dependentParts.contains(part)) {
                    contextManager.getChatMessageForPart(sourcePart).ifPresent(srcMsg -> {
                        int partIndex = srcMsg.getContent().parts().get().indexOf(sourcePart);
                        String[] summary = describePart(sourcePart);
                        dependencySummaries.add(String.format("%d/%d (%s)", srcMsg.getSequentialId(), partIndex, summary[0]));
                    });
                }
            }
        }

        return String.join("<br>", dependencySummaries);
    }

    private String truncateString(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }

    /**
     * Generates a concise description of a content part, including its type and a summary of its content.
     *
     * @param p The part to describe.
     * @return A String array where the first element is the type and the second is the content summary.
     */
    public String[] describePart(Part p) {
        final int MAX_LENGTH = 128;
        String type;
        String contentSummary;

        if (p.text().isPresent()) {
            type = "Text";
            contentSummary = truncateString(p.text().get(), MAX_LENGTH);
        } else if (p.functionCall().isPresent()) {
            FunctionCall fc = p.functionCall().get();
            type = "FunctionCall" + fc.id().map(id -> " (id=" + id + ")").orElse("");

            StringBuilder summary = new StringBuilder(fc.name().get());
            if (fc.args().isPresent()) {
                Map<String, Object> args = fc.args().get();
                String argsString = args.entrySet().stream()
                        .filter(entry -> !TextUtils.isNullOrEmpty(entry.getValue()))
                        .map(entry -> entry.getKey() + "=" + TextUtils.formatValue(entry.getValue()))
                        .collect(Collectors.joining(", ", "(", ")"));
                if (!argsString.equals("()")) {
                    summary.append(argsString);
                }
            }
            contentSummary = summary.toString();

        } else if (p.functionResponse().isPresent()) {
            FunctionResponse fr = p.functionResponse().get();
            type = "FunctionResponse" + fr.id().map(id -> " (id=" + id + ")").orElse("");

            StringBuilder summary = new StringBuilder(fr.name().orElse("unknown"));
            Optional<StatefulResourceStatus> statusOpt = contextManager.getResourceTracker().getResourceStatus(fr);
            if (statusOpt.isPresent()) {
                StatefulResourceStatus srs = statusOpt.get();
                String fileName = new File(srs.getResourceId()).getName();
                summary.append(String.format(":STATEFUL:%s (%s)", fileName, srs.getStatus().name()));
            } else {
                Object responseObject = fr.response().get();
                if (responseObject instanceof Map) {
                    Map<String, Object> responseMap = (Map<String, Object>) responseObject;
                    String summaryString = responseMap.entrySet().stream()
                            .filter(entry -> !TextUtils.isNullOrEmpty(entry.getValue()))
                            .map(entry -> entry.getKey() + "=" + TextUtils.formatValue(entry.getValue()))
                            .collect(Collectors.joining(", ", "{", "}"));
                    if (!summaryString.equals("{}")) {
                        summary.append(": ").append(summaryString);
                    }
                } else {
                    summary.append(": ").append(TextUtils.formatValue(responseObject));
                }
            }
            contentSummary = summary.toString();

        } else if (p.inlineData().isPresent()) {
            type = "Blob";
            contentSummary = PartUtils.toString(p.inlineData().get());
        } else if (p.codeExecutionResult().isPresent()) {
            CodeExecutionResult cer = p.codeExecutionResult().get();
            type = "CodeExecutionResult";
            contentSummary = truncateString(cer.outcome().get() + ":" + cer.output().get(), MAX_LENGTH);
        } else if (p.executableCode().isPresent()) {
            ExecutableCode ec = p.executableCode().get();
            type = "ExecutableCode";
            contentSummary = truncateString(ec.code().get(), MAX_LENGTH);
        } else {
            type = "Unknown";
            contentSummary = truncateString(p.toString(), MAX_LENGTH);
        }
        
        // Escape newlines and pipes to prevent markdown table formatting hiccups.
        String finalContent = contentSummary.replace("\n", "\\n").replace("\r", "").replace("|", "\\|");
        return new String[]{type, finalContent};
    }
}