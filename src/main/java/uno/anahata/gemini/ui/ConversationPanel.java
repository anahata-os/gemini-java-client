package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
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
import uno.anahata.gemini.Chat;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.context.ContextListener;
import uno.anahata.gemini.ui.render.ContentRenderer;
import uno.anahata.gemini.ui.util.SwingUtils;

@Slf4j
@Getter
public class ConversationPanel extends JPanel implements ContextListener {

    private final AnahataPanel parentPanel;
    private Chat chat;
    private EditorKitProvider editorKitProvider;
    private SwingChatConfig config;

    // UI Components
    private JPanel chatContentPanel;
    private JScrollPane chatScrollPane;

    public ConversationPanel(AnahataPanel parentPanel) {
        super(new BorderLayout(5, 5));
        this.parentPanel = parentPanel;
        initComponents();
    }

    private void initComponents() {
        this.chat = parentPanel.getChat();
        this.editorKitProvider = parentPanel.getEditorKitProvider();
        this.config = parentPanel.getConfig();

        this.chat.addContextListener(this);

        chatContentPanel = new ScrollablePanel();
        chatContentPanel.setLayout(new BoxLayout(chatContentPanel, BoxLayout.Y_AXIS));
        chatScrollPane = new JScrollPane(chatContentPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        // Create the button
        JButton scrollToBottomButton = new JButton("↓");
        scrollToBottomButton.setToolTipText("Scroll to Bottom");
        scrollToBottomButton.addActionListener(e -> scrollToBottom());

        // Create a dedicated panel for the button, aligned to the right
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0)); // Add some top padding
        southPanel.add(scrollToBottomButton, BorderLayout.EAST);

        // Add the main components to the ConversationPanel
        add(chatScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    public void redraw() {
        // 1. Capture scroll state BEFORE mutation
        boolean wasAtBottom = isScrolledToBottom();
        int topVisibleIndex = getTopVisibleMessageIndex();

        // 2. Mutate the UI components
        chatContentPanel.removeAll();
        List<ChatMessage> currentContext = chat.getContext();

        for (ChatMessage chatMessage : currentContext) {
            if (chatMessage.getContent() != null) {
                ContentRenderer renderer = new ContentRenderer(editorKitProvider, config);
                int contentIdx = chat.getContextManager().getContext().indexOf(chatMessage);
                JComponent messageComponent = renderer.render(chatMessage, contentIdx, chat.getContextManager());

                ChatMessageJPanel messageContainer = new ChatMessageJPanel(chatMessage);
                messageContainer.setLayout(new BorderLayout());
                messageContainer.add(messageComponent, BorderLayout.CENTER);

                chatContentPanel.add(messageContainer);
            }
        }

        chatContentPanel.revalidate();
        chatContentPanel.repaint();

        // 3. Schedule scroll restoration AFTER layout has been updated
        restoreScrollState(wasAtBottom, topVisibleIndex);
    }

    private boolean isScrolledToBottom() {
        JScrollBar verticalScrollBar = chatScrollPane.getVerticalScrollBar();
        // A tolerance of a few pixels helps account for rounding errors.
        int tolerance = 10;
        return (verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount()) >= (verticalScrollBar.getMaximum() - tolerance);
    }

    private int getTopVisibleMessageIndex() {
        return SwingUtils.getTopmostVisibleComponentIndex(chatScrollPane);
    }

    private void restoreScrollState(boolean wasAtBottom, int topVisibleIndex) {
        SwingUtilities.invokeLater(() -> {
            if (wasAtBottom) {
                scrollToBottom();
            } else if (topVisibleIndex != -1) {
                int newComponentCount = chatContentPanel.getComponentCount();
                if (newComponentCount > 0) {
                    // If the old index is out of bounds (due to pruning), scroll to the new last element.
                    // Otherwise, scroll to the element at the same index to preserve the user's view.
                    int indexToScrollTo = Math.min(topVisibleIndex, newComponentCount - 1);
                    Component componentToScrollTo = chatContentPanel.getComponent(indexToScrollTo);
                    if (componentToScrollTo != null) {
                        chatContentPanel.scrollRectToVisible(componentToScrollTo.getBounds());
                    }
                }
            }
        });
    }

    private void scrollToBottom() {
        if (chatContentPanel.getComponentCount() == 0) {
            return;
        }
        // We need to wait for the layout manager to do its job, hence invokeLater.
        SwingUtilities.invokeLater(() -> {
            Component lastComponent = chatContentPanel.getComponent(chatContentPanel.getComponentCount() - 1);
            if (lastComponent != null) {
                Rectangle bounds = lastComponent.getBounds();
                chatContentPanel.scrollRectToVisible(bounds);
            }
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
}
