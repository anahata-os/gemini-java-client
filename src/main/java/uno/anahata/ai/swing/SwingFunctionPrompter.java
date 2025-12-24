/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.tools.FunctionConfirmation;
import uno.anahata.ai.tools.FunctionPrompter;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;
import uno.anahata.ai.swing.render.ContentRenderer;
import uno.anahata.ai.swing.render.InteractiveFunctionCallRenderer;

/**
 * A combined JDialog and FunctionPrompter implementation for Swing.
 *
 * @author pablo-ai
 */
@Slf4j
public class SwingFunctionPrompter extends JDialog implements FunctionPrompter {

    private final ChatPanel chatPanel;
    private final List<InteractiveFunctionCallRenderer> interactiveRenderers = new ArrayList<>();
    
    private Map<FunctionCall, FunctionConfirmation> functionConfirmations = new LinkedHashMap<>();
    private String userComment = "";
    private boolean cancelled = false;

    public SwingFunctionPrompter(ChatPanel chatPanel) {
        super((JFrame) SwingUtilities.getWindowAncestor(chatPanel), "Confirm Proposed Actions", true);
        this.chatPanel = chatPanel;
    }

    @Override
    public PromptResult prompt(ChatMessage modelMessage, Chat chat) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                interactiveRenderers.clear();
                functionConfirmations.clear();
                userComment = "";
                cancelled = false; // Reset state for the new prompt
                
                initComponents(modelMessage);
                pack();
                setSize(1024, 768);
                setLocationRelativeTo(getOwner());
                setVisible(true); // This blocks until the dialog is closed
            });
        } catch (InterruptedException | InvocationTargetException e) {
            Throwable cause = (e instanceof InvocationTargetException) ? e.getCause() : e;
            log.error("Exception while showing function confirmation dialog", cause);
            Thread.currentThread().interrupt();
            // Return a result indicating cancellation due to error
            return new PromptResult(
                Collections.emptyMap(), 
                "Error showing confirmation dialog. Check IDE log for details.",
                true 
            );
        }
        return new PromptResult(functionConfirmations, userComment, cancelled);
    }

    private void initComponents(ChatMessage modelMessage) {
        setContentPane(new JPanel(new BorderLayout(10, 10)));
        
        ContentRenderer contentRenderer = new ContentRenderer(chatPanel);

        final List<? extends Part> parts = modelMessage.getContent().parts().get();
        
        UITheme theme = chatPanel.getConfig().getTheme();
        
        for (Part part : parts) {
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                FunctionConfirmation preference = chatPanel.getConfig().getFunctionConfirmation(fc);
                InteractiveFunctionCallRenderer interactiveRenderer = new InteractiveFunctionCallRenderer(fc, preference, theme);
                interactiveRenderers.add(interactiveRenderer);
                contentRenderer.registerRenderer(part, interactiveRenderer);
            }
        }

        JComponent renderedContent = contentRenderer.render(modelMessage);
        
        JPanel contentWrapper = new ScrollablePanel();
        contentWrapper.setLayout(new BorderLayout());
        contentWrapper.add(renderedContent, BorderLayout.CENTER);
        
        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        JTextArea commentTextArea = new JTextArea(3, 60);
        JPanel commentPanel = new JPanel(new BorderLayout());
        commentPanel.setBorder(new TitledBorder("Add Comment (Optional)"));
        commentPanel.add(new JScrollPane(commentTextArea), BorderLayout.CENTER);
        bottomPanel.add(commentPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton approveButton = new JButton("Confirm");
        JButton cancelButton = new JButton("Cancel");

        approveButton.addActionListener(e -> {
            this.functionConfirmations = collectResultsFromInteractiveRenderers(chatPanel.getConfig());
            this.userComment = commentTextArea.getText();
            this.cancelled = false;
            setVisible(false);
            dispose();
        });

        cancelButton.addActionListener(e -> {
            this.userComment = commentTextArea.getText();
            this.cancelled = true;
            // Do NOT collect results, the cancellation overrides individual choices.
            setVisible(false);
            dispose();
        });
        
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Treat closing the dialog as a cancellation
                userComment = commentTextArea.getText();
                cancelled = true;
                setVisible(false);
                dispose();
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(approveButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private Map<FunctionCall, FunctionConfirmation> collectResultsFromInteractiveRenderers(ChatConfig config) {
        Map<FunctionCall, FunctionConfirmation> results = new LinkedHashMap<>();
        for (InteractiveFunctionCallRenderer renderer : interactiveRenderers) {
            FunctionCall functionCall = renderer.getFunctionCall();
            FunctionConfirmation state = renderer.getSelectedState();
            
            // Persist the user's choice for the next time this function is called.
            config.setFunctionConfirmation(functionCall, state);
            results.put(functionCall, state);
        }
        return results;
    }
}