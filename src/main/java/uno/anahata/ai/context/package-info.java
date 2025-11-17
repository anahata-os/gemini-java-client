/**
 * Provides the classes and subpackages for managing the chat conversation context.
 * <p>
 * This package is the heart of the client's state management. It is responsible for
 * maintaining the history of the conversation, handling session persistence, tracking
 * stateful resources, and performing context pruning to manage token limits.
 *
 * <h2>Core Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.context.ContextManager}: The central class for context
 *       management. It holds the list of {@link uno.anahata.gemini.ChatMessage}s and
 *       delegates specific responsibilities to other managers in its subpackages.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.ContextListener}: An interface for components
 *       that need to be notified of changes to the chat context.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.context.history}: Manages the logging of every
 *       context entry to the file system for auditing and debugging.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.pruning}: Contains the logic for
 *       intelligently removing parts or entire messages from the context to stay
 *       within token limits, including automatic pruning of old tool calls.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.session}: Handles the saving and loading
 *       of entire chat sessions using Kryo serialization.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.stateful}: Provides the framework for
 *       tracking and managing resources (like files) that have a persistent state
 *       outside the chat context itself.</li>
 * </ul>
 */
package uno.anahata.ai.context;
