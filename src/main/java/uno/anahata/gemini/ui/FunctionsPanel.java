package uno.anahata.gemini.ui;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.functions.FunctionConfirmation;

public class FunctionsPanel extends JScrollPane {
    private final GeminiChat chat;
    private final SwingGeminiConfig config;

    public FunctionsPanel(GeminiChat chat, SwingGeminiConfig config) {
        this.chat = chat;
        this.config = config;
        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        for (FunctionDeclaration fd : chat.getFunctionManager().getFunctionTool().functionDeclarations().get()) {
            mainPanel.add(createFunctionControlPanel(fd));
            mainPanel.add(Box.createVerticalStrut(8));
        }
        
        setViewportView(mainPanel);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    private JPanel createFunctionControlPanel(FunctionDeclaration fd) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(fd.name().get()));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);

        JLabel description = new JLabel("<html>" + fd.description().get().replace("\n", "<br>") + "</html>");
        panel.add(description, gbc);

        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        
        panel.add(createButtonGroup(fd), gbc);
        
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel createButtonGroup(FunctionDeclaration fd) {
        JPanel buttonPanel = new JPanel();
        ButtonGroup group = new ButtonGroup();

        JToggleButton promptButton = new JToggleButton("Prompt");
        JToggleButton alwaysButton = new JToggleButton("Always");
        JToggleButton neverButton = new JToggleButton("Never");

        group.add(promptButton);
        group.add(alwaysButton);
        group.add(neverButton);

        buttonPanel.add(promptButton);
        buttonPanel.add(alwaysButton);
        buttonPanel.add(neverButton);
        
        FunctionCall fc = FunctionCall.builder().name(fd.name().get()).build();

        // Set initial state from config
        FunctionConfirmation currentPref = config.getFunctionConfirmation(fc);
        if (currentPref == null) {
            promptButton.setSelected(true);
        } else {
            switch (currentPref) {
                case ALWAYS:
                    alwaysButton.setSelected(true);
                    break;
                case NEVER:
                    neverButton.setSelected(true);
                    break;
                default:
                    promptButton.setSelected(true);
            }
        }

        // Add listeners to update config
        promptButton.addActionListener(e -> config.clearFunctionConfirmation(fc));
        alwaysButton.addActionListener(e -> config.setFunctionConfirmation(fc, FunctionConfirmation.ALWAYS));
        neverButton.addActionListener(e -> config.setFunctionConfirmation(fc, FunctionConfirmation.NEVER));

        return buttonPanel;
    }
}