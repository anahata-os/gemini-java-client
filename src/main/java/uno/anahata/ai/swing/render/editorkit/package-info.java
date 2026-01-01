/* Licensed under the Apache License, Version 2.0 */
/**
 * Provides an abstraction layer for supplying syntax highlighting engines (EditorKits) to the UI.
 * <p>
 * This package decouples the core UI from any specific syntax highlighting implementation. It defines
 * the {@link uno.anahata.ai.swing.render.editorkit.EditorKitProvider} interface, which allows a host
 * application (like an IDE) to inject its own, more powerful editor kits for rendering code blocks.
 * A {@link uno.anahata.ai.swing.render.editorkit.DefaultEditorKitProvider} is included for standalone
 * use, which provides basic HTML and RTF support.
 */
package uno.anahata.ai.swing.render.editorkit;