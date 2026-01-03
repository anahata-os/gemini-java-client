/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
        
        // Robust scrolling: If the panel resizes (e.g. images loading) and we were at the bottom, stay at the bottom.
        chatContentPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (wasAtBottom) {
                    scrollToBottom();
                }
            }
        });

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
        if (verticalScrollBar.getModel().getExtent() == 0) {
            return true; // Assume bottom if not yet realized
        }
        int tolerance = 30; // Increased tolerance for high-DPI or complex layouts
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
                    if (panel.getChatMessage().equals(scrollStateToRestore.getAnchor())) {
                        int newScrollValue = panel.getY() - scrollStateToRestore.getOffset();
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
        
        // We use a double-invoke strategy to ensure the layout manager has 
        // finished calculating the new preferred sizes after the redraw.
        SwingUtilities.invokeLater(() -> {
            chatContentPanel.revalidate();
            chatScrollPane.revalidate();
            
            SwingUtilities.invokeLater(() -> {
                JScrollBar verticalScrollBar = chatScrollPane.getVerticalScrollBar();
                verticalScrollBar.setValue(verticalScrollBar.getMaximum());
                
                // A third safety scroll for components that load asynchronously (like images)
                Timer timer = new Timer(150, e -> {
                    verticalScrollBar.setValue(verticalScrollBar.getMaximum());
                });
                timer.setRepeats(false);
                timer.start();
            });
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
