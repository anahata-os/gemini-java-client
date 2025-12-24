/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render.editorkit;

import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.rtf.RTFEditorKit;

/**
 * A default implementation of EditorKitProvider that provides the standard
 * kits available in a vanilla Swing environment.
 */
public class DefaultEditorKitProvider implements EditorKitProvider {

    
    @Override
    public EditorKit getEditorKitForLanguage(String language) {
        if ("html".equalsIgnoreCase(language)) {
            return new HTMLEditorKit();
        }
        if ("rtf".equalsIgnoreCase(language)) {
            return new RTFEditorKit();
        }
        // For any other language, return null to signal that no specific
        // editor kit is available, allowing the renderer to use a plain-text fallback.
        return null;
    }
}