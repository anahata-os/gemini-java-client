package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.context.stateful.ResourceStatus;

public class StatefulResourcesProvider extends ContextProvider {

    public StatefulResourcesProvider() {
        super(ContextPosition.AUGMENTED_WORKSPACE);
    }

    
    @Override
    public String getId() {
        return "core-stateful-resources";
    }

    @Override
    public String getDisplayName() {
        return "Stateful Resources";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        ContextManager cm = chat.getContextManager();
        List<StatefulResourceStatus> statuses = cm.getResourceTracker().getStatefulResourcesOverview();
        
        if (statuses.isEmpty()) {
            return Collections.singletonList(Part.fromText("No stateful resources currently tracked in context."));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Stateful Resources in Context\n");
        sb.append("The content of the following stateful resources are tracked in the chat context. 'Status' indicates if the file on disk matches the version in context. The 'Part Id' column indicates the location within the chat history (messageId/partIdx) of the FunctionResponse part that contains the FileInfo object (including the file's content, size, and lastModified timestamp).\n **Do not reload resources marked VALID (e.g. readFile).**\n\nConsider **pruning or reloading** any resources that are not marked as **VALID**\n\n"); 
        
        // Corrected header row with specified titles
        sb.append("| Status | Last Modified | Part Id | Tool Call ID | Resource ID | Size |\n");
        // Separator row
        sb.append("| :--- | ---: | :--- | :--- | :--- | ---: |\n");

        for (StatefulResourceStatus status : statuses) {
            // Display Last Modified and Size only if status is VALID
            String lastModifiedDisplay = status.getStatus() == ResourceStatus.VALID ? String.valueOf(status.getContextLastModified()) : "N/A";
            String sizeDisplay = status.getStatus() == ResourceStatus.VALID ? String.valueOf(status.getContextSize()) : "N/A";

            sb.append(String.format("| **%s** | %s | %s | %s | %s | %s |\n",
                    status.getStatus().name(),
                    lastModifiedDisplay,
                    status.getPartId() != null ? status.getPartId() : "N/A",
                    status.getToolCallId() != null ? status.getToolCallId() : "N/A",
                    status.getResourceId(),
                    sizeDisplay
            ));
        }

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}