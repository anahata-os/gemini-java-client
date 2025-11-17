/**
 * Provides the core, high-level classes that orchestrate the AI chat application.
 * <p>
 * This package contains the central components responsible for managing the application's
 * lifecycle, configuration, and the primary interaction loop with the Gemini API. It
 * forms the foundational layer of the AI assistant's operation.
 *
 * <h2>Key Classes:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.Chat}: The main engine of the application. It orchestrates the
 *       entire chat session, managing the conversation loop, processing user input,
 *       handling tool calls, interacting with the context, and communicating with the
 *       generative model.</li>
 *
 *   <li>{@link uno.anahata.ai.ChatMessage}: A rich, stateful representation of a single
 *       message within the chat history. It serves as the core data model, encapsulating
 *       the content, role, usage metadata, and crucially, the dependency relationships
 *       between different parts of a message (e.g., a FunctionCall and its corresponding
 *       FunctionResponse).</li>
 *
 *   <li>{@link uno.anahata.ai.AnahataConfig}: A utility class for managing global application
 *       paths, specifically locating and creating the central working directory
 *       ({@code ~/.anahata/ai-assistant}).</li>
 *
 *   <li>{@link uno.anahata.ai.AnahataExecutors}: A factory for creating dedicated, session-specific
 *       cached thread pools. Threads created are daemon threads, ensuring they do not
 *       prevent the application from shutting down.</li>
 *
 *   <li>{@link uno.anahata.ai.Executors}: Provides a general-purpose, shared, cached thread
 *       pool with non-daemon threads for background tasks not tied to a specific chat
 *       session's lifecycle.</li>
 *
 *   <li>{@link uno.anahata.ai.MessageRole}: A type-safe enum representing the author of a
 *       message (USER, MODEL, or TOOL).</li>
 * </ul>
 */
package uno.anahata.ai;
