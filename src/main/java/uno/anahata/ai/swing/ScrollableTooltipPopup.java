/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A reusable utility class to display a scrollable JTextArea inside a JPopupMenu
 * when hovering over a target component.
 *
 * This is a refactored version of the logic previously in ContextHeatmapPanel.
 */
public class ScrollableTooltipPopup {

    private final JPopupMenu popup;
    private final JTextArea textArea;
    private final Timer showPopupTimer;
    private final Timer hidePopupTimer;
    private JComponent targetComponent;
    private String fullContent;

    public ScrollableTooltipPopup() {
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(scrollPane, BorderLayout.CENTER);

        showPopupTimer = new Timer(750, e -> showPopup());
        showPopupTimer.setRepeats(false);

        hidePopupTimer = new Timer(500, e -> popup.setVisible(false));
        hidePopupTimer.setRepeats(false);

        // Add mouse listeners to the popup components to prevent immediate hiding
        MouseAdapter popupMouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hidePopupTimer.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Check if the mouse is moving back to the target component
                Point p = SwingUtilities.convertPoint(popup, e.getPoint(), targetComponent);
                if (!targetComponent.getVisibleRect().contains(p)) {
                    hidePopupTimer.start();
                }
            }
        };
        popup.addMouseListener(popupMouseListener);
        textArea.addMouseListener(popupMouseListener);
        scrollPane.addMouseListener(popupMouseListener);
    }

    /**
     * Attaches the popup behavior to a target component.
     *
     * @param targetComponent The component that triggers the popup on hover.
     */
    public void attach(JComponent targetComponent) {
        this.targetComponent = targetComponent;
        MouseAdapter targetMouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (fullContent != null && !fullContent.isEmpty()) {
                    showPopupTimer.start();
                    hidePopupTimer.stop();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isMouseOverPopup(e.getPoint())) {
                    showPopupTimer.stop();
                    hidePopupTimer.start();
                }
            }
        };
        targetComponent.addMouseListener(targetMouseListener);
    }

    /**
     * Sets the content to be displayed in the popup.
     *
     * @param content The full text content.
     */
    public void setContent(String content) {
        this.fullContent = content;
    }

    private void showPopup() {
        if (fullContent == null || fullContent.isEmpty() || targetComponent == null) {
            return;
        }
        textArea.setText(fullContent);
        textArea.setCaretPosition(0);

        Point p = targetComponent.getMousePosition();
        if (p != null) {
            popup.show(targetComponent, p.x + 10, p.y + 10);
        }
    }

    private boolean isMouseOverPopup(Point targetPoint) {
        if (!popup.isVisible()) {
            return false;
        }
        Point popupPoint = SwingUtilities.convertPoint(targetComponent, targetPoint, popup);
        return popup.getBounds().contains(popupPoint);
    }
    
    public void hide() {
        popup.setVisible(false);
    }
}
