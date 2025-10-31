package uno.anahata.gemini.ui;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.context.ContextListener;
import uno.anahata.gemini.ui.render.ContentRenderer;
import uno.anahata.gemini.ui.render.editorkit.EditorKitProvider;

@Slf4j
public class ChatPanel extends JPanel implements ContextListener {

    private final GeminiChat chat;
    private final EditorKitProvider editorKitProvider;
    private final SwingGeminiConfig config;
    private final JPanel chatContentPanel;

    // New UI Components
    private JTextArea inputTextArea;
    private JButton sendButton;
    private AttachmentsPanel attachmentsPanel;

    public ChatPanel(GeminiChat chat, EditorKitProvider editorKitProvider, SwingGeminiConfig config) {
        super(new BorderLayout(5, 5)); // Add some gaps
        this.chat = chat;
        this.editorKitProvider = editorKitProvider;
        this.config = config;

        // Register as a listener to redraw automatically
        this.chat.addContextListener(this);

        // Center panel for chat messages
        chatContentPanel = new GeminiPanel.ScrollablePanel();
        chatContentPanel.setLayout(new BoxLayout(chatContentPanel, BoxLayout.Y_AXIS));
        JScrollPane chatScrollPane = new JScrollPane(chatContentPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(chatScrollPane, BorderLayout.CENTER);

        // South panel for input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        attachmentsPanel = new AttachmentsPanel();
        inputPanel.add(attachmentsPanel, BorderLayout.NORTH);

        inputTextArea = new JTextArea(3, 20); // Start with 3 rows
        inputTextArea.setLineWrap(true);
        inputTextArea.setWrapStyleWord(true);

        // Ctrl+Enter functionality
        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
        inputTextArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "sendMessage");
        inputTextArea.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());

        JPanel southButtonPanel = new JPanel(new BorderLayout());
        southButtonPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(southButtonPanel, BorderLayout.SOUTH);

        add(inputPanel, BorderLayout.SOUTH);
    }

    public AttachmentsPanel getAttachmentsPanel() {
        return attachmentsPanel;
    }

    private void sendMessage() {
        final String text = inputTextArea.getText().trim();
        final List<Part> stagedParts = attachmentsPanel.getStagedParts();
        if (text.isEmpty() && stagedParts.isEmpty()) {
            return;
        }

        requestInProgress();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (!stagedParts.isEmpty()) {
                    List<Part> apiParts = new ArrayList<>();
                    if (!text.isEmpty()) {
                        apiParts.add(Part.fromText(text));
                    }
                    apiParts.addAll(stagedParts);
                    Content contentForApi = Content.builder().role("user").parts(apiParts).build();
                    chat.sendContent(contentForApi);
                } else {
                    chat.sendText(text);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // To catch any exceptions from doInBackground
                } catch (Exception e) {
                    log.error("Exception sending message", e);
                    // Optionally show an error dialog to the user
                } finally {
                    attachmentsPanel.clearStagedParts();
                    enableInputAndRequestFocus();
                }
            }
        }.execute();
    }

    private void requestInProgress() {
        inputTextArea.setEnabled(false);
        sendButton.setEnabled(false);
    }

    private void enableInputAndRequestFocus() {
        inputTextArea.setText("");
        inputTextArea.setEnabled(true);
        sendButton.setEnabled(true);
        inputTextArea.requestFocusInWindow();
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
