/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import javax.swing.JPanel;
import lombok.Getter;
import uno.anahata.ai.ChatMessage;

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