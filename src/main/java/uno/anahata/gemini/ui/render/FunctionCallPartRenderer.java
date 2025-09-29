package uno.anahata.gemini.ui.render;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * Renders a FunctionCall Part into a Swing JPanel, displaying each argument in
 * a collapsible, structured format.
 *
 * @author pablo-ai
 */
public class FunctionCallPartRenderer implements PartRenderer {

    private static final Pattern METHOD_SIGNATURE_PATTERN = Pattern.compile("\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{?");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+[\\w.]+;");

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        if (!part.functionCall().isPresent()) {
            throw new IllegalArgumentException("Part must be a FunctionCall Part.");
        }

        FunctionCall fc = part.functionCall().get();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        Map<String, Object> args = fc.args().get();
        if (args == null || args.isEmpty()) {
            JLabel noArgsLabel = new JLabel("No arguments provided.");
            noArgsLabel.setFont(noArgsLabel.getFont().deriveFont(Font.ITALIC));
            panel.add(noArgsLabel);
            return panel;
        }

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // FIX: Create a collapsible panel for each argument.
            panel.add(createCollapsibleArgumentPanel(key, value, editorKitProvider));
            panel.add(Box.createVerticalStrut(4)); // Space between arguments
        }

        return panel;
    }

    private JComponent createCollapsibleArgumentPanel(String key, Object value, EditorKitProvider editorKitProvider) {
        // Value Component (created first)
        JComponent valueComponent;
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            JLabel naLabel = new JLabel("n/a");
            naLabel.setFont(naLabel.getFont().deriveFont(Font.ITALIC));
            naLabel.setForeground(Color.GRAY);
            valueComponent = naLabel;
        } /*else if (value instanceof String && isLikelyJavaCode((String) value)) {
            valueComponent = CodeBlockRenderer.render("java", (String) value, editorKitProvider);
        } */else {
            String valueAsString = GsonUtils.prettyPrint(value);
            JTextArea textArea = new JTextArea(valueAsString);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font("SF Mono", Font.PLAIN, 14));
            textArea.setBackground(new Color(28, 37, 51));
            textArea.setForeground(new Color(0, 229, 255));
            textArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            valueComponent = textArea;
        }

        // Wrapper panel for the value component to control visibility
        JPanel valueWrapper = new JPanel(new BorderLayout());
        valueWrapper.setOpaque(false);
        valueWrapper.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 0)); // Indent
        valueWrapper.add(valueComponent, BorderLayout.CENTER);

        // Toggle Button (the header)
        JToggleButton toggleButton = new JToggleButton(key, true); // Expanded by default
        toggleButton.setFont(toggleButton.getFont().deriveFont(Font.BOLD));
        toggleButton.setHorizontalAlignment(JToggleButton.LEFT);
        
        valueWrapper.setVisible(toggleButton.isSelected());

        toggleButton.addActionListener(e -> {
            valueWrapper.setVisible(toggleButton.isSelected());
        });

        // Main container for this argument
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(toggleButton, BorderLayout.NORTH);
        mainPanel.add(valueWrapper, BorderLayout.CENTER);
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        return mainPanel;
    }

    private boolean isLikelyJavaCode(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        int score = 0;
        if (value.contains("public") || value.contains("private") || value.contains("class") || value.contains("void")) score++;
        if (value.contains("{") || value.contains("}") || value.contains(";")) score++;
        if (value.contains("\n")) score++;
        if (IMPORT_PATTERN.matcher(value).find()) score += 2;
        if (METHOD_SIGNATURE_PATTERN.matcher(value).find()) score += 2;
        return score >= 3;
    }
}
