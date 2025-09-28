package uno.anahata.gemini.ui.render;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.EditorKit;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * A utility class responsible for creating syntax-highlighted code block
 * components.
 *
 * @author pablo-ai
 */
public final class CodeBlockRenderer {

    private static final Logger logger = Logger.getLogger(CodeBlockRenderer.class.getName());

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
            logger.warning("EditorKitProvider is null. Cannot render code block.");
            return createFallbackPane(code);
        }

        JEditorPane codeEditor = new JEditorPane();
        codeEditor.setEditable(false);

        try {
            EditorKit kit = editorKitProvider.getEditorKitForLanguage(language);
            if (kit == null) {
                logger.warning("No EditorKit found for language '" + language + "'. Falling back to plain text.");
                return createFallbackPane(code);
            }
            codeEditor.setEditorKit(kit);
            codeEditor.setText(code);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to render code block for language '" + language + "'.", e);
            return createFallbackPane(code);
        }
        /*
        // THE FIX: Eliminate the race condition by forcing a re-layout after the
        // EditorKit has asynchronously calculated the component\'s true size.
        SwingUtilities.invokeLater(() -> {
            // Explicitly set preferred size based on current content after EditorKit has laid it out
            codeEditor.setPreferredSize(codeEditor.getPreferredSize());
            codeEditor.updateUI();
            if (codeEditor.getParent() != null) {
                codeEditor.getParent().revalidate();
                codeEditor.getParent().repaint();
            }
        });
*/
        return codeEditor;
    }
    
    private static JComponent createFallbackPane(String code) {
        JEditorPane fallbackEditor = new JEditorPane("text/plain", code);
        fallbackEditor.setEditable(false);
        fallbackEditor.setBackground(new java.awt.Color(240, 240, 240));
        fallbackEditor.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        return new JScrollPane(fallbackEditor);
    }
}
