package uno.anahata.gemini.ui;

import com.google.genai.types.Part;
import com.google.genai.types.FunctionResponse;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.context.stateful.ResourceTracker;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.ui.render.PartType;

/**
 * A panel that visualizes the chat context as a vertical, scrollable list of
 * parts, ordered by size.
 *
 * @author Anahata
 */
public class ContextHeatmapPanel extends JPanel {

    private static final Gson GSON = GsonUtils.getGson();
    private final JPanel containerPanel;
    private FunctionManager functionManager;

    public ContextHeatmapPanel() {
        super(new BorderLayout());
        containerPanel = new JPanel();
        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(containerPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24); // Increase scroll speed
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    public void updateContext(List<ChatMessage> newContext) {
        rebuildView(newContext);
    }

    private long getPartSizeInBytes(Part part) {
        if (part.text().isPresent()) {
            return part.text().get().getBytes(StandardCharsets.UTF_8).length;
        }
        if (part.inlineData().isPresent()) {
            return part.inlineData().get().data().get().length;
        }
        if (part.functionResponse().isPresent()) {
            Object response = part.functionResponse().get().response();
            if (response != null) {
                return response.toString().length();
            } else {
                return 0;
            }
            
        }
        return GSON.toJson(part).getBytes(StandardCharsets.UTF_8).length;
    }

    private void rebuildView(List<ChatMessage> context) {
        containerPanel.removeAll();

        if (context == null || context.isEmpty()) {
            containerPanel.revalidate();
            containerPanel.repaint();
            return;
        }

        List<PartInfo> allParts = new ArrayList<>();
        for (int i = 0; i < context.size(); i++) {
            ChatMessage message = context.get(i);
            if (message.getContent() != null && message.getContent().parts().isPresent()) {
                List<Part> parts = message.getContent().parts().get();
                for (int j = 0; j < parts.size(); j++) {
                    Part part = parts.get(j);
                    allParts.add(new PartInfo(part, message, i, j, getPartSizeInBytes(part)));
                }
            }
        }

        if (allParts.isEmpty()) {
            containerPanel.revalidate();
            containerPanel.repaint();
            return;
        }

        allParts.sort(Comparator.comparingLong(PartInfo::getSizeInBytes).reversed());

        for (PartInfo partInfo : allParts) {
            containerPanel.add(new HeatmapItemPanel(partInfo));
        }

        containerPanel.revalidate();
        containerPanel.repaint();
    }

    private Color getColorForRole(String role) {
        switch (role.toLowerCase()) {
            case "user":
                return new Color(212, 237, 218);
            case "model":
                return new Color(221, 234, 248);
            case "tool":
            case "function":
                return new Color(223, 213, 235);
            default:
                return Color.LIGHT_GRAY;
        }
    }

    /**
     * A panel for a single item in the heatmap list.
     */
    private class HeatmapItemPanel extends JPanel {

        private final PartInfo partInfo;
        private final String resourceIdFilename;

        HeatmapItemPanel(PartInfo partInfo) {
            this.partInfo = partInfo;
            this.resourceIdFilename = extractResourceIdFilename(partInfo.getPart());
            setBackground(getColorForRole(partInfo.getMessage().getContent().role().orElse("unknown")));
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
            setToolTipText(buildTooltip());
        }

        private String extractResourceIdFilename(Part part) {
            if (part.functionResponse().isPresent() && functionManager != null) {
                return ResourceTracker.getResourceIdIfStateful(part.functionResponse().get(), functionManager)
                    .map(fullPath -> {
                        int lastSeparator = fullPath.lastIndexOf(File.separator);
                        if (lastSeparator != -1 && lastSeparator < fullPath.length() - 1) {
                            return fullPath.substring(lastSeparator + 1);
                        }
                        return fullPath;
                    })
                    .orElse(null);
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 40);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.BLACK);

            FontMetrics fm = g2d.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            int x = 5;

            String info = String.format("Msg Idx: %d  |  Part Idx: %d  |  Type: %s  |  Size: %,d bytes",
                partInfo.getMessageIndex(),
                partInfo.getPartIndex(),
                PartType.from(partInfo.getPart()),
                partInfo.getSizeInBytes()
            );

            if (StringUtils.isNotBlank(resourceIdFilename)) {
                info += "  |  Resource: " + resourceIdFilename;
            }

            g2d.drawString(info, x, y);
            g2d.dispose();
        }

        private String buildTooltip() {
            Part part = partInfo.getPart();
            StringBuilder tooltip = new StringBuilder("<html>");

            part.text().ifPresent(text -> {
                String escaped = StringEscapeUtils.escapeHtml4(StringUtils.abbreviate(text, 200));
                tooltip.append("<b>Text:</b><pre>").append(escaped).append("</pre>");
            });

            part.inlineData().ifPresent(data -> {
                tooltip.append("<b>MIME Type:</b> ").append(data.mimeType()).append("<br>");
                tooltip.append("<b>Size:</b> ").append(String.format("%,d bytes", data.data().get().length));
            });

            part.functionCall().ifPresent(fc -> {
                String escaped = StringEscapeUtils.escapeHtml4(GSON.toJson(fc));
                tooltip.append("<b>Function Call:</b><pre>").append(escaped).append("</pre>");
            });

            part.functionResponse().ifPresent(fr -> {
                Optional<String> resourceIdOpt = functionManager != null ? ResourceTracker.getResourceIdIfStateful(fr, functionManager) : Optional.empty();

                // If it's a stateful file, just show the ID.
                if (resourceIdOpt.isPresent()) {
                    tooltip.append("<b>Stateful Resource ID:</b> ").append(StringEscapeUtils.escapeHtml4(resourceIdOpt.get()));
                } else {
                    // For other responses, show an abbreviated pretty-printed JSON.
                    String jsonResponse = GsonUtils.prettyPrint(fr);
                    String escaped = StringEscapeUtils.escapeHtml4(StringUtils.abbreviate(jsonResponse, 500)); // Abbreviate to 500 chars
                    tooltip.append("<b>Function Response:</b><pre>").append(escaped).append("</pre>");
                }
            });

            tooltip.append("</html>");
            return tooltip.toString();
        }
    }

    /**
     * Data class to hold information about each part for rendering.
     */
    private static class PartInfo {

        private final Part part;
        private final ChatMessage message;
        private final int messageIndex;
        private final int partIndex;
        private final long sizeInBytes;

        public PartInfo(Part part, ChatMessage message, int messageIndex, int partIndex, long sizeInBytes) {
            this.part = part;
            this.message = message;
            this.messageIndex = messageIndex;
            this.partIndex = partIndex;
            this.sizeInBytes = sizeInBytes;
        }

        public Part getPart() { return part; }
        public ChatMessage getMessage() { return message; }
        public int getMessageIndex() { return messageIndex; }
        public int getPartIndex() { return partIndex; }
        public long getSizeInBytes() { return sizeInBytes; }
    }
}