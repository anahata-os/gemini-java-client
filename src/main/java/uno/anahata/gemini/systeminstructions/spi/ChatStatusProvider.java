package uno.anahata.gemini.systeminstructions.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;

public class ChatStatusProvider extends SystemInstructionProvider {

    @Override
    public String getId() {
        return "core-chat-status";
    }

    @Override
    public String getDisplayName() {
        return "Chat Status";
    }

    @Override
    public List<Part> getInstructionParts(GeminiChat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        
        StringBuilder chatStatusBlock = new StringBuilder();
        chatStatusBlock.append("- Chat: ").append(chat).append("\n");
        chatStatusBlock.append("- Model Id: ").append(chat.getConfig().getApi().getModelId()).append("\n");
        chatStatusBlock.append("- ContextManager: ").append(chat.getContextManager()).append("\n");
        chatStatusBlock.append("- FunctionManager: ").append(chat.getFunctionManager()).append("\n");
        chatStatusBlock.append("- Session Start time: ").append(chat.getStartTime()).append("\n");
        chatStatusBlock.append("- Live Workspace (auto attaches screen captures on every call) Enabled: ").append(chat.isLiveWorkspaceEnabled()).append("\n");
        chatStatusBlock.append("- Local Functions Enabled: ").append(chat.isFunctionsEnabled()).append("\n");
        chatStatusBlock.append("- Server Side Tools (like google search) Enabled: ").append(!chat.isFunctionsEnabled()).append("\n");
        if (chat.getLatency() > 0) {
            chatStatusBlock.append("- Latency (last successfull user/model round trip): ").append(chat.getLatency()).append(" ms.\n");
        }
        if (chat.getLastApiError() != null) {
            chatStatusBlock.append("- Last API Error: \n").append(chat.getLastApiError()).append("\n");
        }
        
        return Collections.singletonList(Part.fromText(chatStatusBlock.toString()));
    }
}
