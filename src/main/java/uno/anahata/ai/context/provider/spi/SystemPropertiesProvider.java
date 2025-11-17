package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextProvider;

public class SystemPropertiesProvider extends ContextProvider {

    @Override
    public String getId() {
        return "core-system-properties";
    }

    @Override
    public String getDisplayName() {
        return "System Properties";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- **System Properties**:\n");
        Properties props = System.getProperties();
        String propsString = props.entrySet().stream()
                .map(e -> "  " + e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining("\n"));
        sb.append(propsString);

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}
