package uno.anahata.gemini.ui;

import javax.swing.JPanel;
import uno.anahata.gemini.ChatMessage;

/**
 * A custom JPanel that holds a direct reference to the ChatMessage it renders.
 * This is the cornerstone of the precise UI pruning mechanism.
 * @author Anahata
 */
public class ChatMessageJPanel extends JPanel {
    private final ChatMessage chatMessage;

    public ChatMessageJPanel(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }
}
