package uno.anahata.gemini.config.systeminstructions.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.context.stateful.StatefulResourceStatus;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;

public class StatefulResourcesProvider extends SystemInstructionProvider {

    @Override
    public String getId() {
        return "core-stateful-resources";
    }

    @Override
    public String getDisplayName() {
        return "Stateful Resources";
    }

    @Override
    public List<Part> getInstructionParts(GeminiChat chat) {
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
        sb.append("The following stateful resources are tracked in the chat context. 'Status' indicates if the file on disk matches the version in context.\n **Consider either pruning or reloading any resources not market as VALID. **\n\n"); 
        sb.append("| Resource ID | Ctx Last Mod | Ctx Size | Disk Last Mod | Disk Size | Status |\n");
        sb.append("| :--- | ---: | ---: | ---: | ---: | :--- |\n");

        for (StatefulResourceStatus status : statuses) {
            String contextTime = (status.getContextLastModified() > 0) ? String.valueOf(status.getContextLastModified()) : "N/A";
            String diskTime = (status.getDiskLastModified() > 0) ? String.valueOf(status.getDiskLastModified()) : "N/A";
            String contextSize = (status.getContextSize() > 0) ? String.valueOf(status.getContextSize()) : "N/A";
            String diskSize = (status.getDiskSize() > 0) ? String.valueOf(status.getDiskSize()) : "N/A";
            
            sb.append(String.format("| %s | %s | %s | %s | %s | **%s** |\n",
                    status.getResourceId(),
                    contextTime,
                    contextSize,
                    diskTime,
                    diskSize,
                    status.getStatus().name()
            ));
        }

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}
