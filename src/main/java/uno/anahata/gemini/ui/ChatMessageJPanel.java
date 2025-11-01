package uno.anahata.gemini.ui;

import javax.swing.JPanel;
import lombok.Getter;
import uno.anahata.gemini.ChatMessage;

/**
 * A JPanel that holds a single rendered ChatMessage.
 */
@Getter
public class ChatMessageJPanel extends JPanel {
    private final ChatMessage chatMessage;

    public ChatMessageJPanel(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }
}
