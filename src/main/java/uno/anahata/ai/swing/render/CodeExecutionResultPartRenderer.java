/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

/**
 * Renders the content of a CodeExecutionResult part as plain text.
 *
 * @author pablo-ai
 */
public class CodeExecutionResultPartRenderer implements PartRenderer {

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        if (!part.codeExecutionResult().isPresent()) {
            panel.add(new JLabel("[CodeExecutionResultPartRenderer: No result found in part]"));
            return panel;
        }

        CodeExecutionResult result = part.codeExecutionResult().get();
        String outcome = result.outcome().map(Object::toString).orElse("UNKNOWN");
        boolean isError = "ERROR".equalsIgnoreCase(outcome);

        JLabel outcomeLabel = new JLabel("Outcome: " + outcome);
        outcomeLabel.setFont(outcomeLabel.getFont().deriveFont(Font.BOLD));
        outcomeLabel.setForeground(isError ? new Color(192, 0, 0) : new Color(0, 128, 0));
        outcomeLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        panel.add(outcomeLabel, BorderLayout.NORTH);

        String outputText = result.output().orElse("");

        JTextArea textArea = new JTextArea(outputText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setOpaque(true);
        textArea.setBackground(new Color(245, 245, 245));
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(null); 

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
}
