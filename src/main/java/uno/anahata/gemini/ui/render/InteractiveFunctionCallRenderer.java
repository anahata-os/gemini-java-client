package uno.anahata.gemini.ui.render;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.FlowLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.ui.SwingChatConfig;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

public class InteractiveFunctionCallRenderer implements PartRenderer {

    private final FunctionCall functionCall;
    private final SwingChatConfig.UITheme theme;
    private FunctionConfirmation selectedState;
    private final JToggleButton yesButton, noButton, alwaysButton, neverButton;

    public InteractiveFunctionCallRenderer(FunctionCall functionCall, FunctionConfirmation preference, SwingChatConfig.UITheme theme) {
        this.functionCall = functionCall;
        this.theme = theme;
        this.selectedState = (preference != null) ? preference : FunctionConfirmation.YES;

        this.yesButton = new JToggleButton("Yes");
        this.noButton = new JToggleButton("No");
        this.alwaysButton = new JToggleButton("Always");
        this.neverButton = new JToggleButton("Never");

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(yesButton);
        buttonGroup.add(noButton);
        buttonGroup.add(alwaysButton);
        buttonGroup.add(neverButton);

        switch (selectedState) {
            case YES:
                yesButton.setSelected(true);
                break;
            case NO:
                noButton.setSelected(true);
                break;
            case ALWAYS:
                alwaysButton.setSelected(true);
                break;
            case NEVER:
                neverButton.setSelected(true);
                break;
        }

        yesButton.addActionListener(e -> selectedState = FunctionConfirmation.YES);
        noButton.addActionListener(e -> selectedState = FunctionConfirmation.NO);
        alwaysButton.addActionListener(e -> selectedState = FunctionConfirmation.ALWAYS);
        neverButton.addActionListener(e -> selectedState = FunctionConfirmation.NEVER);
    }

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        JPanel mainPanel = new JPanel(new java.awt.BorderLayout());
        mainPanel.setOpaque(false);

        JComponent functionCallDetails = new FunctionCallPartRenderer(theme).render(part, editorKitProvider);
        mainPanel.add(functionCallDetails, java.awt.BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setOpaque(false);
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        buttonPanel.add(alwaysButton);
        buttonPanel.add(neverButton);
        mainPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        return mainPanel;
    }

    public FunctionCall getFunctionCall() {
        return functionCall;
    }

    public FunctionConfirmation getSelectedState() {
        return selectedState;
    }
}
