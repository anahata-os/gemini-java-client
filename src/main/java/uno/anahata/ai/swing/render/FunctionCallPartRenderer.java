/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

public class FunctionCallPartRenderer implements PartRenderer {

    private static final int MAX_LINES_EXPANDED = 5;

    public FunctionCallPartRenderer() {
    }

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        if (!part.functionCall().isPresent()) {
            throw new IllegalArgumentException("Part must be a FunctionCall Part.");
        }

        FunctionCall fc = part.functionCall().get();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        if (fc.args().get() == null || fc.args().get().isEmpty()) {
            JLabel noArgsLabel = new JLabel("No arguments provided.");
            noArgsLabel.setFont(noArgsLabel.getFont().deriveFont(java.awt.Font.ITALIC));
            panel.add(noArgsLabel);
            return panel;
        }

        for (java.util.Map.Entry<String, Object> entry : fc.args().get().entrySet()) {
            panel.add(createCollapsibleArgumentPanel(entry.getKey(), entry.getValue()));
            panel.add(Box.createVerticalStrut(4));
        }

        return panel;
    }

    private JComponent createCollapsibleArgumentPanel(String key, Object value) {
        UITheme theme = UITheme.get();
        JComponent valueComponent;
        String valueAsString;

        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            JLabel naLabel = new JLabel("n/a");
            naLabel.setFont(naLabel.getFont().deriveFont(java.awt.Font.ITALIC));
            naLabel.setForeground(theme.getSecondaryFontColor());
            valueComponent = naLabel;
            valueAsString = "";
        } else {
            valueAsString = GsonUtils.prettyPrint(value);
            JTextArea textArea = new JTextArea(valueAsString);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(theme.getMonoFont());
            
            if (theme.isMinimalist()) {
                textArea.setBackground(theme.getPanelBg());
                textArea.setForeground(theme.getFontColor());
                textArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.getBorder()),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));
            } else {
                textArea.setBackground(theme.getFunctionCallBg());
                textArea.setForeground(theme.getFunctionCallFg());
                textArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.getDefaultBorder()),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));
            }
            valueComponent = textArea;
        }

        JPanel valueWrapper = new JPanel(new BorderLayout());
        valueWrapper.setOpaque(false);
        valueWrapper.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 0));
        valueWrapper.add(valueComponent, BorderLayout.CENTER);

        boolean isExpandedByDefault = valueAsString.lines().count() <= MAX_LINES_EXPANDED;
        JToggleButton toggleButton = new JToggleButton(key, isExpandedByDefault);
        toggleButton.setFont(toggleButton.getFont().deriveFont(java.awt.Font.BOLD));
        toggleButton.setHorizontalAlignment(JToggleButton.LEFT);

        valueWrapper.setVisible(toggleButton.isSelected());

        toggleButton.addActionListener(e -> valueWrapper.setVisible(toggleButton.isSelected()));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(toggleButton, BorderLayout.NORTH);
        mainPanel.add(valueWrapper, BorderLayout.CENTER);
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        return mainPanel;
    }
}
