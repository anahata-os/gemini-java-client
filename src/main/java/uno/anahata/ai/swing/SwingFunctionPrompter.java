/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.undo.UndoManager;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.tools.FunctionConfirmation;
import uno.anahata.ai.tools.FunctionPrompter;
import uno.anahata.ai.swing.render.ContentRenderer;
import uno.anahata.ai.swing.render.InteractiveFunctionCallRenderer;

/**
 * A combined JDialog and FunctionPrompter implementation for Swing.
 *
 * @author anahata
 */
@Slf4j
public class SwingFunctionPrompter extends JDialog implements FunctionPrompter {

    private final ChatPanel chatPanel;
    private final List<InteractiveFunctionCallRenderer> interactiveRenderers = new ArrayList<>();
    
    private Map<FunctionCall, FunctionConfirmation> functionConfirmations = new LinkedHashMap<>();
    private String userComment = "";
    private boolean cancelled = false;

    /**
     * Constructs a new SwingFunctionPrompter.
     * @param chatPanel The ChatPanel that initiated the prompt.
     */
    public SwingFunctionPrompter(ChatPanel chatPanel) {
        // We use the window ancestor of the chatPanel as the owner to prevent the "modal hang"
        super(SwingUtilities.getWindowAncestor(chatPanel), "Confirm Proposed Actions", ModalityType.APPLICATION_MODAL);
        this.chatPanel = chatPanel;
    }

    @Override
    public PromptResult prompt(ChatMessage modelMessage, Chat chat) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                Window owner = SwingUtilities.getWindowAncestor(chatPanel);
                
                setTitle("Confirm Proposed Actions - " + chat.getDisplayName());
                interactiveRenderers.clear();
                functionConfirmations.clear();
                userComment = "";
                cancelled = false; // Reset state for the new prompt
                
                initComponents(modelMessage);
                pack();
                setSize(1024, 768);
                setLocationRelativeTo(owner);
                
                // Ensure it's visible and focused
                toFront();
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
        
        for (Part part : parts) {
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                FunctionConfirmation preference = chatPanel.getConfig().getFunctionConfirmation(fc);
                InteractiveFunctionCallRenderer interactiveRenderer = new InteractiveFunctionCallRenderer(fc, preference);
                interactiveRenderers.add(interactiveRenderer);
                contentRenderer.registerRenderer(part, interactiveRenderer);
            }
        }

        JComponent renderedContent = contentRenderer.render(modelMessage);
        
        JPanel contentWrapper = new ScrollablePanel();
        contentWrapper.setLayout(new BorderLayout());
        contentWrapper.add(renderedContent, BorderLayout.CENTER);
        
        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        // Add a bottom border to create a visible seam with the divider
        scrollPane.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        
        // Targeted fix for the scroll position:
        // We do it in invokeLater to ensure it happens AFTER the focus manager 
        // has done its initial pass when the dialog becomes visible.
        SwingUtilities.invokeLater(() -> {
            scrollPane.getVerticalScrollBar().setValue(0);
        });

        JTextArea commentTextArea = new JTextArea(5, 60);
        
        // Undo/Redo Support for the comment area
        UndoManager undoManager = new UndoManager();
        commentTextArea.getDocument().addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));
        commentTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "Undo");
        commentTextArea.getActionMap().put("Undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) undoManager.undo();
            }
        });
        commentTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "Redo");
        commentTextArea.getActionMap().put("Redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) undoManager.redo();
            }
        });

        JPanel commentPanel = new JPanel(new BorderLayout());
        commentPanel.setBorder(new TitledBorder("Add Comment (Optional)"));
        commentPanel.add(new JScrollPane(commentTextArea), BorderLayout.CENTER);
        // Add a top border to create a visible seam with the divider
        commentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            commentPanel.getBorder()
        ));
        
        // Use a JSplitPane to make the comment area resizable relative to the tool output
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, commentPanel);
        splitPane.setResizeWeight(0.8);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(10);
        splitPane.putClientProperty("FlatLaf.style", "dividerStyle: grip");
        
        add(splitPane, BorderLayout.CENTER);

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
            setVisible(false);
            dispose();
        });
        
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                userComment = commentTextArea.getText();
                cancelled = true;
                setVisible(false);
                dispose();
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(approveButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private Map<FunctionCall, FunctionConfirmation> collectResultsFromInteractiveRenderers(ChatConfig config) {
        Map<FunctionCall, FunctionConfirmation> results = new LinkedHashMap<>();
        for (InteractiveFunctionCallRenderer renderer : interactiveRenderers) {
            FunctionCall functionCall = renderer.getFunctionCall();
            FunctionConfirmation state = renderer.getSelectedState();
            
            config.setFunctionConfirmation(functionCall, state);
            results.put(functionCall, state);
        }
        return results;
    }
}
