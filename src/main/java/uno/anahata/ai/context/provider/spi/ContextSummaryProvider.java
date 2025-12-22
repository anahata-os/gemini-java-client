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

public class ContextSummaryProvider extends ContextProvider {

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
        sb.append("The following table is the Output of ContextWindow.getContextSummary() with the unique id of every part, every stateful resource and every tool call pair (request response) in this conversation so you can compress the context *as-you-go* or when explicitely instructed by the user. Dont expect the user to instruct you explicitely and dont expect the user to perform manual prunning either."
                + "You have to work with the total token count (as given by the api on the last turn) and the threshold (max tokens) to work out how far you can go in adding to the context window."
                + "Your ultimate optimal goal is to ensure that a conversation can "
                + "\na)Run indefinetly (without ever hitting max tokens) with the most relevant information in the most relevant postion of the context"
                + "\nb)Be as efficient as possible in terms of token usage and turns"
                + "\nc)Be a smooth flowing experience to the point that the user wont even think about manual prunning or ask you to 'compress' the context."
                
                + "\n"
                + "\n\nTool execution results are sent inmeditaly (sometimes without the user having a chance to add a message) so: "
                + "\n**Prune in this turn if ANY of these two conditions are met**: "
                + "\n\ta) **you are making other tool calls that are not pruning / context window management related** (i.e. if you are just replying to the user, dont prune unless you are making other non prunning tool calls, otherwise it would waste a turn as the prunning tool calls are inmediatly sent back to the server)"
                + "\n\tb) the context window has gone so large that you estimate prunning to be essential to stay within max input tokens"
                + ""
                + "Whenever ANY part (regardless of the type, the role or the position in the history) becomes redundant (i.e. a task that has been resolved/completed, a duplicate message, a hallucination, etc) OR its semantic meaning can be compressed (i.e. the matter being discussed has been clarified and can be kept in context in much more synthetized manner like when it is part of a resolved trial-and-error process and just the 'gist' of it needs to stay in context). Every token in the context window is -ultimately- your responsability."
                + "\n\nIf I (the user) explicitely ask you to **compress** the context you must:\n"
                + "\n1) Work with the me to see what discussions / resources / tool calls to keep and what to discard.  \n"
                + "\n2) Use the prune tools in ContextWindow like you normally do when you prune-as-you-go"
                + "\n\n"
                + "Some prune tools have a reason parameter which is mainly for debugging, pruning logic improvements, diagnostics etc... but will disappear from the conversation -like every other ephemeral tool call- after 5 user turns so dont exepct a reason parameter to stay in context. The *compressed* content of anything you are prunning must be in the text parts from your 'spoken' response (i.e. text parts of this turn).\n"
                + "\n\n Use your discrimination when choosing prunning tools but take into consideration that some Parts have logical dependencies (e.g. FunctionCall <-> FunctionResponse)"
                + "\n"
                + "\n**STRICT PRUNING PROTOCOL**:"
                + "\n\t1) **Type O (Other)**: Use `pruneOther` for non-tool content (Text, Blob, CodeExecutionResult or ExecutableCode parts). Specify the 'Pruning ID' (MessageId/PartId)."
                + "\n\t2) **Type E (Ephemeral)**: Use `pruneEphemeralToolCall` for non-stateful tool calls. Specify the 'Pruning ID' (Tool Call ID)."
                + "\n\t3) **Type S (Stateful)**: Use `pruneStatefulResources` ONLY when you explicitly intend to remove a file's content from your context. Specify the 'Pruning ID' (Full Resource Path) from the FR row."
                + "\n\t4) Pruning a FunctionResponse will automatically prune its corresponding FunctionCall (and vice versa)."
                + "\n"
                + "\nRemember that you can also offload a summary of the conversation to an .md file on disk so it is always up to you and the user whether you want to offload to disk or just onto a simple text part in your next turn"
                + "\nDo not give text parts less weight than tool calls, if you can summarise all your text parts from the last five or ten turns (or whatever number of turns) onto a single one, that can also help a lot in reducing the total number of tokens (is not always just about pruning stateful resources or tool calls)"
                + "\n\n");

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
        sb.append("  - **Types**: **O**=Other (Text/Blob/Code), **E**=Ephemeral (Non-stateful tool), **S**=Stateful (File content)\n\n");

        // --- Table ---
        List<String> headers = new ArrayList<>();
        headers.add("Age");
        headers.add("Name");
        headers.add("Type");
        headers.add("Pruning ID");
        headers.add("Content");
        headers.add("Size (KB)");

        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("|:---|:---|:---|:---|:---|---:|\n");

        Instant now = Instant.now();
        ResourceTracker rt = chat.getContextManager().getResourceTracker();

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

                List<String> row = new ArrayList<>();
                row.add(i == 0 ? age : ""); // Only show age for the first part of a message
                row.add(i == 0 ? nameAbbr : ""); // Only show name for the first part
                
                // Type Column
                String typeCode = isStateful ? "**S**" : (isToolCall ? "**E**" : "**O**");
                row.add(typeCode);
                
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
