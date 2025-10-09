package uno.anahata.gemini.ui;

import java.awt.Color;
import java.awt.Rectangle;
import uno.anahata.gemini.ChatMessage;

/**
 * A data class to hold the calculated geometry and styling for a single
 * rectangle within the ContextHeatmapPanel.
 * @author Anahata
 */
public class HeatmapRect {
    public final Rectangle bounds;
    public final Color color;
    public final ChatMessage message;

    public HeatmapRect(Rectangle bounds, Color color, ChatMessage message) {
        this.bounds = bounds;
        this.color = color;
        this.message = message;
    }
}
