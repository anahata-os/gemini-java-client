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
        contextStatusBlock.append(String.format("\nTotal Token Count: %d\nToken Threshold: %d\n",
                chat.getContextManager().getTotalTokenCount(),
                chat.getContextManager().getTokenThreshold()
        ));
        contextStatusBlock.append("\n");
        contextStatusBlock.append("The following table is the Output of ContextWindow.listEntries() with the unique id of every message in this conversation and a list of all parts so you can compress the context by prunning entire messages or individual parts as needed or as instructed by the user. "
                + "If the user asks you to **compress** the context, you must:\n"
                + " 1) summarize everything you are prunning onto an actual text part in your next response (not just the message on the prune tool).  \n"
                + " 2) use the prune tools in ContextWindow to remove entire messages or individual parts that are no longer relevant / needed (note that some prune tools have a reason parameter which is mainly for debugging purpose and verifying the models intent and that reason parameter will only appear on the FunctionCall arguments and will disappear from context because prunning tools are ephemeral) the important thing is that any prunned text parts that have relevant information are included on your text part). \n"
                + "\n Use your discrimination when choosing prunning tools but take into consideration that: "
                + "\na) Pruning a FunctionResponse will automatically prune its corresponding FunctionCall  "
                + "\nb) Pruning a FunctionResponse or a  FunctionCall of a tool annoatated with ContextBehaviour.STATEFUL_REPLACE that rerurned a StatefulResource (with an resourceId) will remove the resource from context (as the content of the StatefulResource is in fhe FunctionResponse part itself)"
                + "\nc) Some parts of the conversation can be offloaded to relevant md files on disk and that It is up to you and the user whether you want to offload anything to disk or just onto a text part before the prune claling tools)"
                + "\nc) Pruning large redundant text parts from the conversation can also help a lot in reducing the total number of tokens (is not always just about pruning stateful resources)"
                
                + "\n"
                + "\n");
        contextStatusBlock.append("\n");
        contextStatusBlock.append(chat.getContextManager().getSessionManager().getSummaryAsString());
        contextStatusBlock.append("\n-------------------------------------------------------------------");

        return Collections.singletonList(Part.fromText(contextStatusBlock.toString()));
    }
}
