package uno.anahata.gemini.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.apache.commons.text.StringEscapeUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.internal.PartUtils;

/**
 * A panel that visualizes the chat context as a treemap, where each message's
 * area is proportional to its token count.
 * @author Anahata
 */
public class ContextHeatmapPanel extends JPanel {

    private List<ChatMessage> currentContext = new ArrayList<>();
    private List<HeatmapRect> rects = new ArrayList<>();

    public ContextHeatmapPanel() {
        setToolTipText(""); // Enable tooltips
    }

    public void updateContext(List<ChatMessage> newContext) {
        this.currentContext = new ArrayList<>(newContext);
        recalculateLayout();
        repaint();
    }

    private void recalculateLayout() {
        rects.clear();
        if (currentContext.isEmpty()) {
            return;
        }

        long totalTokens = currentContext.stream()
            .mapToLong(msg -> msg.getUsageMetadata() != null ? msg.getUsageMetadata().totalTokenCount().orElse(0) : 50) // 50 for user
            .sum();

        if (totalTokens == 0) {
            return;
        }

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        // Simple slice-and-dice layout
        int currentX = 0;
        for (ChatMessage message : currentContext) {
            long messageTokens = message.getUsageMetadata() != null ? message.getUsageMetadata().totalTokenCount().orElse(0) : 50;
            double percentage = (double) messageTokens / totalTokens;
            int rectWidth = (int) (panelWidth * percentage);

            Rectangle bounds = new Rectangle(currentX, 0, rectWidth, panelHeight);
            Color color = getColorForRole(message.getContent().role().orElse("unknown"));
            rects.add(new HeatmapRect(bounds, color, message));

            currentX += rectWidth;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getWidth() != (rects.isEmpty() ? 0 : rects.get(rects.size() - 1).bounds.x + rects.get(rects.size() - 1).bounds.width)) {
            recalculateLayout();
        }
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (HeatmapRect rect : rects) {
            g2d.setColor(rect.color);
            g2d.fill(rect.bounds);
            g2d.setColor(rect.color.darker());
            g2d.draw(rect.bounds);
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        for (HeatmapRect rect : rects) {
            if (rect.bounds.contains(event.getPoint())) {
                ChatMessage msg = rect.message;
                long tokens = msg.getUsageMetadata() != null ? msg.getUsageMetadata().totalTokenCount().orElse(0) : 0;
                String role = msg.getContent().role().orElse("?");
                String summary = msg.getContent().parts().isPresent() ? PartUtils.summarize(msg.getContent().parts().get().get(0)) : "No parts";

                return String.format("<html><b>Role:</b> %s<br><b>Tokens:</b> %d<br><b>Summary:</b> %s</html>",
                    StringEscapeUtils.escapeHtml4(role),
                    tokens,
                    StringEscapeUtils.escapeHtml4(summary)
                );
            }
        }
        return null;
    }

    private Color getColorForRole(String role) {
        switch (role.toLowerCase()) {
            case "user": return new Color(212, 237, 218);
            case "model": return new Color(221, 234, 248);
            case "tool": return new Color(223, 213, 235);
            default: return Color.LIGHT_GRAY;
        }
    }
}
