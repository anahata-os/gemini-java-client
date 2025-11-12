package uno.anahata.gemini.config.systeminstructions.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.status.ApiExceptionRecord;

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
        chatStatusBlock.append("- ChatConfig: ").append(chat.getConfig()).append("\n");
        chatStatusBlock.append("- Session Id: ").append(chat.getConfig().getSessionId()).append("\n");
        chatStatusBlock.append("- Model Id: ").append(chat.getConfig().getApi().getModelId()).append("\n");
        chatStatusBlock.append("- ContextManager: ").append(chat.getContextManager()).append("\n");
        chatStatusBlock.append("- FunctionManager: ").append(chat.getFunctionManager()).append("\n");
        chatStatusBlock.append("- ConfigManager: ").append(chat.getConfigManager()).append("\n");
        chatStatusBlock.append("- StatusManager: ").append(chat.getStatusManager()).append("\n");
        chatStatusBlock.append("- Session Start time: ").append(chat.getStartTime()).append("\n");
        chatStatusBlock.append("- Live Workspace (auto attaches screen captures on every call) Enabled: ").append(chat.isLiveWorkspaceEnabled()).append("\n");        
        chatStatusBlock.append("- Server Side Tools (like google search) Enabled: ").append(!chat.isFunctionsEnabled()).append("\n");
        chatStatusBlock.append("- Local @AiToolMethod Tools (e.g. LocalFiles) Enabled: ").append(chat.isFunctionsEnabled()).append("\n");
        if (chat.getLatency() > 0) {
            chatStatusBlock.append("- Latency (last successfull user/model round trip): ").append(chat.getLatency()).append(" ms.\n");
        }

        List<ApiExceptionRecord> errors = chat.getStatusManager().getApiErrors();
        if (!errors.isEmpty()) {
            chatStatusBlock.append("- Recent API Error(s): \n");
            for (int i = 0; i < errors.size(); i++) {
                ApiExceptionRecord error = errors.get(i);
                chatStatusBlock.append("  --- Error ").append(i + 1).append(" ---\n");
                //chatStatusBlock.append(ExceptionUtils.getStackTrace(error.getException()));
                //trying just with the error
                chatStatusBlock.append(error);
                chatStatusBlock.append("\n");
            }
        }

        return Collections.singletonList(Part.fromText(chatStatusBlock.toString()));
    }
}
