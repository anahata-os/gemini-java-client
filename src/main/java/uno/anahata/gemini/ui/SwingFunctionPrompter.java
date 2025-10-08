package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
import uno.anahata.gemini.ui.render.PartRenderer;
import uno.anahata.gemini.ui.render.ContentRenderer;
import uno.anahata.gemini.ui.render.InteractiveFunctionCallRenderer;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.ContextManager;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.ui.render.InteractiveFunctionCallRenderer.ConfirmationState;
import uno.anahata.gemini.ui.render.PartType;

/**
 * A combined JDialog and FunctionPrompter implementation for Swing.
 *
 * @author pablo-ai
 */
public class SwingFunctionPrompter extends JDialog implements FunctionPrompter {
    private static final Logger logger = Logger.getLogger(SwingFunctionPrompter.class.getName());

    private final EditorKitProvider editorKitProvider;
    private final List<InteractiveFunctionCallRenderer> interactiveRenderers = new ArrayList<>();
    
    private List<FunctionCall> approvedFunctions = new ArrayList<>();
    private List<FunctionCall> deniedFunctions = new ArrayList<>();
    private String userComment = "";

    public SwingFunctionPrompter(JFrame owner, EditorKitProvider editorKitProvider) {
        super(owner, "Confirm Proposed Actions", true);
        this.editorKitProvider = editorKitProvider;
    }

    @Override
    public PromptResult prompt(ChatMessage modelMessage, int contentIdx, Set<String> alwaysApprove, Set<String> alwaysDeny) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                interactiveRenderers.clear();
                approvedFunctions.clear();
                deniedFunctions.clear();
                userComment = "";
                
                initComponents(modelMessage, contentIdx, alwaysApprove, alwaysDeny);
                pack();
                setSize(1024, 768);
                setLocationRelativeTo(getOwner());
                setVisible(true);
            });
        } catch (InterruptedException | InvocationTargetException e) {
            Throwable cause = (e instanceof InvocationTargetException) ? e.getCause() : e;
            logger.log(Level.SEVERE, "Exception while showing function confirmation dialog", cause);
            Thread.currentThread().interrupt();
            return new PromptResult(
                Collections.emptyList(), 
                collectAllProposedFunctions(modelMessage.getContent()), 
                "Error showing confirmation dialog. Check IDE log for details."
            );
        }
        return new PromptResult(approvedFunctions, deniedFunctions, userComment);
    }

    private void initComponents(ChatMessage modelMessage, int contentIdx, Set<String> alwaysApprove, Set<String> neverApprove) {
        setContentPane(new JPanel(new BorderLayout(10, 10)));

        ContentRenderer renderer = new ContentRenderer(editorKitProvider);
        PartRenderer defaultFcRenderer = renderer.getDefaultRendererForType(PartType.FUNCTION_CALL);

        final List<? extends Part> parts = modelMessage.getContent().parts().get();
        
        for (Part part : parts) {
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                InteractiveFunctionCallRenderer interactiveRenderer = new InteractiveFunctionCallRenderer(fc, defaultFcRenderer, alwaysApprove, neverApprove);
                interactiveRenderers.add(interactiveRenderer);
                renderer.registerRenderer(part, interactiveRenderer);
            }
        }

        JComponent renderedContent = renderer.render(modelMessage, contentIdx, ContextManager.get());
        
        // FIX: Wrap the rendered content in a ScrollablePanel to enforce width constraints.
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
            collectResultsFromInteractiveRenderers(alwaysApprove, neverApprove);
            this.userComment = commentTextArea.getText();
            setVisible(false);
            dispose();
        });

        cancelButton.addActionListener(e -> {
            this.userComment = commentTextArea.getText();
            setVisible(false);
            dispose();
        });
        
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
                dispose();
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(approveButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void collectResultsFromInteractiveRenderers(Set<String> always, Set<String> never) {
        for (InteractiveFunctionCallRenderer renderer : interactiveRenderers) {
            FunctionCall functionCall = renderer.getFunctionCall();
            String functionName = functionCall.name().get();
            ConfirmationState state = renderer.getSelectedState();
            if (state == ConfirmationState.YES) {
                approvedFunctions.add(functionCall);
                never.remove(functionName);
                always.remove(functionName);
            } else if (state == ConfirmationState.NO) {
                deniedFunctions.add(functionCall);
                never.remove(functionName);
                always.remove(functionName);
            } else if (state == ConfirmationState.ALWAYS) {
                approvedFunctions.add(functionCall);
                never.remove(functionName);
                always.add(functionName);
            } else if (state == ConfirmationState.NEVER) {
                deniedFunctions.add(functionCall);
                never.add(functionName);
                always.remove(functionName);
            }
        }
    }
    
    private List<FunctionCall> collectAllProposedFunctions(Content modelResponse) {
        List<FunctionCall> all = new ArrayList<>();
        modelResponse.parts().ifPresent(parts -> parts.forEach(part -> part.functionCall().ifPresent(all::add)));
        return all;
    }
    
    /**
     * A panel that implements Scrollable to enforce that its width matches the
     * viewport width. This is the definitive fix for the line-wrapping issue.
     */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // This is the magic!
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
