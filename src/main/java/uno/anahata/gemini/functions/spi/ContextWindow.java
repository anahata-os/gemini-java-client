package uno.anahata.gemini.functions.spi;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;
import uno.anahata.gemini.internal.PartUtils;

public class ContextWindow {

    public static int TOKEN_THRESHOLD = 108_000;

    @AIToolMethod("Sets the token threshold for automatic context pruning.")
    public static String setTokenThreshold(
            @AIToolParam("The new token threshold value.") int newThreshold
    ) {
        TOKEN_THRESHOLD = newThreshold;
        return "Token threshold updated to " + newThreshold;
    }

    @AIToolMethod("Gets the current token threshold for automatic context pruning.")
    public static int getTokenThreshold() {
        return TOKEN_THRESHOLD;
    }

    @AIToolMethod("Gets the current total token count in the context window as shown to the user.")
    public static int getTokenCount() throws Exception {
        return ContextManager.getCallingInstance().getTotalTokenCount();
    }

    @AIToolMethod(value = "Prunes one or more entire messages (Content objects) from the context.", requiresApproval = true)
    public static String pruneMessages(
            @AIToolParam("A list of the unique, stable IDs of the ChatMessages to be removed.") List<String> uids,
            @AIToolParam("A brief rationale for why the messages are being removed.") String reason
    ) throws Exception {
        ContextManager cm = ContextManager.getCallingInstance();
        int countBefore = cm.getContext().size();
        cm.pruneMessages(uids, reason);
        int countAfter = cm.getContext().size();
        int removedCount = countBefore - countAfter;

        File historyFolder = GeminiConfig.getWorkingFolder("history");
        SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        File pruneSummaryfile = new File(historyFolder, timestamp + "-prune-messages.md");
        String summary = "Rationale: " + reason + "\n\nRemoved IDs:\n" + String.join("\n", uids);
        Files.writeString(pruneSummaryfile.toPath(), summary);

        return "Pruning complete. Removed " + removedCount + " messages.";
    }
    
    @AIToolMethod(value = "Prunes one or several parts from a given message. If a FunctionCall or FunctionResponse part is pruned, its corresponding paired part (FunctionResponse or FunctionCall) is automatically resolved and pruned as well to maintain conversation integrity.", requiresApproval = true)
    public static String pruneParts(
            @AIToolParam("The unique, stable ID of the ChatMessage containing the parts.") String messageUID,
            @AIToolParam("A list of zero-based indices of the parts to remove.") List<Number> parts,
            @AIToolParam("A brief rationale for why the parts are being removed.") String reason
    ) throws Exception {
        ContextManager.getCallingInstance().pruneParts(messageUID, parts, reason);
        return "Pruning of " + parts.size() + " part(s) from message " + messageUID + " complete.";
    }

    @AIToolMethod("Lists all entries in the context, including their stable IDs, roles, and a summary of their parts.")
    public static String listEntries() {
        return ContextManager.getCallingInstance().getSummaryAsString();
    }
    
    @AIToolMethod(value = "Prunes all FunctionResponse parts associated with the given stateful resource IDs, along with their corresponding FunctionCall parts.", requiresApproval = true)
    public static String pruneStatefulResources(
            @AIToolParam("A list of resource identifiers (e.g., absolute file paths) to be pruned from the context.") List<String> resourceIds,
            @AIToolParam("A brief rationale for why these resources are being removed.") String reason
    ) throws Exception {
        ContextManager.getCallingInstance().pruneStatefulResources(resourceIds, reason);
        return "Pruning of " + resourceIds.size() + " stateful resource(s) complete.";
    }
}
