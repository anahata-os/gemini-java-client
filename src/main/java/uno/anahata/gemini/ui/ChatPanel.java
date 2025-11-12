package uno.anahata.gemini.ui;

import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.context.ContextListener;
import java.util.List;
import uno.anahata.gemini.ui.render.ContentRenderer;

@Slf4j
@Getter
public class ChatPanel extends JPanel implements ContextListener {

    private final GeminiPanel parentPanel;
    private GeminiChat chat;
    private EditorKitProvider editorKitProvider;
    private SwingGeminiConfig config;
    
    // UI Components
    private JPanel chatContentPanel;
    private InputPanel inputPanel;

    public ChatPanel(GeminiPanel parentPanel) {
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
        JScrollPane chatScrollPane = new JScrollPane(chatContentPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(chatScrollPane, BorderLayout.CENTER);

        inputPanel = new InputPanel(parentPanel);
        add(inputPanel, BorderLayout.SOUTH);
    }

    public void redraw() {
        chatContentPanel.removeAll();
        List<ChatMessage> currentContext = chat.getContext();

        ChatMessageJPanel lastMessageContainer = null;
        for (ChatMessage chatMessage : currentContext) {
            if (chatMessage.getContent() != null) {
                ContentRenderer renderer = new ContentRenderer(editorKitProvider, config);
                int contentIdx = chat.getContextManager().getContext().indexOf(chatMessage);
                JComponent messageComponent = renderer.render(chatMessage, contentIdx, chat.getContextManager());

                ChatMessageJPanel messageContainer = new ChatMessageJPanel(chatMessage);
                messageContainer.setLayout(new BorderLayout());
                messageContainer.add(messageComponent, BorderLayout.CENTER);

                chatContentPanel.add(messageContainer);
                lastMessageContainer = messageContainer;
            }
        }

        chatContentPanel.revalidate();
        chatContentPanel.repaint();

        if (lastMessageContainer != null) {
            final ChatMessageJPanel finalLastComponent = lastMessageContainer;
            SwingUtilities.invokeLater(() -> {
                Rectangle bounds = finalLastComponent.getBounds();
                chatContentPanel.scrollRectToVisible(bounds);
            });
        }
    }

    @Override
    public void contextChanged(GeminiChat source) {
        SwingUtilities.invokeLater(this::redraw);
    }

    @Override
    public void contextCleared(GeminiChat source) {
        SwingUtilities.invokeLater(this::redraw);
    }
}
