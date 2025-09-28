package uno.anahata.gemini.ui.render;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.Scrollable;
import javax.swing.border.Border;
import org.apache.commons.text.StringEscapeUtils;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * V3 Renderer: Orchestrates the rendering of a {@link Content} object by
 * building a hierarchy of standard Swing components (JPanel, JLabel, etc.)
 * This provides robust and predictable layout.
 *
 * @author pablo-ai
 */
public class ComponentContentRenderer2 {

    public enum PartType {
        TEXT, FUNCTION_CALL, FUNCTION_RESPONSE
    }

    private final Map<PartType, PartRenderer> typeRendererMap;
    private final Map<Part, PartRenderer> instanceRendererMap;
    private final EditorKitProvider editorKitProvider;

    public ComponentContentRenderer2(EditorKitProvider editorKitProvider) {
        this.editorKitProvider = editorKitProvider;
        this.typeRendererMap = new HashMap<>();
        this.instanceRendererMap = new HashMap<>();

        typeRendererMap.put(PartType.TEXT, new TextPartRenderer());
        typeRendererMap.put(PartType.FUNCTION_CALL, new FunctionCallPartRenderer());
        typeRendererMap.put(PartType.FUNCTION_RESPONSE, new FunctionResponsePartRenderer());
    }

    public void registerRenderer(Part partInstance, PartRenderer renderer) {
        instanceRendererMap.put(partInstance, renderer);
    }

    public PartRenderer getDefaultRendererForType(PartType partType) {
        return typeRendererMap.get(partType);
    }

    public JComponent render(Content content, int contentIdx) {
        String role = content.role().orElse("model");

        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(getBorderForRole(role));

        JLabel header = new JLabel(String.format("%S [%d]", role, contentIdx));
        header.setOpaque(true);
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        header.setBackground(getBackgroundColor(role, true));
        header.setForeground(getForegroundColor(role));
        messagePanel.add(header, BorderLayout.NORTH);

        // FIX: Use GridBagLayout to properly constrain child component widths.
        JPanel contentPanel = new ScrollablePanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setOpaque(true);
        contentPanel.setBackground(getBackgroundColor(role, false));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        List<? extends Part> parts = content.parts().orElse(Collections.emptyList());
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                gbc.insets = new Insets(10, 0, 10, 0);
                JSeparator separator = new JSeparator();
                separator.setForeground(new Color(204, 204, 204));
                contentPanel.add(separator, gbc);
            }

            gbc.insets = new Insets(0, 0, 0, 0);

            Part part = parts.get(i);
            PartRenderer renderer = instanceRendererMap.getOrDefault(part, typeRendererMap.get(getPartType(part)));

            if (renderer != null) {
                String titleText = String.format("Part %d of %d", i + 1, parts.size());
                if (part.functionCall().isPresent()) {
                    FunctionCall fc = part.functionCall().get();
                    titleText += String.format(" - %s id:%s", StringEscapeUtils.escapeHtml4(fc.name().orElse("?")), StringEscapeUtils.escapeHtml4(fc.id().orElse("n/a")));
                } else if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    titleText += String.format(" - %s id:n/a", StringEscapeUtils.escapeHtml4(fr.name().orElse("?")));
                }
                JLabel partTitle = new JLabel(titleText);
                partTitle.setFont(partTitle.getFont().deriveFont(Font.ITALIC, 11f));
                partTitle.setForeground(Color.DARK_GRAY);
                contentPanel.add(partTitle, gbc);

                gbc.insets = new Insets(4, 0, 0, 0);
                JComponent partComponent = renderer.render(part, editorKitProvider);
                contentPanel.add(partComponent, gbc);

            } else {
                JLabel unsupportedLabel = new JLabel("Unsupported part type");
                contentPanel.add(unsupportedLabel, gbc);
            }
        }

        gbc.weighty = 1;
        contentPanel.add(Box.createVerticalGlue(), gbc);

        messagePanel.add(contentPanel, BorderLayout.CENTER);
        return messagePanel;
    }

    private PartType getPartType(Part part) {
        if (part.text().isPresent()) return PartType.TEXT;
        if (part.functionCall().isPresent()) return PartType.FUNCTION_CALL;
        if (part.functionResponse().isPresent()) return PartType.FUNCTION_RESPONSE;
        return null;
    }

    private Border getBorderForRole(String role) {
        switch (role.toLowerCase()) {
            case "user": return BorderFactory.createLineBorder(new Color(144, 198, 149), 2, true);
            case "function": return BorderFactory.createLineBorder(new Color(240, 173, 78), 2, true);
            default: return BorderFactory.createLineBorder(new Color(160, 195, 232), 2, true);
        }
    }

    private Color getBackgroundColor(String role, boolean isHeader) {
        switch (role.toLowerCase()) {
            case "user": return isHeader ? new Color(212, 237, 218) : new Color(233, 247, 239);
            case "function": return isHeader ? new Color(252, 248, 227) : new Color(255, 250, 240);
            default: return isHeader ? new Color(221, 234, 248) : new Color(240, 248, 255);
        }
    }

    private Color getForegroundColor(String role) {
        switch (role.toLowerCase()) {
            case "user": return new Color(21, 87, 36);
            case "function": return new Color(138, 109, 59);
            default: return new Color(0, 123, 255);
        }
    }

    public static class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public static class WrappingEditorPane extends JEditorPane implements Scrollable {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
