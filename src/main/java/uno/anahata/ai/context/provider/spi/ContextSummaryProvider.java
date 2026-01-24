/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.*;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import uno.anahata.ai.Chat;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.context.stateful.ResourceStatus;
import uno.anahata.ai.context.stateful.ResourceTracker;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.gemini.GeminiAdapter;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.internal.TextUtils;

/**
 * A critical context provider that injects a detailed, machine-readable summary
 * of the entire conversation history into the model's prompt.
 * <p>
 * This summary includes unique IDs for every message and part, allowing the
 * model to perform precise context pruning and management.
 * </p>
 */
public class ContextSummaryProvider extends ContextProvider {

    /**
     * Constructs a new ContextSummaryProvider, targeting the
     * {@link ContextPosition#AUGMENTED_WORKSPACE} position.
     */
    public ContextSummaryProvider() {
        super(ContextPosition.AUGMENTED_WORKSPACE);
    }
    
    @Override
    public String getId() {
        return "core-context-summary";
    }

    @Override
    public String getDisplayName() {
        return "Context Summary";
    }

    private String formatAge(Duration duration) {
        if (duration == null) {
            return "N/A";
        }
        long seconds = duration.getSeconds();
        if (seconds < 0) {
            return "0s";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
            if (hours > 0) {
                sb.append(" ").append(hours).append("h");
            }
        } else if (hours > 0) {
            sb.append(hours).append("h");
            if (minutes > 0) {
                sb.append(" ").append(minutes).append("m");
            }
        } else if (minutes > 0) {
            sb.append(minutes).append("m");
            if (secs > 0) {
                sb.append(" ").append(secs).append("s");
            }
        } else {
            sb.append(secs).append("s");
        }
        return sb.toString();
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Total Token Count (last turn): %d\nToken Threshold (Max Tokens): %d\n",
                chat.getContextManager().getTotalTokenCount(),
                chat.getContextManager().getTokenThreshold()
        ));
        sb.append("\n");
        sb.append("The following table provides unique IDs for every part in the conversation history. "
                + "Use these IDs with the ContextWindow pruning tools to manage your context window efficiency "
                + "according to the 'PAYG PRUNING PROTOCOL' in your system instructions.\n\n");

        List<ChatMessage> messages = chat.getContextManager().getContext();
        if (messages.isEmpty()) {
            sb.append("# Context Entries: 0\n");
            return Collections.singletonList(Part.fromText(sb.toString()));
        }

        sb.append("# Context Entries: ").append(messages.size()).append("\n");

        // --- Legend ---
        Map<String, String> nameAbbreviations = new LinkedHashMap<>();
        Map<String, String> reverseNameMap = new LinkedHashMap<>();

        messages.stream().map(msg -> getMessageName(msg, chat.getContextManager())).distinct().forEach(name -> {
            String abbr;
            if (name.equals("tool")) {
                abbr = "t";
            } else {
                abbr = name.substring(0, 1);
                if (reverseNameMap.values().contains(abbr)) {
                    int i = 2;
                    while (reverseNameMap.values().contains(abbr + i)) {
                        i++;
                    }
                    abbr = abbr + i;
                }
            }
            nameAbbreviations.put(name, abbr);
            reverseNameMap.put(name, abbr);
        });

        sb.append("**Legend:**\n");
        sb.append("  - **Roles**: u=user, m=model, t=tool\n");
        sb.append("  - **Names**: ").append(nameAbbreviations.entrySet().stream().map(e -> e.getValue() + "=" + e.getKey()).collect(Collectors.joining(", "))).append("\n");
        sb.append("  - **Types**: **O**=Other (Text/Blob/etc.), **E**=Ephemeral (Non-stateful tool), **S**=Stateful (File content)\n");
        sb.append("  - **Sig**: **Y**=Contains a thoughtSignature \n\n");

        // --- Table ---
        List<String> headers = new ArrayList<>();
        headers.add("Age");
        headers.add("Name");
        headers.add("Type");
        headers.add("Sig");
        headers.add("Pruning ID");
        headers.add("Content");
        headers.add("Size (KB)");

        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|---:|\n");

        Instant now = Instant.now();
        ResourceTracker rt = chat.getContextManager().getResourceTracker();

        // Pre-calculate which tool call IDs are associated with a signature
        Set<String> signedToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            for (Part part : msg.getContent().parts().orElse(Collections.emptyList())) {
                if (part.thoughtSignature().isPresent()) {
                    GeminiAdapter.getToolCallId(part).ifPresent(signedToolCallIds::add);
                }
            }
        }

        for (ChatMessage msg : messages) {
            List<Part> parts = msg.getContent().parts().orElse(Collections.emptyList());
            String msgName = getMessageName(msg, chat.getContextManager());
            String nameAbbr = reverseNameMap.getOrDefault(msgName, "?");
            String age = formatAge(Duration.between(msg.getCreatedOn(), now));

            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                long partSize = PartUtils.calculateSizeInBytes(part);
                
                Optional<StatefulResourceStatus> statusOpt = rt.getResourceStatus(part, msg);
                boolean isStateful = statusOpt.isPresent();
                String toolCallId = GeminiAdapter.getToolCallId(part).orElse("");
                boolean isToolCall = !toolCallId.isEmpty();
                
                // A part is 'signed' if it has a signature OR if it's a tool call/response 
                // where the other side of the pair has a signature.
                boolean hasSignature = part.thoughtSignature().isPresent() || 
                                       (isToolCall && signedToolCallIds.contains(toolCallId));

                List<String> row = new ArrayList<>();
                row.add(i == 0 ? age : ""); // Only show age for the first part of a message
                row.add(i == 0 ? nameAbbr : ""); // Only show name for the first part
                
                // Type Column
                String typeCode = isStateful ? "**S**" : (isToolCall ? "**E**" : "**O**");
                row.add(typeCode);

                // Sig Column
                row.add(hasSignature ? "**Y**" : "");
                
                // Pruning ID Column
                String pruningId = "";
                if (isStateful) {
                    if (part.functionResponse().isPresent()) {
                        pruningId = "**" + statusOpt.get().getResourceId() + "**";
                    }
                } else if (isToolCall) {
                    pruningId = "**" + toolCallId + "**";
                } else {
                    pruningId = "**" + msg.getSequentialId() + "/" + i + "**";
                }
                row.add(pruningId);
                
                String typePrefix;
                String contentSummary;

                if (part.text().isPresent()) {
                    typePrefix = "T";
                    contentSummary = TextUtils.formatValue(part.text().get());
                } else if (part.functionCall().isPresent()) {
                    FunctionCall fc = part.functionCall().get();
                    typePrefix = "FC";
                    String toolName = fc.name().get();
                    
                    if (isStateful) {
                        StatefulResourceStatus srs = statusOpt.get();
                        String fileName = new File(srs.getResourceId()).getName();
                        contentSummary = String.format("%s %s", toolName, fileName);
                    } else {
                        StringBuilder summary = new StringBuilder(toolName);
                        Map<String, Object> args = fc.args().orElse(Collections.emptyMap());
                        if (!args.isEmpty()) {
                            String argsString = args.entrySet().stream()
                                    .filter(entry -> !TextUtils.isNullOrEmpty(entry.getValue()))
                                    .map(entry -> entry.getKey() + "=" + TextUtils.formatValue(entry.getValue()))
                                    .collect(Collectors.joining(", ", "(", ")"));
                            if (!"()".equals(argsString)) {
                                summary.append(argsString);
                            }
                        }
                        contentSummary = summary.toString();
                    }
                    
                } else if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    typePrefix = "FR";
                    String toolName = fr.name().orElse("unknown");
                    
                    if (isStateful) {
                        StatefulResourceStatus srs = statusOpt.get();
                        String fileName = new File(srs.getResourceId()).getName();
                        String statusStr = srs.getStatus().name();
                        if (srs.getStatus() == ResourceStatus.VALID) {
                            contentSummary = String.format("%s %s %s %d", toolName, fileName, statusStr, srs.getContextLastModified());
                        } else {
                            contentSummary = String.format("%s %s %s", toolName, fileName, statusStr);
                        }
                    } else {
                        contentSummary = toolName + ": " + TextUtils.formatValue(fr.response().orElse(Collections.emptyMap()));
                    }
                } else if (part.inlineData().isPresent()) {
                    typePrefix = "B";
                    contentSummary = part.inlineData().get().mimeType() + " " + PartUtils.calculateSizeInBytes(part) + " bytes";
                } else if (part.executableCode().isPresent()) {
                    typePrefix = "EC";
                    contentSummary = "Executable Code (" + part.executableCode().get().language() + ")";
                } else if (part.codeExecutionResult().isPresent()) {
                    typePrefix = "ER";
                    contentSummary = "Code Execution Result (" + part.codeExecutionResult().get().outcome() + ")";
                } else {
                    typePrefix = "?";
                    contentSummary = "Unknown part type";
                }
                
                String contentColumn = typePrefix + " " + contentSummary.replace("\n", " ").replaceAll("\\s+", " ").trim();
                row.add(contentColumn);
                row.add(String.format("%.1f", partSize / 1024.0));

                sb.append("| ").append(String.join(" | ", row)).append(" |\n");
            }
        }
        sb.append("\n-------------------------------------------------------------------");

        return Collections.singletonList(Part.fromText(sb.toString()));
    }

    private String getMessageName(ChatMessage msg, ContextManager contextManager) {
        String role = msg.getContent().role().orElse("system");
        switch (role) {
            case "model":
                return msg.getModelId();
            case "tool":
                return "tool";
            case "user":
            default:
                return System.getProperty("user.name");
        }
    }
}