/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.ExecutableCode;
import com.google.genai.types.Language;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

/**
 * Renders the content of an ExecutableCode part, which is a request from the
 * model to execute a block of code.
 *
 * @author anahata
 */
public class ExecutableCodePartRenderer implements PartRenderer {

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        if (!part.executableCode().isPresent()) {
            panel.add(new JLabel("[ExecutableCodePartRenderer: No executable code found in part]"));
            return panel;
        }

        ExecutableCode execution = part.executableCode().get();
        Language language = execution.language().get();
        String code = execution.code().get();

        JLabel titleLabel = new JLabel("Executable Code (" + language + ")");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 102, 153));
        titleLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        panel.add(titleLabel, BorderLayout.NORTH);

        JComponent codeBlock = CodeBlockRenderer.render(language.toString(), code, editorKitProvider);
        panel.add(codeBlock, BorderLayout.CENTER);

        return panel;
    }
}