package uno.anahata.gemini.ui.render;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.FlowLayout;
import java.util.Set;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import uno.anahata.gemini.ui.SwingGeminiConfig;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

public class InteractiveFunctionCallRenderer implements PartRenderer {

    public enum ConfirmationState { YES, NO, ALWAYS, NEVER }

    private final FunctionCall functionCall;
    private final SwingGeminiConfig.UITheme theme;
    private ConfirmationState selectedState;
    private final JToggleButton yesButton, noButton, alwaysButton, neverButton;

    public InteractiveFunctionCallRenderer(FunctionCall functionCall, PartRenderer defaultRenderer, Set<String> alwaysApprove, Set<String> neverApprove, SwingGeminiConfig.UITheme theme) {
        this.functionCall = functionCall;
        this.theme = theme;
        this.selectedState = ConfirmationState.YES;
        String functionName = functionCall.name().get();

        if (neverApprove.contains(functionName)) selectedState = ConfirmationState.NEVER;
        else if (alwaysApprove.contains(functionName)) selectedState = ConfirmationState.ALWAYS;

        this.yesButton = new JToggleButton("Yes");
        this.noButton = new JToggleButton("No");
        this.alwaysButton = new JToggleButton("Always");
        this.neverButton = new JToggleButton("Never");

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(yesButton);
        buttonGroup.add(noButton);
        buttonGroup.add(alwaysButton);
        buttonGroup.add(neverButton);

        // Reverted to Java 8 compatible if-else block
        if (selectedState == ConfirmationState.YES) {
            yesButton.setSelected(true);
        } else if (selectedState == ConfirmationState.NO) {
            noButton.setSelected(true);
        } else if (selectedState == ConfirmationState.ALWAYS) {
            alwaysButton.setSelected(true);
        } else {
            neverButton.setSelected(true);
        }

        yesButton.addActionListener(e -> selectedState = ConfirmationState.YES);
        noButton.addActionListener(e -> selectedState = ConfirmationState.NO);
        alwaysButton.addActionListener(e -> selectedState = ConfirmationState.ALWAYS);
        neverButton.addActionListener(e -> selectedState = ConfirmationState.NEVER);
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

    public ConfirmationState getSelectedState() {
        return selectedState;
    }
}
