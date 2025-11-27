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
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.internal.TextUtils;

public class ContextSummaryProvider extends ContextProvider {

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
        sb.append(String.format("Total Token Count: %d\nToken Threshold: %d\n",
                chat.getContextManager().getTotalTokenCount(),
                chat.getContextManager().getTokenThreshold()
        ));
        sb.append("\n");
        sb.append("The following table is the Output of ContextWindow.listEntries() with the unique id of every message in this conversation and a list of all parts so you can compress the context as needed or as instructed by the user."
                + "\n\nIf the user asks you to **compress** the context or you are approaching max context window usage (max tokens), you must:\n"
                + "\n1) summarize everything you are prunning onto an actual text part in your next response (not just the message on the prune tool).  \n"
                + "\n2) use the prune tools in ContextWindow to remove: "
                + "    \n\ta) entire messages, "
                + "    \n\tb) specific parts "
                + "    \n\tc) tool calls (call/response pairs)."
                + "\n\n"
                + "Some prune tools have a reason parameter which is mainly for debugging and pruning logic improvement and diagnostics but will disappear from the conversation like every other ephemeral tool call. The Compressed content must be in your 'spoken' response.\n"
                + "\n\n Use your discrimination when choosing prunning tools but take into consideration that:"
                + "\na) Some Parts have logical dependencies (e.g. FunctionCall <-> FunctionResponse)"
                + "\nb) Pruning a message will prune ALL the parts on that message and ALL dependencies of ALL those parts."
                + "\n"
                + "\nIn Other words:"
                + "\n\t1) Pruning a message will prune all its parts"
                + "\n\t2) Pruning a FunctionResponse will automatically prune its corresponding FunctionCall"
                + "\n\t3) Pruning a FunctionCall will automatically prune its corresponding FunctionResponse"
                + "\n\t4) Pruning a FunctionResponse (or a FunctionCall) of a STATEFULE_REPLACE tool that returned an actual Stateful Resource will remove the resource itself from context (as the very content of this resource is in fhe FunctionResponse part itself)"
                + "\n"
                + "\nc) Some parts of the conversation can be offloaded to relevant md files on disk and is up to you and the user whether you want to offload anything to disk or just onto a text part before the prune calls)"
                + "\nd) Compressing text parts from the conversation can also help a lot in reducing the total number of tokens (is not always just about pruning stateful resources)"
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
        sb.append("  - **Parts**: T=Text, FC=FunctionCall, FR=FunctionResponse, B=BLOB\n\n");

        // --- Table ---
        List<String> headers = new ArrayList<>();
        headers.add("Age");
        headers.add("Name");
        headers.add("Part Id");
        headers.add("Part Type");
        headers.add("Size (KB)");
        // headers.add("Role"); // Commented out as requested
        headers.add("Content");
        headers.add("Dependencies");

        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("|:---|:---|:---|:---|---:|:---|:---|\n");

        Instant now = Instant.now();

        for (ChatMessage msg : messages) {
            List<Part> parts = msg.getContent().parts().orElse(Collections.emptyList());
            String msgName = getMessageName(msg, chat.getContextManager());
            String nameAbbr = reverseNameMap.getOrDefault(msgName, "?");
            String age = formatAge(Duration.between(msg.getCreatedOn(), now));

            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                String[] partSummary = describePart(part, chat.getContextManager());
                String dependencies = summarizeDependencies(part, chat.getContextManager());
                long partSize = PartUtils.calculateSizeInBytes(part);

                List<String> row = new ArrayList<>();
                row.add(i == 0 ? age : ""); // Only show age for the first part of a message
                row.add(i == 0 ? nameAbbr : ""); // Only show name for the first part
                row.add(msg.getSequentialId() + "/" + i);
                row.add(partSummary[0]);
                row.add(String.format("%.1f", partSize / 1024.0));
                // row.add(roleAbbr); // Commented out as requested
                row.add(partSummary[1].replace("\n", " ").replaceAll("\\s+", " ").trim());
                row.add(dependencies);

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

    private String summarizeDependencies(Part part, ContextManager contextManager) {
        Set<String> dependencySummaries = new HashSet<>();
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
                            dependencySummaries.add(String.format("%d/%d", depMsg.getSequentialId(), partIndex));
                        });
                    }
                }

                // Reverse dependency: This part is a dependent
                if (dependentParts.contains(part)) {
                    contextManager.getChatMessageForPart(sourcePart).ifPresent(srcMsg -> {
                        int partIndex = srcMsg.getContent().parts().get().indexOf(sourcePart);
                        dependencySummaries.add(String.format("%d/%d", srcMsg.getSequentialId(), partIndex));
                    });
                }
            }
        }
        return dependencySummaries.stream().sorted().collect(Collectors.joining(" "));
    }

    private String[] describePart(Part p, ContextManager contextManager) {
        String type;
        String contentSummary;

        if (p.text().isPresent()) {
            type = "T";
            contentSummary = TextUtils.formatValue(p.text().get());
        } else if (p.functionCall().isPresent()) {
            FunctionCall fc = p.functionCall().get();
            type = "FC" + fc.id().map(id -> " " + id).orElse("");
            
            StringBuilder summary = new StringBuilder(fc.name().get());
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
            
        } else if (p.functionResponse().isPresent()) {
            FunctionResponse fr = p.functionResponse().get();
            type = "FR" + fr.id().map(id -> " " + id).orElse("");
            StringBuilder summary = new StringBuilder(fr.name().orElse("unknown"));
            Optional<StatefulResourceStatus> statusOpt = contextManager.getResourceTracker().getResourceStatus(fr);
            if (statusOpt.isPresent()) {
                StatefulResourceStatus srs = statusOpt.get();
                String fileName = new File(srs.getResourceId()).getName();
                summary.append(String.format(":STATEFUL:%s (%s)", fileName, srs.getStatus().name()));
            } else {
                summary.append(": ").append(TextUtils.formatValue(fr.response().orElse(Collections.emptyMap())));
            }
            contentSummary = summary.toString();
        } else if (p.inlineData().isPresent()) {
            type = "B";
            contentSummary = p.inlineData().get().mimeType() + " " + PartUtils.calculateSizeInBytes(p) + " bytes";
        } else {
            type = "?";
            contentSummary = "Unknown part type";
        }

        String finalContent = contentSummary.replace("\n", "\\n").replace("\r", "").replace("|", "\\|");
        return new String[]{type, finalContent};
    }
}