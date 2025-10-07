package uno.anahata.gemini.functions.spi;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.ContextManager;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;

public class ContextWindow {

    public static int TOKEN_THRESHOLD = 108_000;

    @AIToolMethod("Sets the token threshold for automatic context pruning.")
    public static String setTokenThreshold(
            @AIToolParam("The new token threshold value.") int newThreshold
    ) {
        TOKEN_THRESHOLD = newThreshold;
        return "Token threshold updated to " + newThreshold;
    }

    @AIToolMethod("Gets the current token threshold for automatic context pruning. "
            + "Calls to the model should never exceed this value. It is passed with the system instructions on every request to the model along with the last total token count as in History.getTotalTokenCount")
    public static int getTokenThreshold() {
        return TOKEN_THRESHOLD;
    }

    @AIToolMethod("Gets the current total token count in the context window as shown to the user. "
            + "This is a value calculated by the model and extracted from the models last response")
    public static int getTokenCount() throws Exception {
        GeminiChat chat = GeminiChat.currentChat.get();
        if (chat == null) {
            throw new IllegalStateException("Could not get current chat context.");
        }
        return chat.getContextManager().getTotalTokenCount();
    }

    @AIToolMethod(value = "Prunes the context window by removing specific entries. STRATEGIC NOTE: This removes information from the model's active context for future turns. Only prune entries that are truly redundant or no longer relevant to the current task.", requiresApproval = true)
    public static String pruneContext(
            @AIToolParam("A list of the entries being removed [contentIdx/partIdx][partType]reason for removal") String removedParts,
            @AIToolParam("An array of zero-based identifiers in format of context entries to removed. Use 'contentIdx' for a whole entry or 'contentIdx/partIdx' for a specific part.") String[] identifiers
    ) throws Exception {
        GeminiChat chat = GeminiChat.currentChat.get();
        if (chat == null) {
            return "Error: Could not get current chat context from ThreadLocal.";
        }

        ContextManager hm = chat.getContextManager();
        List<ChatMessage> originalMessages = hm.getContext();
        int contentCountBefore = originalMessages.size();

        Set<Integer> contentIndicesToRemove = new HashSet<>();
        List<int[]> partRefsToRemove = new ArrayList<>();

        for (String id : identifiers) {
            try {
                if (id.contains("/")) {
                    String[] parts = id.split("/");
                    partRefsToRemove.add(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                } else {
                    contentIndicesToRemove.add(Integer.parseInt(id));
                }
            } catch (NumberFormatException e) {
                // Ignore invalid identifiers
            }
        }

        int partsModified = 0;
        // Handle part removals first by replacing the ChatMessage
        for (int[] partRef : partRefsToRemove) {
            int contentIndex = partRef[0];
            int partIndex = partRef[1];

            if (contentIndex >= 0 && contentIndex < originalMessages.size()) {
                ChatMessage oldMessage = originalMessages.get(contentIndex);
                Content oldContent = oldMessage.getContent();
                if (oldContent != null && oldContent.parts().isPresent()) {
                    List<Part> oldParts = oldContent.parts().get();
                    if (partIndex >= 0 && partIndex < oldParts.size()) {
                        List<Part> newParts = new ArrayList<>(oldParts);
                        newParts.remove(partIndex);

                        if (newParts.isEmpty()) {
                            // Mark the whole message for removal if no parts are left
                            contentIndicesToRemove.add(contentIndex);
                        } else {
                            Content newContent = Content.builder().role(oldContent.role().get()).parts(newParts).build();
                            ChatMessage newMessage = new ChatMessage(
                                oldMessage.getModelId(), newContent, oldMessage.getFunctionResponses(),
                                oldMessage.getUsageMetadata(), oldMessage.getGroundingMetadata()
                            );
                            originalMessages.set(contentIndex, newMessage); // Replace in the list
                            partsModified++;
                        }
                    }
                }
            }
        }

        List<ChatMessage> messagesToRemove = new ArrayList<>();
        for (int index : contentIndicesToRemove) {
            if (index >= 0 && index < originalMessages.size()) {
                messagesToRemove.add(originalMessages.get(index));
            }
        }

        if (!messagesToRemove.isEmpty()) {
            originalMessages.removeAll(messagesToRemove);
        }

        // After modifications, set the context back.
        hm.setContext(originalMessages);
        hm.notifyHistoryChange();

        int contentCountAfter = originalMessages.size();

        File historyFolder = GeminiConfig.getWorkingFolder("history");
        SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        File pruneSummaryfile = new File(historyFolder, timestamp + "-prune.md");
        Files.writeString(pruneSummaryfile.toPath(), removedParts);

        return "Pruning complete. Removed " + partsModified + " Parts and " + messagesToRemove.size() + " entire Content entries. "
                + "\nTotal History entries: Before: " + contentCountBefore + " after:" + contentCountAfter + ". "
                + "\nPruned content summary saved to: " + pruneSummaryfile + " " + pruneSummaryfile.length() + " bytes"
                + "\nUI refresh started.";
    }

    @AIToolMethod("Lists all entries in the context in [contentIdx/partIdx][partType] format")
    public static String listEntries() {
        return ContextManager.get().getSummaryAsString();
    }
}
