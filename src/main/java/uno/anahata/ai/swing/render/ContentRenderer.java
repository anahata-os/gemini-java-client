/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import uno.anahata.ai.Chat;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.swing.ChatPanel;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;
import uno.anahata.ai.swing.TimeUtils;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;
import javax.xml.bind.DatatypeConverter;

public class ContentRenderer {

    private final Map<PartType, PartRenderer> typeRendererMap;
    private final Map<Part, PartRenderer> instanceRendererMap;
    private final EditorKitProvider editorKitProvider;
    private final ChatPanel chatPanel;
    private final Chat chat;

    public ContentRenderer(ChatPanel chatPanel) {
        this.chatPanel = chatPanel;
        this.chat = chatPanel.getChat();
        this.editorKitProvider = chatPanel.getEditorKitProvider();
        this.typeRendererMap = new HashMap<>();
        this.instanceRendererMap = new HashMap<>();

        typeRendererMap.put(PartType.TEXT, new TextPartRenderer());
        typeRendererMap.put(PartType.FUNCTION_CALL, new FunctionCallPartRenderer());
        typeRendererMap.put(PartType.FUNCTION_RESPONSE, new FunctionResponsePartRenderer());
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

    public JComponent render(ChatMessage message) {
        Content content = message.getContent();
        String role = content.role().orElse("model");
        List<? extends Part> parts = content.parts().orElse(Collections.emptyList());
        ContextManager contextManager = chat.getContextManager();
        UITheme theme = UITheme.get();

        Color headerBg, contentBg, headerFg, borderColor;
        
        if ("user".equalsIgnoreCase(role)) {
            headerBg = theme.getUserHeaderBg(); contentBg = theme.getUserContentBg(); headerFg = theme.getUserHeaderFg(); borderColor = theme.getUserBorder();
        } else if ("tool".equalsIgnoreCase(role)) {
            headerBg = theme.getToolHeaderBg(); contentBg = theme.getToolContentBg(); headerFg = theme.getToolHeaderFg(); borderColor = theme.getToolBorder();
        } else {
            headerBg = theme.getModelHeaderBg(); contentBg = theme.getModelContentBg(); headerFg = theme.getModelHeaderFg(); borderColor = theme.getModelBorder();
        }

        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(true);
        messagePanel.setBackground(contentBg);
        messagePanel.setBorder(BorderFactory.createLineBorder(borderColor, 2, true));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(true);
        headerPanel.setBackground(headerBg);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        String headerText = String.format("<html><b>%S</b> [#%d]", role, message.getSequentialId());
        headerText += " <font color='" + theme.getSecondaryFontColorHex() + "'>- " + TimeUtils.formatSmartTimestamp(message.getCreatedOn()) + "</font>";
        
        if (message.getElapsedTimeMillis() > 0) {
            headerText += " <font color='" + theme.getSecondaryFontColorHex() + "'><i>(Elapsed: " + TimeUtils.formatDuration(message.getElapsedTimeMillis()) + ")</i></font>";
        }
        
        Optional<GenerateContentResponseUsageMetadata> usageOpt = Optional.ofNullable(message.getUsageMetadata());
        if (usageOpt.isPresent()) {
            headerText += String.format(" <font color='" + theme.getSecondaryFontColorHex() + "'><i>(Tokens: %d)</i></font>", usageOpt.get().candidatesTokenCount().orElse(0));
        }
        headerText += "</html>";
        
        JLabel headerLabel = new JLabel(headerText);
        headerLabel.setForeground(headerFg);
        headerPanel.add(headerLabel, BorderLayout.CENTER);

        JButton pruneMessageButton = new JButton("X");
        pruneMessageButton.setMargin(new Insets(0, 4, 0, 4));
        pruneMessageButton.setToolTipText("Remove this entire message from the context");
        pruneMessageButton.addActionListener(e -> contextManager.pruneMessages(
            Collections.singletonList(message.getSequentialId()), 
            "User pruned message from UI"
        ));
        headerPanel.add(pruneMessageButton, BorderLayout.EAST);
        
        messagePanel.add(headerPanel, BorderLayout.NORTH);

        JPanel contentPanel = new ScrollablePanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                gbc.insets = new Insets(12, 0, 12, 0);
                JSeparator separator = new JSeparator();
                separator.setForeground(borderColor);
                contentPanel.add(separator, gbc);
            }

            gbc.insets = new Insets(0, 0, 0, 0);

            Part part = parts.get(i);
            PartRenderer renderer = instanceRendererMap.getOrDefault(part, typeRendererMap.get(PartType.from(part)));

            if (renderer != null) {
                String[] description = chat.getContextManager().getSessionManager().describePart(part);
                
                JPanel partHeaderPanel = new JPanel(new BorderLayout(4, 0));
                partHeaderPanel.setOpaque(false);
                
                JLabel partTitle = new JLabel(String.format("<html>%s<font color='" + theme.getSecondaryFontColorHex() + "'> - %s</font></html>", description[0], description[1]));
                partTitle.setFont(partTitle.getFont().deriveFont(Font.ITALIC, 11f));
                partTitle.setForeground(theme.getSecondaryFontColor());
                partHeaderPanel.add(partTitle, BorderLayout.CENTER);
                
                JButton prunePartButton = new JButton("x");
                prunePartButton.setMargin(new Insets(0, 2, 0, 2));
                prunePartButton.setToolTipText("Remove this part (and its dependencies) from the context");
                prunePartButton.addActionListener(e -> contextManager.prunePartsByReference(
                    Collections.singletonList(part), 
                    "User pruned part from UI"
                ));
                
                JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                buttonWrapper.setOpaque(false);
                buttonWrapper.add(prunePartButton);
                partHeaderPanel.add(buttonWrapper, BorderLayout.EAST);
                
                contentPanel.add(partHeaderPanel, gbc);

                gbc.insets = new Insets(4, 0, 0, 0);
                JComponent partComponent = renderer.render(part, editorKitProvider);
                contentPanel.add(partComponent, gbc);

                if (part.thoughtSignature().isPresent()) {
                    byte[] signatureBytes = part.thoughtSignature().get();
                    String hexSignature = DatatypeConverter.printHexBinary(signatureBytes);
                    String displaySignature = hexSignature.length() > 60 ? hexSignature.substring(0, 57) + "..." : hexSignature;
                    
                    JLabel thoughtSignatureLabel = new JLabel(String.format("<html><font color='" + theme.getSecondaryFontColorHex() + "'><i>(Thought Signature: %s)</i></font></html>", displaySignature));
                    thoughtSignatureLabel.setFont(thoughtSignatureLabel.getFont().deriveFont(Font.ITALIC, 10f));
                    thoughtSignatureLabel.setForeground(theme.getSecondaryFontColor());
                    gbc.insets = new Insets(4, 0, 0, 0);
                    contentPanel.add(thoughtSignatureLabel, gbc);
                }

            } else {
                JLabel unsupportedLabel = new JLabel("Unsupported part type " + part);
                unsupportedLabel.setForeground(theme.getFontColor());
                contentPanel.add(unsupportedLabel, gbc);
            }
        }
        
        if (message.getGroundingMetadata() != null) {
            gbc.insets = new Insets(12, 0, 0, 0);
            GroundingMetadataRenderer groundingRenderer = new GroundingMetadataRenderer(message.getGroundingMetadata());
            contentPanel.add(groundingRenderer, gbc);
        }

        gbc.weighty = 1;
        contentPanel.add(Box.createVerticalGlue(), gbc);

        messagePanel.add(contentPanel, BorderLayout.CENTER);
        return messagePanel;
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
