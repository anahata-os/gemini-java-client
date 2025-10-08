package uno.anahata.gemini.functions.spi;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import uno.anahata.gemini.ContextManager;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;

/**
 * V2: A tool for the model to inspect and manipulate its own context window.
 * This version uses stable, unique IDs for pruning, ensuring robust context management.
 * @author Anahata
 */
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

    @AIToolMethod(value = "Prunes the context window by removing specific ChatMessage entries using their stable IDs. This is the primary mechanism for managing context size.", requiresApproval = true)
    public static String pruneContext(
            @AIToolParam("A brief rationale for why the messages are being removed. This is for logging and user visibility.") String rationale,
            @AIToolParam("An array of the unique, stable IDs of the ChatMessages to be removed.") String[] ids
    ) throws Exception {
        GeminiChat chat = GeminiChat.currentChat.get();
        if (chat == null) {
            return "Error: Could not get current chat context from ThreadLocal.";
        }

        ContextManager cm = chat.getContextManager();
        int countBefore = cm.getContext().size();

        for (String id : ids) {
            cm.pruneById(id);
        }
        
        // Notify the UI that the context has changed so it can perform a full refresh.
        cm.notifyHistoryChange();

        int countAfter = cm.getContext().size();
        int removedCount = countBefore - countAfter;

        File historyFolder = GeminiConfig.getWorkingFolder("history");
        SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        File pruneSummaryfile = new File(historyFolder, timestamp + "-prune.md");
        String summary = "Rationale: " + rationale + "\n\nRemoved IDs:\n" + String.join("\n", ids);
        Files.writeString(pruneSummaryfile.toPath(), summary);

        return "Pruning complete. Removed " + removedCount + " ChatMessage entries. "
                + "\nTotal History entries: Before: " + countBefore + ", After: " + countAfter + ". "
                + "\nPruned content summary saved to: " + pruneSummaryfile.getAbsolutePath();
    }

    @AIToolMethod("Lists all entries in the context, including their stable IDs, roles, and a summary of their parts.")
    public static String listEntries() {
        return ContextManager.get().getSummaryAsString();
    }
}
