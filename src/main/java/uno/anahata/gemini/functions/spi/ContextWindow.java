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

    @AIToolMethod("Sets the token threshold. See getTokenThreshold")
    public static String setTokenThreshold(
            @AIToolParam("The new token threshold value.") int newThreshold
    ) {
        TOKEN_THRESHOLD = newThreshold;
        return "Token threshold updated to " + newThreshold;
    }

    @AIToolMethod("Gets the current token threshold. This is the maximum number of total tokens (prompt + candidate) that the user can / is keen to work with due to his own budget or api / model constraints.")
    public static int getTokenThreshold() {
        return TOKEN_THRESHOLD;
    }

    @AIToolMethod("Gets the current total token count in the context window as shown to the user. This is the total token count received on the last api call (prompt + candidate)")
    public static int getTokenCount() throws Exception {
        return ContextManager.getCallingInstance().getTotalTokenCount();
    }

    @AIToolMethod(value = "Prunes one or more entire messages (Content objects) from the context. "
            + "IMPORTANT: If a message contains FunctionCall or FunctionResponse parts, their counter parts will automatically be prunned to mantain conversation integrity.", requiresApproval = true)
    public static String pruneMessages(
            @AIToolParam("A list of the unique, stable UUIDs of the ChatMessages to be removed.") List<String> uids,
            @AIToolParam("A brief rationale for why the messages are being removed.") String reason
    ) throws Exception {
        ContextManager cm = ContextManager.getCallingInstance();
        int countBefore = cm.getContext().size();
        cm.pruneMessages(uids, reason);
        int countAfter = cm.getContext().size();
        int removedCount = countBefore - countAfter;

        return "Pruning complete. Removed " + removedCount + " messages.";
    }
    
    @AIToolMethod(value = "Prunes one or several parts from a given message. IMPORTANT: If a FunctionCall or FunctionResponse part is targeted, its corresponding paired part (FunctionResponse or FunctionCall) is **automatically resolved and pruned as well** by the system to maintain conversation integrity. The model only needs to specify one part of the pair.", requiresApproval = true)
    public static String pruneParts(
            @AIToolParam("The unique, stable UUIDs of the ChatMessage containing the parts.") String messageUID,
            @AIToolParam("A list of zero-based indices of the parts to remove.") List<Number> parts,
            @AIToolParam("A brief rationale for why the parts are being removed.") String reason
    ) throws Exception {
        ContextManager.getCallingInstance().pruneParts(messageUID, parts, reason);
        return "Pruning of " + parts.size() + " part(s) from message " + messageUID + " complete.";
    }
    
    
    @AIToolMethod(value = "Prunes all FunctionCall and FunctionResponse parts associated with the given stateful resource IDs. This is the recommended method for removing stale or unnecessary stateful resources (e.g. file contents).", requiresApproval = true)
    public static String pruneStatefulResources(
            @AIToolParam("A list of sateful resource identifiers (e.g., absolute file paths) to be pruned from the context.") List<String> resourceIds,
            @AIToolParam("A brief rationale for why these resources are being removed.") String reason
    ) throws Exception {
        ContextManager.getCallingInstance().getResourceTracker().pruneStatefulResources(resourceIds);
        return "Pruning of " + resourceIds.size() + " stateful resource(s) complete.";
    }
    
    @AIToolMethod("Lists all entries (messages) in the context, including their stable IDs, roles, and a summary of their parts.")
    public static String listEntries() {
        return ContextManager.getCallingInstance().getSessionManager().getSummaryAsString();
    }
}
