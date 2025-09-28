package uno.anahata.gemini.ui.render.editorkit;

import javax.swing.text.EditorKit;

/**
 * An interface for providing an EditorKit for a given programming language.
 * This allows the generic UI to be configured with IDE-specific editor kits.
 */
public interface EditorKitProvider {

    /**
     * Gets a Swing EditorKit suitable for rendering the specified language.
     *
     * @param language The programming language (e.g., "java", "html", "xml").
     * @return An EditorKit instance, or null if no specific kit is available.
     */
    EditorKit getEditorKitForLanguage(String language);
}
