/* Licensed under the Apache License, Version 2.0 */
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
 * A set of tools for inspecting and managing the AI's conversation context window.
 * <p>
 * These tools allow the model to monitor its own token usage, prune redundant
 * information, and toggle dynamic context providers.
 * </p>
 */
public class ContextWindow {

    /**
     * Generates a detailed Markdown summary of all messages and parts in the context.
     *
     * @return The context summary string.
     */
    @AIToolMethod(
            value = "Lists all entries (messages) in the context including their stable Message IDs, roles, and all their parts"
    )
    public static String getContextSummary() {
        return ContextManager.getCallingInstance().getSessionManager().getSummaryAsString();
    }

    /**
     * Prunes specific non-tool parts from the context.
     *
     * @param partIds A list of part identifiers in 'messageId/partIndex' format.
     * @param reason  The rationale for removal.
     * @return A status message indicating the result of the pruning.
     * @throws Exception if pruning fails.
     */
    @AIToolMethod(
            value = "Prunes one or more non-tool parts (Text, Blob, CodeExecutionResult or ExecutableCode) from the context using the 'messageId/partIndex' notation."
    )
    public static String pruneOther(
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
            cm.getContextPruner().pruneOther(partsToPrune, reason);
        }

        StringBuilder response = new StringBuilder();
        response.append("Pruning request processed. Found and pruned ").append(partsToPrune.size()).append(" parts.");
        if (!unresolvedIds.isEmpty()) {
            response.append(" Could not resolve the following IDs: ").append(String.join(", ", unresolvedIds));
        }

        return response.toString();
    }

    /**
     * Prunes ephemeral tool calls and their responses by ID.
     *
     * @param toolCallIds A list of tool call IDs.
     * @param reason      The rationale for removal.
     * @return A success message.
     * @throws Exception if pruning fails.
     */
    @AIToolMethod(
            value = "Prunes one or more ephemeral (non-stateful) tool calls and their associated responses using the Tool Call IDs. "
                    + "This tool removes the entire call/response pair from the context. "
                    + "CRITICAL: This tool will fail if used on a tool call that produced a stateful resource (e.g., LocalFiles.readFile, LocalFiles.writeFile, LocalFiles.createFile). "
                    + "For those, you must use pruneStatefulResources with the full file path."
    )
    public static String pruneEphemeralToolCalls(
            @AIToolParam(value = "A list of tool call IDs as shown in the 'Tool Call ID' column of the context summary.") List<String> toolCallIds,
            @AIToolParam(value = "A brief rationale for why the tool calls are being removed.") String reason) throws Exception {
        ContextManager.getCallingInstance().getContextPruner().pruneEphemeralToolCalls(toolCallIds, reason);
        return "Pruning request for ephemeral tool call IDs " + toolCallIds + " has been processed.";
    }

    /**
     * Prunes all parts associated with specific stateful resources.
     *
     * @param resourceIds A list of resource IDs (full paths).
     * @param reason      The rationale for removal.
     * @return A success message.
     * @throws Exception if pruning fails.
     */
    @AIToolMethod(
            value = "Prunes all FunctionCall and FunctionResponse parts associated with the given stateful resource IDs (full paths). "
                    + "Note: This tool takes Resource IDs (file paths), NOT Tool Call IDs."
    )
    public static String pruneStatefulResources(
            @AIToolParam(value = "The Resource ID (full path as given by the 'Pruning ID' column in the context summary)") List<String> resourceIds,
            @AIToolParam(value = "A brief rationale for why the resources are being removed.") String reason) throws Exception {
        ContextManager.getCallingInstance().getResourceTracker().pruneStatefulResources(resourceIds, reason);
        return "Pruning request for " + resourceIds.size() + " stateful resources has been processed.";
    }

    /**
     * Gets the current total token count in the context window.
     *
     * @return The token count.
     * @throws Exception if the count cannot be retrieved.
     */
    @AIToolMethod(
            value = "Gets the current total token count in the context window as shown to the user. This is the total token count received on the last api call (prompt + candidate)"
    )
    public static int getTokenCount() throws Exception {
        return ContextManager.getCallingInstance().getTotalTokenCount();
    }

    /**
     * Gets the current token threshold.
     *
     * @return The token threshold.
     */
    @AIToolMethod(
            value = "Gets the current token threshold. This is the maximum number of total tokens (prompt + candidate) that the user can / is keen to work with due to his own budget or api / model constraints."
    )
    public static int getTokenThreshold() {
        return ContextManager.getCallingInstance().getTokenThreshold();
    }

    /**
     * Sets a new token threshold.
     *
     * @param newThreshold The new threshold value.
     * @return A success message.
     */
    @AIToolMethod(
            value = "Sets the token threshold. See getTokenThreshold"
    )
    public static String setTokenThreshold(
            @AIToolParam(value = "The new token threshold value.") int newThreshold
    ) {
        ContextManager.getCallingInstance().setTokenThreshold(newThreshold);
        return "Token threshold set to " + newThreshold;
    }

    /**
     * Enables or disables specific context providers.
     *
     * @param contextProviderIds A list of provider IDs.
     * @param enabled            The desired state.
     * @return A feedback message listing the updated providers.
     */
    @AIToolMethod(
            value = "Enables / disables one or more context providers"
    )
    public static String toggleContextProviders(
            @AIToolParam("A list of context provider IDs (e.g., ['core-chat-status', 'core-system-properties']).") List<String> contextProviderIds,
            @AIToolParam("True to enable the providers, false to disable them.") boolean enabled) {
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
