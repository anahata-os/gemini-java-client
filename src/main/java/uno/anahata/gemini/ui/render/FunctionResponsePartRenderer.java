package uno.anahata.gemini.ui.render;

import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.ui.StandaloneSwingGeminiConfig.UITheme;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

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
        Object contentToRender = isError ? responseMap.get("error") : (responseMap.containsKey("output") ? responseMap.get("output") : responseMap);
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
