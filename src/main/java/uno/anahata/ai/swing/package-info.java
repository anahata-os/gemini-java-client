/**
 * Provides a comprehensive and well-structured Swing-based user interface for the AI assistant.
 * <p>
 * This package is highly modular, with a clear separation of concerns between different UI components,
 * data models, and rendering logic.
 *
 * <h2>Key Architectural Concepts:</h2>
 * <ul>
 *     <li><b>Main Entry Point:</b> {@link uno.anahata.ai.swing.Main} is the application's entry point,
 *     responsible for setting up the main {@link javax.swing.JFrame} and initializing the core
 *     {@link uno.anahata.ai.swing.AnahataPanel}.</li>
 *
 *     <li><b>Core UI Component:</b> {@link uno.anahata.ai.swing.AnahataPanel} is the central UI container,
 *     bringing together all the other panels (conversation, status, input, etc.) into a cohesive
 *     user experience.</li>
 *
 *     <li><b>Conversation Display:</b> {@link uno.anahata.ai.swing.ConversationPanel} is responsible for
 *     displaying the chat history. It uses a sophisticated rendering system to handle different types
 *     of message parts (text, code, function calls, etc.).</li>
 *
 *     <li><b>Part Rendering:</b> The {@link uno.anahata.ai.swing.render} package provides a flexible and
 *     extensible rendering framework. {@link uno.anahata.ai.swing.render.ContentRenderer} iterates
 *     through the parts of a {@link uno.anahata.ai.ChatMessage} and delegates the rendering of each part
 *     to a specialized {@link uno.anahata.ai.swing.render.PartRenderer} (e.g.,
 *     {@link uno.anahata.ai.swing.render.TextPartRenderer},
 *     {@link uno.anahata.ai.swing.render.CodeBlockRenderer},
 *     {@link uno.anahata.ai.swing.render.FunctionCallPartRenderer}). This allows for easy addition of
 *     new part types without modifying the core rendering logic.</li>
 *
 *     <li><b>Configuration and Settings:</b> The {@link uno.anahata.ai.swing.config} package provides UI
 *     panels for configuring the application, such as {@link uno.anahata.ai.swing.GeminiKeysPanel} for
 *     managing API keys and {@link uno.anahata.ai.swing.SystemInstructionsPanel} for editing the AI's
 *     core instructions.</li>
 *
 *     <li><b>Context Visualization:</b> {@link uno.anahata.ai.swing.ContextHeatmapPanel} and
 *     {@link uno.anahata.ai.swing.ContextUsageBar} provide visual representations of the context
 *     window's state, helping the user understand the conversation's memory usage.</li>
 *
 *     <li><b>Tool Integration:</b> {@link uno.anahata.ai.swing.FunctionsPanel} allows the user to manage
 *     the permissions for each available tool, while {@link uno.anahata.ai.swing.SwingFunctionPrompter}
 *     provides a UI for confirming tool executions.</li>
 *
 *     <li><b>Status and Feedback:</b> {@link uno.anahata.ai.swing.StatusPanel} displays the real-time
 *     status of the application, providing feedback on API calls, tool executions, and other
 *     background processes.</li>
 *
 *     <li><b>Utilities:</b> The {@link uno.anahata.ai.swing.util} package contains various helper
 *     classes for Swing-related tasks, such as {@link uno.anahata.ai.swing.util.SwingUtils} for
 * "common UI operations.</li>
 * </ul>
 */
package uno.anahata.ai.swing;
