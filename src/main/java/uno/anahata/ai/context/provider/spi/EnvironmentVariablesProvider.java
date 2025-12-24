/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextProvider;

public class EnvironmentVariablesProvider extends ContextProvider {

    @Override
    public String getId() {
        return "core-environment-variables";
    }

    @Override
    public String getDisplayName() {
        return "Environment Variables";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- **Environment variables**:\n");
        Map<String, String> env = System.getenv();
        String envString = env.entrySet().stream()
                .map(e -> "  " + e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining("\n"));
        sb.append(envString);

        return Collections.singletonList(Part.fromText(sb.toString()));
    }
}
