/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi;

import java.io.IOException;
import java.util.List;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.tools.AIToolMethod;

/**
 * A tool provider for managing chat sessions using Kryo serialization.
 * <p>
 * These tools allow the model to persist the current conversation history
 * to disk and reload it later, enabling long-running or multi-stage tasks.
 * </p>
 */
public class Session {
    
    /**
     * Saves the current conversation history to a file.
     *
     * @param name The name of the session (used as the filename).
     * @return A success message.
     * @throws IOException if an I/O error occurs.
     */
    @AIToolMethod("Saves the current chat session to a file using Kryo serialization.")
    public static String saveSession(String name) throws IOException {
        return ContextManager.getCallingInstance().getSessionManager().saveSession(name);
    }

    /**
     * Lists all available saved chat sessions.
     *
     * @return A list of session names.
     * @throws IOException if an I/O error occurs.
     */
    @AIToolMethod("Lists all saved chat sessions.")
    public static List<String> listSavedSessions() throws IOException {
        return ContextManager.getCallingInstance().getSessionManager().listSavedSessions();
    }

    /**
     * Loads a conversation history from a saved session file.
     *
     * @param id The name of the session to load.
     * @throws IOException if the session file is not found or an error occurs.
     */
    @AIToolMethod("Loads a chat session from a file.")
    public static void loadSession(String id) throws IOException {
        ContextManager.getCallingInstance().getSessionManager().loadSession(id);
    }
}