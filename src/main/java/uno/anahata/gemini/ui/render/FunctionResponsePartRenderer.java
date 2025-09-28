package uno.anahata.gemini.ui.render;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

/**
 * Renders a FunctionResponse Part into a collapsible Swing JPanel.
 *
 * @author pablo-ai
 */
public class FunctionResponsePartRenderer implements PartRenderer {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        if (!part.functionResponse().isPresent()) {
            throw new IllegalArgumentException("Part must be a FunctionResponse Part.");
        }

        FunctionResponse fr = part.functionResponse().get();
        Map<String, Object> responseMap = fr.response().get();

        boolean isError = responseMap.containsKey("error");
        Object contentToRender = isError ? responseMap.get("error") : (responseMap.containsKey("output") ? responseMap.get("output") : responseMap);
        String finalContentString = (contentToRender instanceof String) ? (String) contentToRender : gson.toJson(contentToRender);

        // Create the content panel (the part that gets shown/hidden)
        JTextArea contentArea = new JTextArea(finalContentString);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        // FIX: Increase font size for better readability
        contentArea.setFont(new Font("SF Mono", Font.PLAIN, 14));
        contentArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (isError) {
            contentArea.setBackground(new Color(51, 28, 28)); // #331c1c
            contentArea.setForeground(Color.RED);
        } else {
            contentArea.setBackground(Color.BLACK);
            contentArea.setForeground(new Color(0, 255, 0)); // Green
        }
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(contentArea, BorderLayout.CENTER);
        contentPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Create the toggle button
        JToggleButton toggleButton = new JToggleButton("Tool Output");
        toggleButton.setSelected(isError); // Show errors by default
        contentPanel.setVisible(isError);

        toggleButton.addActionListener(e -> {
            contentPanel.setVisible(toggleButton.isSelected());
        });

        // Create the main container panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(toggleButton, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        return mainPanel;
    }
}
