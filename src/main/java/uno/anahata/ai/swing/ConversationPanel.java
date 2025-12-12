package uno.anahata.ai.swing;

import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.Chat;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.context.ContextListener;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusListener;
import uno.anahata.ai.swing.render.ContentRenderer;
import uno.anahata.ai.swing.util.SwingUtils;

@Slf4j
@Getter
public class ConversationPanel extends JPanel implements ContextListener, StatusListener {

    private final ChatPanel parentPanel;
    private Chat chat;
    private EditorKitProvider editorKitProvider;
    private SwingChatConfig config;

    // UI Components
    private JPanel chatContentPanel;
    private JScrollPane chatScrollPane;

    // For scroll state restoration
    private SwingUtils.ScrollState<ChatMessage> scrollStateToRestore;
    private boolean wasAtBottom = true;

    public ConversationPanel(ChatPanel parentPanel) {
        super(new BorderLayout(5, 5));
        this.parentPanel = parentPanel;
        initComponents();
    }

    private void initComponents() {
        this.chat = parentPanel.getChat();
        this.editorKitProvider = parentPanel.getEditorKitProvider();
        this.config = parentPanel.getConfig();

        this.chat.addContextListener(this);
        this.chat.addStatusListener(this);

        chatContentPanel = new ScrollablePanel();
        chatContentPanel.setLayout(new BoxLayout(chatContentPanel, BoxLayout.Y_AXIS));
        chatScrollPane = new JScrollPane(chatContentPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());

        JButton scrollToBottomButton = new JButton("↓");
        scrollToBottomButton.setToolTipText("Scroll to Bottom");
        scrollToBottomButton.addActionListener(e -> scrollToBottom());

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        southPanel.add(scrollToBottomButton, BorderLayout.EAST);

        add(chatScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    public void redraw() {
        captureScrollState();

        chatContentPanel.removeAll();
        List<ChatMessage> currentContext = chat.getContext();

        for (ChatMessage chatMessage : currentContext) {
            if (chatMessage.getContent() != null) {
                ContentRenderer renderer = new ContentRenderer(parentPanel);
                JComponent messageComponent = renderer.render(chatMessage);

                ChatMessageJPanel messageContainer = new ChatMessageJPanel(chatMessage);
                messageContainer.setLayout(new BorderLayout());
                messageContainer.add(messageComponent, BorderLayout.CENTER);

                chatContentPanel.add(messageContainer);
            }
        }

        chatContentPanel.revalidate();
        chatContentPanel.repaint();

        restoreScrollState();
    }

    private void captureScrollState() {
        wasAtBottom = isScrolledToBottom();
        if (wasAtBottom) {
            scrollStateToRestore = null;
        } else {
            scrollStateToRestore = SwingUtils.getScrollState(chatScrollPane, c -> ((ChatMessageJPanel) c).getChatMessage());
        }
    }

    private boolean isScrolledToBottom() {
        JScrollBar verticalScrollBar = chatScrollPane.getVerticalScrollBar();
        int tolerance = 10; // A few pixels of tolerance
        return (verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount()) >= (verticalScrollBar.getMaximum() - tolerance);
    }

    private void restoreScrollState() {
        SwingUtilities.invokeLater(() -> {
            if (wasAtBottom) {
                scrollToBottom();
                return;
            }

            if (scrollStateToRestore == null || scrollStateToRestore.getAnchor() == null) {
                return;
            }

            for (Component comp : chatContentPanel.getComponents()) {
                if (comp instanceof ChatMessageJPanel) {
                    ChatMessageJPanel panel = (ChatMessageJPanel) comp;
                    // Use .equals() for robustness, though reference equality should work too.
                    if (panel.getChatMessage().equals(scrollStateToRestore.getAnchor())) {
                        int newScrollValue = panel.getY() - scrollStateToRestore.getOffset();
                        int messageIndex = chat.getContext().indexOf(panel.getChatMessage());
                        
                        log.info("Restoring scroll state to message #{} ({}), pixel value: {}",
                                 messageIndex, panel.getChatMessage().toString(), newScrollValue);
                        chatScrollPane.getVerticalScrollBar().setValue(newScrollValue);
                        return;
                    }
                }
            }
        });
    }

    private void scrollToBottom() {
        if (chatContentPanel.getComponentCount() == 0) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalScrollBar = chatScrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
    }

    @Override
    public void contextChanged(Chat source) {
        SwingUtilities.invokeLater(this::redraw);
    }

    @Override
    public void contextCleared(Chat source) {
        SwingUtilities.invokeLater(this::redraw);
    }

    @Override
    public void statusChanged(ChatStatus status, String lastExceptionToString) {
        // This listener is no longer responsible for scrolling logic.
    }
}
