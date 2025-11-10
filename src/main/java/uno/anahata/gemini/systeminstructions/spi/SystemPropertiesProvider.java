package uno.anahata.gemini.systeminstructions.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;

public class SystemPropertiesProvider extends SystemInstructionProvider {

    @Override
    public String getId() {
        return "core-system-properties";
    }

    @Override
    public String getDisplayName() {
        return "System Properties";
    }

    @Override
    public List<Part> getInstructionParts(GeminiChat chat) {
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
