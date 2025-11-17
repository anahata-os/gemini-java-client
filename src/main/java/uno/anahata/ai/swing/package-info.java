/**
 * Provides the Swing-based user interface for the Gemini Java client.
 * <p>
 * This package contains all the necessary components to build and run a standalone chat application.
 * It is designed to be embeddable, allowing the chat interface to be integrated into larger host
 * applications like an IDE.
 *
 * <h2>Core UI Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.ui.GeminiPanel}: The main, self-contained Swing component that houses
 *       the entire chat interface, including the toolbar, chat history, input area, and configuration tabs.</li>
 *
 *   <li>{@link uno.anahata.gemini.ui.ChatPanel}: Manages the display of the conversation history and the
 *       user input area.</li>
 *
 *   <li>{@link uno.anahata.gemini.ui.ContentRenderer}: The master renderer responsible for displaying
 *       a {@link uno.anahata.gemini.ChatMessage} by delegating to specific part renderers.</li>
 *
 *   <li>{@link uno.anahata.gemini.ui.SwingFunctionPrompter}: A JDialog-based implementation of the
 *       {@link uno.anahata.gemini.functions.FunctionPrompter} interface, providing a user-friendly
 *       way to confirm or deny tool execution.</li>
 *
 *   <li>{@link uno.anahata.gemini.ui.Main}: The entry point for launching the client as a standalone
 *       desktop application.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.ui.functions.spi}: Contains UI-specific tool implementations.</li>
 *   <li>{@link uno.anahata.gemini.ui.instructions}: Contains UI-specific system instruction providers.</li>
 *   <li>{@link uno.anahata.gemini.ui.render}: Contains the specialized renderers for different types
 *       of message parts (text, images, function calls, etc.).</li>
 * </ul>
 */
package uno.anahata.ai.swing;
