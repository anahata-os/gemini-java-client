package uno.anahata.gemini.ui.render;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
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
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.Scrollable;
import javax.swing.border.Border;
import org.apache.commons.text.StringEscapeUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.ui.SwingChatConfig;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

public class ContentRenderer {

    private final Map<PartType, PartRenderer> typeRendererMap;
    private final Map<Part, PartRenderer> instanceRendererMap;
    private final EditorKitProvider editorKitProvider;
    private final SwingChatConfig.UITheme theme;

    public ContentRenderer(EditorKitProvider editorKitProvider, SwingChatConfig config) {
        this.editorKitProvider = editorKitProvider;
        this.theme = config.getTheme();
        this.typeRendererMap = new HashMap<>();
        this.instanceRendererMap = new HashMap<>();

        typeRendererMap.put(PartType.TEXT, new TextPartRenderer());
        typeRendererMap.put(PartType.FUNCTION_CALL, new FunctionCallPartRenderer(theme));
        typeRendererMap.put(PartType.FUNCTION_RESPONSE, new FunctionResponsePartRenderer(theme));
        typeRendererMap.put(PartType.BLOB, new BlobPartRenderer());
        typeRendererMap.put(PartType.CODE_EXECUTION_RESULT, new CodeExecutionResultPartRenderer());
        typeRendererMap.put(PartType.EXECUTABLE_CODE, new ExecutableCodePartRenderer());
    }

    public void registerRenderer(Part partInstance, PartRenderer renderer) {
        instanceRendererMap.put(partInstance, renderer);
    }

    public PartRenderer getDefaultRendererForType(PartType partType) {
        return typeRendererMap.get(partType);
    }

    public JComponent render(ChatMessage message, int contentIdx, ContextManager contextManager) {
        Content content = message.getContent();
        String role = content.role().orElse("model");
        List<? extends Part> parts = content.parts().orElse(Collections.emptyList());

        JPanel messagePanel = new JPanel(new BorderLayout());
        int tokenCount = message.getUsageMetadata() != null ? message.getUsageMetadata().totalTokenCount().orElse(0) : 0;
        messagePanel.setBorder(getBorderForRole(role, tokenCount));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(true);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        headerPanel.setBackground(getBackgroundColor(role, true));

        String headerText = String.format("<html><b>%S</b> [%d]", role, contentIdx);
        if ("model".equalsIgnoreCase(role)) {
            headerText += " <font color='#666666'>- " + StringEscapeUtils.escapeHtml4(message.getModelId()) + "</font>";
        } else if ("user".equalsIgnoreCase(role)) {
            headerText += " <font color='#666666'>- " + System.getProperty("user.name") + "</font>";
        }
        
        Optional<GenerateContentResponseUsageMetadata> usageOpt = Optional.ofNullable(message.getUsageMetadata());
        if (usageOpt.isPresent()) {
            headerText += String.format(" <font color='#888888'><i>(Tokens: %d)</i></font>", usageOpt.get().candidatesTokenCount().orElse(0));
        }
        headerText += "</html>";
        
        JLabel headerLabel = new JLabel(headerText);
        headerLabel.setForeground(getForegroundColor(role));
        headerPanel.add(headerLabel, BorderLayout.CENTER);

        JButton pruneButton = new JButton("X");
        pruneButton.setMargin(new Insets(0, 4, 0, 4));
        pruneButton.setToolTipText("Remove this message from the context");
        pruneButton.addActionListener(e -> contextManager.pruneMessages(
            Collections.singletonList(message.getId()), 
            "User pruned message from UI"
        ));
        headerPanel.add(pruneButton, BorderLayout.EAST);
        
        messagePanel.add(headerPanel, BorderLayout.NORTH);

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

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                gbc.insets = new Insets(10, 0, 10, 0);
                JSeparator separator = new JSeparator();
                separator.setForeground(new Color(204, 204, 204));
                contentPanel.add(separator, gbc);
            }

            gbc.insets = new Insets(0, 0, 0, 0);

            Part part = parts.get(i);
            PartRenderer renderer = instanceRendererMap.getOrDefault(part, typeRendererMap.get(PartType.from(part)));

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
                JLabel unsupportedLabel = new JLabel("Unsupported part type " + part);
                contentPanel.add(unsupportedLabel, gbc);
            }
        }
        
        if (message.getGroundingMetadata() != null) {
            gbc.insets = new Insets(10, 0, 0, 0);
            GroundingMetadataRenderer groundingRenderer = new GroundingMetadataRenderer(message.getGroundingMetadata(), theme);
            contentPanel.add(groundingRenderer, gbc);
        }

        gbc.weighty = 1;
        contentPanel.add(Box.createVerticalGlue(), gbc);

        messagePanel.add(contentPanel, BorderLayout.CENTER);
        return messagePanel;
    }

    private Border getBorderForRole(String role, int tokenCount) {
        Color baseColor;
        switch (role.toLowerCase()) {
            case "user":
                baseColor = theme.getUserBorder();
                break;
            case "model":
                baseColor = theme.getModelBorder();
                break;
            case "tool":
                baseColor = theme.getToolBorder();
                break;
            default:
                baseColor = theme.getDefaultBorder();
                break;
        }
        return BorderFactory.createLineBorder(baseColor, 2, true);
    }

    private Color getBackgroundColor(String role, boolean isHeader) {
        switch (role.toLowerCase()) {
            case "user": return isHeader ? theme.getUserHeaderBg() : theme.getUserContentBg();
            case "model": return isHeader ? theme.getModelHeaderBg() : theme.getModelContentBg();
            case "tool": return isHeader ? theme.getToolHeaderBg() : theme.getToolContentBg();
            default: return isHeader ? theme.getDefaultHeaderBg() : theme.getDefaultContentBg();
        }
    }

    private Color getForegroundColor(String role) {
        switch (role.toLowerCase()) {
            case "user": return theme.getUserHeaderFg();
            case "model": return theme.getModelHeaderFg();
            case "tool": return theme.getToolHeaderFg();
            default: return theme.getFontColor();
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
