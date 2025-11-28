package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.stateful.StatefulResourceStatus;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.context.provider.ContextProvider;

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
        sb.append("The following stateful resources are tracked in the chat context. 'Status' indicates if the file on disk matches the version in context.\n **Consider either pruning or reloading any resources not market as VALID. **\n\n"); 
        
        sb.append("| Status | Ctx Last Mod | Resource ID | Ctx Size |\n");
        sb.append("| :--- | ---: | :--- | ---: |\n");

        for (StatefulResourceStatus status : statuses) {
            sb.append(String.format("| **%s** | %d | %s | %d |\n",
                    status.getStatus().name(),
                    status.getContextLastModified(),
                    status.getResourceId(),
                    status.getContextSize()
            ));
        }

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}