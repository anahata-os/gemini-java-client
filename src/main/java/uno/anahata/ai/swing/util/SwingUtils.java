/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.function.Function;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * General Swing utility methods.
 */
public final class SwingUtils {

    private SwingUtils() {
        // Prevent instantiation
    }

    /**
     * A generic data class to hold the state of a scroll pane's viewport, anchored to a stable model object.
     * @param <T> The type of the anchor object.
     */
    @Getter
    @AllArgsConstructor
    public static class ScrollState<T> {
        /** The stable model object that identifies the component at the user's focus. */
        private final T anchor;
        /** The precise pixel offset from the top of the anchored component to the top of the viewport. */
        private final int offset;
    }

    /**
     * Captures the precise scroll state of a JScrollPane. It identifies the component closest to the center of the
     * viewport as the user's focus, extracts a stable anchor object from it using the provided function, and records
     * the precise pixel offset.
     *
     * @param <T>           The type of the anchor object.
     * @param scrollPane    The JScrollPane to inspect.
     * @param anchorExtractor A function that takes a Component and returns a stable, non-UI anchor object (e.g., a
     *                      data model object).
     * @return A {@link ScrollState} object, or {@code null} if the state could not be determined.
     */
    public static <T> ScrollState<T> getScrollState(JScrollPane scrollPane, Function<Component, T> anchorExtractor) {
        JViewport viewport = scrollPane.getViewport();
        if (!(viewport.getView() instanceof Container)) {
            return null;
        }

        Container view = (Container) viewport.getView();
        if (view.getComponentCount() == 0) {
            return null;
        }

        Rectangle viewRect = viewport.getViewRect();
        int viewportCenterY = viewRect.y + viewRect.height / 2;

        Component centerMostComponent = null;
        int minDistance = Integer.MAX_VALUE;

        // Find the component whose center is closest to the viewport's center.
        for (Component comp : view.getComponents()) {
            if (comp.getBounds().intersects(viewRect)) {
                int componentCenterY = comp.getY() + comp.getHeight() / 2;
                int distance = Math.abs(viewportCenterY - componentCenterY);
                if (distance < minDistance) {
                    minDistance = distance;
                    centerMostComponent = comp;
                }
            }
        }

        if (centerMostComponent != null) {
            T anchor = anchorExtractor.apply(centerMostComponent);
            if (anchor != null) {
                int offset = viewRect.y - centerMostComponent.getY();
                return new ScrollState<>(anchor, offset);
            }
        }

        return null;
    }
}
