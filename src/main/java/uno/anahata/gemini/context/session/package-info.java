/**
 * Manages the persistence of chat sessions.
 * <p>
 * This package contains the {@link uno.anahata.gemini.context.session.SessionManager},
 * which handles the saving and loading of the entire chat context to and from the
 * file system. It uses the efficient Kryo serialization library to persist the state
 * of the conversation, allowing users to resume their work across application restarts.
 * It also manages the automatic backup of the current session.
 */
package uno.anahata.gemini.context.session;
