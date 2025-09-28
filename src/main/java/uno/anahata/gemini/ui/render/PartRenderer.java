package uno.anahata.gemini.ui.render;

import com.google.genai.types.Part;
import javax.swing.JComponent;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * An interface for rendering a specific {@link Part} of a model's response
 * into a JComponent. This is part of the V3 "Swing Native" rendering pipeline.
 *
 * @author pablo-ai
 */
public interface PartRenderer {

    /**
     * Renders a given Part into a JComponent.
     *
     * @param part The Part to render.
     * @param editorKitProvider A provider for syntax highlighting, if needed.
     * @return A JComponent representing the rendered Part.
     */
    JComponent render(Part part, EditorKitProvider editorKitProvider);

}
