package uno.anahata.gemini.ui.render;

import java.awt.Dimension;
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.EditorKit;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * A utility class responsible for creating syntax-highlighted code block
 * components.
 *
 * @author pablo-ai
 */
@Slf4j
public final class CodeBlockRenderer {

    private CodeBlockRenderer() {
    }

    /**
     * Creates a syntax-highlighted JEditorPane for the given code, wraps it in
     * a JScrollPane, and returns the component.
     *
     * @param language The programming language for syntax highlighting.
     * @param code The source code to render.
     * @param editorKitProvider The provider to get the correct EditorKit from.
     * @return A JComponent (a JScrollPane containing the JEditorPane) ready to be displayed.
     */
    public static JComponent render(String language, String code, EditorKitProvider editorKitProvider) {
        if (editorKitProvider == null) {
            log.warn("EditorKitProvider is null. Cannot render code block.");
            return createFallbackPane(code);
        }

        JEditorPane codeEditor = new JEditorPane();
        codeEditor.setEditable(false);

        try {
            EditorKit kit = editorKitProvider.getEditorKitForLanguage(language);
            if (kit == null) {
                log.warn("No EditorKit found for language '" + language + "'. Falling back to plain text.");
                return createFallbackPane(code);
            }
            codeEditor.setEditorKit(kit);
            codeEditor.setText(code);
            
        } catch (Exception e) {
            log.warn("Failed to render code block for language '" + language + "'.", e);
            return createFallbackPane(code);
        }
        
        return codeEditor;
    }
    
    private static JComponent createFallbackPane(String code) {
        JTextArea fallbackEditor = new JTextArea(code, 10, 80);
        fallbackEditor.setEditable(false);
        fallbackEditor.setLineWrap(true);
        fallbackEditor.setWrapStyleWord(true);
        fallbackEditor.setBackground(new java.awt.Color(240, 240, 240));
        fallbackEditor.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));        
        
        return fallbackEditor;
    }
}
