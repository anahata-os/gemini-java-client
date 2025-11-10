package uno.anahata.gemini.config.systeminstructions.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.functions.spi.ContextWindow;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;

public class ContextSummaryProvider extends SystemInstructionProvider {

    @Override
    public String getId() {
        return "core-context-summary";
    }

    @Override
    public String getDisplayName() {
        return "Context Summary";
    }

    @Override
    public List<Part> getInstructionParts(GeminiChat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        
        StringBuilder contextStatusBlock = new StringBuilder();
        contextStatusBlock.append("\nContext id:").append(chat.getContextManager().getContextId());
        contextStatusBlock.append(String.format("\nTotal Token Count: %d\nToken Threshold: %d\n",
                chat.getContextManager().getTotalTokenCount(),
                chat.getContextManager().getTokenThreshold()
        ));
        contextStatusBlock.append("\n");
        contextStatusBlock.append(chat.getContextManager().getSessionManager().getSummaryAsString());
        contextStatusBlock.append("\n-------------------------------------------------------------------");
        
        return Collections.singletonList(Part.fromText(contextStatusBlock.toString()));
    }
}
