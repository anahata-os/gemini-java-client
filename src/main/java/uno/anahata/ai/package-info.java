/**
 * Provides the core classes for the Gemini Java client application.
 * <p>
 * This package contains the central orchestrators that manage the application's lifecycle,
 * configuration, and interaction with the Google Gemini API.
 *
 * <h2>Key Classes:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.Chat}: The main orchestrator of the chat. It manages the
 *       conversation loop, builds system instructions from various providers, handles API
 *       retries, and processes function calls and responses.</li>
 *
 *   <li>{@link uno.anahata.gemini.GeminiConfig}: An abstract base class for host-specific
 *       configurations. It allows the client to be adapted to different environments by
 *       providing API keys, working folder locations, and function confirmation preferences.</li>
 *
 *   <li>{@link uno.anahata.gemini.GeminiAPI}: Manages the underlying Google GenAI client,
 *       handles API key pooling (round-robin), and model selection to ensure robust and
 *       efficient communication with the Gemini service.</li>
 *
 *   <li>{@link uno.anahata.gemini.ChatMessage}: A rich, stateful representation of a single
 *       message in the chat history. It serves as the core data model, encapsulating not
 *       just the content but also metadata and dependency information between message parts.</li>
 *
 *   <li>{@link uno.anahata.gemini.Executors}: A utility class providing a shared, cached
 *       thread pool for executing asynchronous tasks within the client.</li>
 * </ul>
 */
package uno.anahata.ai;
