package uno.anahata.gemini.functions.spi;

import java.io.IOException;
import java.util.List;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.functions.AIToolMethod;

/**
 * A tool for managing chat sessions.
 */
public class Session {
    @AIToolMethod("Saves the current chat session to a file.")
    public static String saveSession(String name) throws IOException {
        return ContextManager.getCallingInstance().saveSession(name);
    }

    @AIToolMethod("Lists all saved chat sessions.")
    public static List<String> listSavedSessions() throws IOException {
        return ContextManager.getCallingInstance().listSavedSessions();
    }

    @AIToolMethod("Loads a chat session from a file.")
    public static void loadSession(String id) throws IOException {
        ContextManager.getCallingInstance().loadSession(id);
    }
}
