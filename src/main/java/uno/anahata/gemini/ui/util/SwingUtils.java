package uno.anahata.gemini.ui.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.JScrollPane;

/**
 * General Swing utility methods.
 */
public final class SwingUtils {

    private SwingUtils() {
        // Prevent instantiation
    }

    /**
     * Gets the index of the first component within a JScrollPane's viewport that is currently visible.
     *
     * @param scrollPane The JScrollPane to inspect.
     * @return The index of the first visible component, or -1 if no components are visible or the viewport is not a Container.
     */
    public static int getTopmostVisibleComponentIndex(JScrollPane scrollPane) {
        if (!(scrollPane.getViewport().getView() instanceof Container)) {
            return -1;
        }

        Container view = (Container) scrollPane.getViewport().getView();
        if (view.getComponentCount() == 0) {
            return -1;
        }

        Rectangle viewRect = scrollPane.getViewport().getViewRect();
        Component[] components = view.getComponents();

        for (int i = 0; i < components.length; i++) {
            if (components[i].getBounds().intersects(viewRect)) {
                return i;
            }
        }

        return -1;
    }
}
