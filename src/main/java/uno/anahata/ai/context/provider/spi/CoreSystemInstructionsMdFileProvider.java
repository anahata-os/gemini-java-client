/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider.spi;

import com.google.genai.types.Part;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import uno.anahata.ai.Chat;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.context.provider.ContextProvider;

/**
 * A context provider that loads the core system instructions from a Markdown
 * file in the classpath.
 * <p>
 * These instructions define the AI's identity, core principles, and general
 * operational guidelines.
 * </p>
 */
public class CoreSystemInstructionsMdFileProvider extends ContextProvider {
    private static final String SYSTEM_INSTRUCTIONS;

    static {
        SYSTEM_INSTRUCTIONS = loadManual("/system-instructions.md");
    }
    
    @Override
    public String getId() {
        return "core-system-instructions-md";
    }

    @Override
    public String getDisplayName() {
        return "Core System Instructions";
    }

    @Override
    public List<Part> getParts(Chat chat) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        String processedManual = SYSTEM_INSTRUCTIONS
                .replace("${work.dir}", chat.getConfig().getWorkingFolder().getAbsolutePath());
        return Collections.singletonList(Part.fromText(processedManual));
    }
    
    private static String loadManual(String resourceName) {
        try (InputStream is = CoreSystemInstructionsMdFileProvider.class.getResourceAsStream(resourceName)) {
            if (is == null) {
                return "Error: Could not find resource " + resourceName;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Error loading " + resourceName + ": " + e.getMessage();
        }
    }
}