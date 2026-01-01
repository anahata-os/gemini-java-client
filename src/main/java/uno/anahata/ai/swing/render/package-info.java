/* Licensed under the Apache License, Version 2.0 */
/**
 * Provides a framework of specialized renderers for displaying different types of message parts.
 * <p>
 * This package is the core of the UI's ability to display a rich, interactive conversation history.
 * It follows a strategy pattern where the main {@link uno.anahata.ai.swing.render.ContentRenderer}
 * delegates the task of rendering each {@link com.google.genai.types.Part} to a specific
 * {@link uno.anahata.ai.swing.render.PartRenderer} implementation based on the part's type.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.swing.render.ContentRenderer}: The main orchestrator that builds the
 *       JComponent for a complete {@link uno.anahata.ai.ChatMessage}.</li>
 *
 *   <li>{@link uno.anahata.ai.swing.render.PartRenderer}: The interface that all specific renderers
 *       must implement.</li>
 *
 *   <li>{@link uno.anahata.ai.swing.render.TextPartRenderer}: Renders Markdown-formatted text,
 *       including support for tables and code blocks.</li>
 *
 *   <li>{@link uno.anahata.ai.swing.render.FunctionCallPartRenderer}: Renders a visually distinct
 *       representation of a tool call requested by the model.</li>
 *
 *   <li>{@link uno.anahata.ai.swing.render.BlobPartRenderer}: Renders binary data, displaying a
 *       thumbnail for images.</li>
 *
 *   <li>{@link uno.anahata.ai.swing.render.CodeBlockRenderer}: A utility for creating syntax-highlighted
 *       code snippets, which can be configured with IDE-specific editor kits.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.swing.render.editorkit}: Provides an abstraction for supplying
 *       syntax highlighting engines (EditorKits) to the UI.</li>
 * </ul>
 */
package uno.anahata.ai.swing.render;