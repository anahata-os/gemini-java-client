package uno.anahata.gemini.functions.spi;

import java.io.IOException;
import java.util.List;
import uno.anahata.gemini.ContextManager;
import uno.anahata.gemini.functions.AITool;

/**
 * A tool for managing chat sessions.
 */
public class Session {
    @AITool("Saves the current chat session to a file.")
    public static String saveSession(String name) throws IOException {
        return ContextManager.get().saveSession(name);
    }

    @AITool("Lists all saved chat sessions.")
    public static List<String> listSavedSessions() throws IOException {
        return ContextManager.get().listSavedSessions();
    }

    @AITool("Loads a chat session from a file.")
    public static void loadSession(String id) throws IOException {
        ContextManager.get().loadSession(id);
    }
}
