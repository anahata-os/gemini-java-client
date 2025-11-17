/**
 * Provides the core classes and subpackages for managing the chat conversation's state and history.
 * <p>
 * This package is the heart of the client's state management. It is responsible for
 * maintaining the history of the conversation, handling session persistence, tracking
 * stateful resources, and performing context pruning to manage token limits.
 *
 * <h2>Core Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.context.ContextManager}: The central orchestrator for all
 *       context-related activities. It holds the list of {@link uno.anahata.ai.ChatMessage}s
 *       and delegates specialized tasks such as session management, resource tracking, and
 *       pruning to dedicated components in its subpackages.</li>
 *
 *   <li>{@link uno.anahata.ai.context.ContextListener}: A simple interface that allows
 *       other components (like the UI) to listen for and react to any changes in the
 *       chat context, ensuring the view is always synchronized with the state.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@code provider}: Contains interfaces and implementations for providers that supply just-in-time, high-salience context to the model on each turn.</li>
 *   <li>{@code pruning}: Implements the logic for intelligently removing parts or entire messages from the context to stay within token limits.</li>
 *   <li>{@code session}: Handles the saving and loading of entire chat sessions using Kryo serialization.</li>
 *   <li>{@code stateful}: Provides the framework for tracking and managing local resources (like files) that have a persistent state outside the chat context itself.</li>
 * </ul>
 */
package uno.anahata.ai.context;
