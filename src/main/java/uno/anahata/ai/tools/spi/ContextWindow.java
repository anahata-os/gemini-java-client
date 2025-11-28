package uno.anahata.ai.tools.spi;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

/**
 * A set of tools for inspecting and managing the chat context window.
 */
public class ContextWindow {

    @AIToolMethod(
            value = "Lists all entries (messages) in the context including their stable Message IDs, roles, and all their parts"
    )
    public static String getContextSummary() {
        return ContextManager.getCallingInstance().getSessionManager().getSummaryAsString();
    }

//    @AIToolMethod(
//        value = "Prunes one or more entire messages from the context. "
//        + "\nVery Important notes: "
//        + "\nPruning a message will prune ALL the parts on that message and ALL dependencies of ALL those parts."
//        + "\n\t2) Pruning a FunctionResponse will automatically prune its corresponding FunctionCall"
//        + "\n\t3) Pruning a FunctionCall will automatically prune its corresponding FunctionResponse (if any)"
//        + "\n\t4) Pruning a FunctionResponse (or a FunctionCall) of a STATEFULE_REPLACE tool that returned an actual Stateful Resource will remove the resource itself from context (as the very content of this resource is in fhe FunctionResponse part itself)"
//    )
    public static String pruneMessages(
            @AIToolParam(value = "A list of the unique, Message IDs to be removed.") List<Long> uids,
            @AIToolParam(value = "A brief rationale for why the messages are being removed.") String reason) throws Exception {
        ContextManager cm = ContextManager.getCallingInstance();
        cm.pruneMessages(uids, reason);
        return "Pruned " + uids.size() + " messages.";
    }

//    @AIToolMethod(
//        value = "Prunes one or several parts from a given message. "
//        + "IMPORTANT: If a FunctionCall or FunctionResponse part is targeted, its corresponding paired part (FunctionResponse or FunctionCall) is **automatically resolved and pruned as well** by the system to maintain conversation integrity. The model only needs to specify one part of the pair."
//    )
    public static String pruneParts(
            @AIToolParam(value = "Message ID containing the parts.") long messageId,
            @AIToolParam(value = "A list of Part Nº indices of those parts .") List<Number> parts,
            @AIToolParam(value = "A brief rationale for why those parts are being removed.") String reason) throws Exception {
        ContextManager.getCallingInstance().pruneParts(messageId, parts, reason);
        return "Pruning request for " + parts.size() + " parts from message " + messageId + " has been processed.";
    }

    @AIToolMethod(
            value = "Prunes one or more parts from the context using the 'messageId/partIndex' notation. This is a convenience tool that resolves the part references and then calls the underlying pruning logic, which handles all dependency resolution automatically."
    )
    public static String prunePartsByIds(
            @AIToolParam(value = "A list of part identifiers in the format 'messageId/partIndex' (e.g., ['128/0', '130/1']).") List<String> partIds,
            @AIToolParam(value = "A brief rationale for why the parts are being removed.") String reason) throws Exception {

        ContextManager cm = ContextManager.getCallingInstance();
        List<ChatMessage> context = cm.getContext();
        List<Part> partsToPrune = new ArrayList<>();
        List<String> unresolvedIds = new ArrayList<>();

        for (String partId : partIds) {
            try {
                String[] split = partId.split("/");
                if (split.length != 2) {
                    throw new IllegalArgumentException("Invalid format. Expected 'messageId/partIndex'.");
                }
                long messageId = Long.parseLong(split[0]);
                int partIndex = Integer.parseInt(split[1]);

                Optional<ChatMessage> targetMessage = context.stream()
                        .filter(m -> m.getSequentialId() == messageId)
                        .findFirst();

                if (targetMessage.isPresent()) {
                    List<Part> parts = targetMessage.get().getContent().parts().orElse(Collections.emptyList());
                    if (partIndex >= 0 && partIndex < parts.size()) {
                        partsToPrune.add(parts.get(partIndex));
                    } else {
                        unresolvedIds.add(partId + " (Part index out of bounds)");
                    }
                } else {
                    unresolvedIds.add(partId + " (Message ID not found)");
                }
            } catch (Exception e) {
                unresolvedIds.add(partId + " (Error: " + e.getMessage() + ")");
            }
        }

        if (!partsToPrune.isEmpty()) {
            cm.prunePartsByReference(partsToPrune, reason);
        }

        StringBuilder response = new StringBuilder();
        response.append("Pruning request processed. Found and pruned ").append(partsToPrune.size()).append(" parts.");
        if (!unresolvedIds.isEmpty()) {
            response.append(" Could not resolve the following IDs: ").append(String.join(", ", unresolvedIds));
        }

        return response.toString();
    }

    @AIToolMethod(
            value = "Finds and prunes all Parts (both FunctionCall and FunctionResponse) with that tool call ID. This is the most effective way to remove a complete tool interaction and all its dependencies from the context."
    )
    public static String pruneToolCall(
            @AIToolParam(value = "The tool call ID as shown in the type column of the context summary (FunctionCall (id=108)) or (FunctionResponse (id=108)) ") String toolCallId,
            @AIToolParam(value = "A brief rationale for why the tool call is being removed.") String reason) throws Exception {
        ContextManager.getCallingInstance().pruneToolCall(toolCallId, reason);
        return "Pruning request for tool call ID '" + toolCallId + "' has been processed.";
    }

    @AIToolMethod(
            value = "Prunes all FunctionCall and FunctionResponse parts associated with the given stateful resource IDs. "
            + "This is the recommended method for removing stale or unnecessary stateful resources (e.g. file contents) as it provides better feedback to the user"
    )
    public static String pruneStatefulResources(
            @AIToolParam(value = "The Resource ID (as given by the 'Stateful Resources in Context' provider)") List<String> resourceIds) throws Exception {
        ContextManager.getCallingInstance().getResourceTracker().pruneStatefulResources(resourceIds);
        return "Pruning request for " + resourceIds.size() + " stateful resources has been processed.";
    }

    @AIToolMethod(
            value = "Gets the current total token count in the context window as shown to the user. This is the total token count received on the last api call (prompt + candidate)"
    )
    public static int getTokenCount() throws Exception {
        return ContextManager.getCallingInstance().getTotalTokenCount();
    }

    @AIToolMethod(
            value = "Gets the current token threshold. This is the maximum number of total tokens (prompt + candidate) that the user can / is keen to work with due to his own budget or api / model constraints."
    )
    public static int getTokenThreshold() {
        return ContextManager.getCallingInstance().getTokenThreshold();
    }

    @AIToolMethod(
            value = "Sets the token threshold. See getTokenThreshold"
    )
    public static String setTokenThreshold(
            @AIToolParam(value = "The new token threshold value.") int newThreshold
    ) {
        ContextManager.getCallingInstance().setTokenThreshold(newThreshold);
        return "Token threshold set to " + newThreshold;
    }

    @AIToolMethod(
            value = "Enables / disables one or more context providers"
    )
    public static String toggleContextProviders(
            @AIToolParam("Context provider id as in ") List<String> contextProviderIds,
            @AIToolParam("Context provider id as in ") boolean enabled) {
        String feedback = "";
        for (String contextProviderId : contextProviderIds) {
            boolean found = false;
            for (ContextProvider cp : ContextManager.getCallingInstance().getConfig().getContextProviders()) {
                if (cp.getId().equals(contextProviderId)) {
                    feedback += "\n-Found provider " + cp.getId() + " " + cp.getDisplayName();
                    if (!cp.isEnabled() == enabled) {
                        cp.setEnabled(enabled);
                        feedback += ". Provider updated. Enabled  : " + enabled;
                    } else {
                        feedback += ". No change, enabled flag was already : " + enabled;
                    }
                    found = true; // Correctly set the flag
                }
            }
            if (!found) {
                feedback += "\n-Context Provider: " + contextProviderId + " was not found. ";
            }
        }
        return feedback;
    }
}
