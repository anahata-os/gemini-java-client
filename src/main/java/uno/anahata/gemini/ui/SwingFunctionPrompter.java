package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
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
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.Chat;
import uno.anahata.gemini.config.ChatConfig;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.ui.SwingChatConfig.UITheme;

/**
 * A combined JDialog and FunctionPrompter implementation for Swing.
 *
 * @author pablo-ai
 */
@Slf4j
public class SwingFunctionPrompter extends JDialog implements FunctionPrompter {

    private final EditorKitProvider editorKitProvider;
    private final SwingChatConfig config;
    private final List<InteractiveFunctionCallRenderer> interactiveRenderers = new ArrayList<>();
    
    private List<FunctionCall> approvedFunctions = new ArrayList<>();
    private List<FunctionCall> deniedFunctions = new ArrayList<>();
    private String userComment = "";

    public SwingFunctionPrompter(JFrame owner, EditorKitProvider editorKitProvider, SwingChatConfig config) {
        super(owner, "Confirm Proposed Actions", true);
        this.editorKitProvider = editorKitProvider;
        this.config = config;
    }

    @Override
    public PromptResult prompt(ChatMessage modelMessage, Chat chat) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                interactiveRenderers.clear();
                approvedFunctions.clear();
                deniedFunctions.clear();
                userComment = "";
                
                initComponents(modelMessage, chat);
                pack();
                setSize(1024, 768);
                setLocationRelativeTo(getOwner());
                setVisible(true);
            });
        } catch (InterruptedException | InvocationTargetException e) {
            Throwable cause = (e instanceof InvocationTargetException) ? e.getCause() : e;
            log.error("Exception while showing function confirmation dialog", cause);
            Thread.currentThread().interrupt();
            return new PromptResult(
                Collections.emptyList(), 
                collectAllProposedFunctions(modelMessage.getContent()), 
                "Error showing confirmation dialog. Check IDE log for details."
            );
        }
        return new PromptResult(approvedFunctions, deniedFunctions, userComment);
    }

    private void initComponents(ChatMessage modelMessage, Chat chat) {
        setContentPane(new JPanel(new BorderLayout(10, 10)));
        
        ContentRenderer contentRenderer = new ContentRenderer(editorKitProvider, config);

        final List<? extends Part> parts = modelMessage.getContent().parts().get();
        
        UITheme theme = config.getTheme();
        
        for (Part part : parts) {
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                FunctionConfirmation preference = config.getFunctionConfirmation(fc);
                InteractiveFunctionCallRenderer interactiveRenderer = new InteractiveFunctionCallRenderer(fc, preference, theme);
                interactiveRenderers.add(interactiveRenderer);
                contentRenderer.registerRenderer(part, interactiveRenderer);
            }
        }

        int contentIdx = chat.getContextManager().getContext().indexOf(modelMessage);
        JComponent renderedContent = contentRenderer.render(modelMessage, contentIdx, chat.getContextManager());
        
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
            collectResultsFromInteractiveRenderers(config);
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

    private void collectResultsFromInteractiveRenderers(ChatConfig config) {
        for (InteractiveFunctionCallRenderer renderer : interactiveRenderers) {
            FunctionCall functionCall = renderer.getFunctionCall();
            FunctionConfirmation state = renderer.getSelectedState();
            
            config.setFunctionConfirmation(functionCall, state);

            switch (state) {
                case YES:
                case ALWAYS:
                    approvedFunctions.add(functionCall);
                    break;
                case NO:
                case NEVER:
                    deniedFunctions.add(functionCall);
                    break;
            }
        }
    }
    
    private List<FunctionCall> collectAllProposedFunctions(Content modelResponse) {
        List<FunctionCall> all = new ArrayList<>();
        modelResponse.parts().ifPresent(parts -> parts.forEach(part -> part.functionCall().ifPresent(all::add)));
        return all;
    }
    
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
            return true;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
