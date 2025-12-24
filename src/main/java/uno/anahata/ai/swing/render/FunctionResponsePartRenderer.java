/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

public class FunctionResponsePartRenderer implements PartRenderer {

    private final UITheme theme;

    public FunctionResponsePartRenderer(UITheme theme) {
        this.theme = theme;
    }

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        if (!part.functionResponse().isPresent()) {
            throw new IllegalArgumentException("Part must be a FunctionResponse Part.");
        }

        FunctionResponse fr = part.functionResponse().get();
        java.util.Map<String, Object> responseMap = fr.response().get();

        boolean isError = responseMap.containsKey("error");
        Object contentToRender;

        if (isError) {
            contentToRender = responseMap.get("error");
        } else if (responseMap.size() == 1 && responseMap.containsKey("output")) {
            // Only one key, and it's "output", so just show the value for simplicity.
            contentToRender = responseMap.get("output");
        } else {
            // Multiple keys, or a single key that isn't "output", so show the whole object.
            contentToRender = responseMap;
        }
        
        String finalContentString = GsonUtils.prettyPrint(contentToRender);
        
        JTextArea contentArea = new JTextArea(finalContentString);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(theme.getMonoFont());
        contentArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (isError) {
            contentArea.setBackground(theme.getFunctionErrorBg());
            contentArea.setForeground(theme.getFunctionErrorFg());
        } else {
            contentArea.setBackground(theme.getFunctionResponseBg());
            contentArea.setForeground(theme.getFunctionResponseFg());
        }
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(contentArea, BorderLayout.CENTER);
        contentPanel.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));

        JToggleButton toggleButton = new JToggleButton("Tool Output");
        toggleButton.setSelected(isError);
        contentPanel.setVisible(isError);

        toggleButton.addActionListener(e -> contentPanel.setVisible(toggleButton.isSelected()));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(toggleButton, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        return mainPanel;
    }
}