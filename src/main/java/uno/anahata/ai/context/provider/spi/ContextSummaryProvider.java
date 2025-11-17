package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import uno.anahata.ai.Chat;
import uno.anahata.ai.tools.spi.ContextWindow;
import uno.anahata.ai.context.provider.ContextProvider;

public class ContextSummaryProvider extends ContextProvider {

    @Override
    public String getId() {
        return "core-context-summary";
    }

    @Override
    public String getDisplayName() {
        return "Context Summary";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder contextStatusBlock = new StringBuilder();
        contextStatusBlock.append(String.format("\nTotal Token Count: %d\nToken Threshold: %d\n",
                chat.getContextManager().getTotalTokenCount(),
                chat.getContextManager().getTokenThreshold()
        ));
        contextStatusBlock.append("\n");
        contextStatusBlock.append("The following table is the Output of ContextWindow.listEntries() with the unique id of every message in this conversation and a list of all parts so you can compress the context as needed or as instructed by the user."
                + "\n\nIf the user asks you to **compress** the context or you are approaching max context window usage (max tokens), you must:\n"
                + "\n1) summarize everything you are prunning onto an actual text part in your next response (not just the message on the prune tool).  \n"
                + "\n2) use the prune tools in ContextWindow to remove: "
                + "    \n\ta) entire messages, "
                + "    \n\tb) specific parts "
                + "    \n\tc) tool calls (call/response pairs)."
                + "\n\n"
                + "Some prune tools have a reason parameter which is mainly for debugging and pruning logic improvement and diagnostics but will disappear from the conversation like every other ephemeral tool call. The Compressed content must be in your 'spoken' response.\n"
                + "\n\n Use your discrimination when choosing prunning tools but take into consideration that:"
                + "\na) Some Parts have logical dependencies (e.g. FunctionCall <-> FunctionResponse)"
                + "\nb) Pruning a message will prune ALL the parts on that message and ALL dependencies of ALL those parts."
                + "\n"
                + "\nIn Other words:"
                + "\n\t1) Pruning a message will prune all its parts"
                + "\n\t2) Pruning a FunctionResponse will automatically prune its corresponding FunctionCall"
                + "\n\t3) Pruning a FunctionCall will automatically prune its corresponding FunctionResponse"
                + "\n\t4) Pruning a FunctionResponse (or a FunctionCall) of a STATEFULE_REPLACE tool that returned an actual Stateful Resource will remove the resource itself from context (as the very content of this resource is in fhe FunctionResponse part itself)"
                + "\n"
                + "\nc) Some parts of the conversation can be offloaded to relevant md files on disk and is up to you and the user whether you want to offload anything to disk or just onto a text part before the prune calls)"
                + "\nd) Compressing text parts from the conversation can also help a lot in reducing the total number of tokens (is not always just about pruning stateful resources)"
                
                + "\n"
                + "\n");
        contextStatusBlock.append("\n");
        contextStatusBlock.append(chat.getContextManager().getSessionManager().getSummaryAsString());
        contextStatusBlock.append("\n-------------------------------------------------------------------");

        return Collections.singletonList(Part.fromText(contextStatusBlock.toString()));
    }
}
