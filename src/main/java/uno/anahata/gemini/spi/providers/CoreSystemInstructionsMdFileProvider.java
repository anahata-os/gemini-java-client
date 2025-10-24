package uno.anahata.gemini.spi.providers;

import com.google.genai.types.Part;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.spi.SystemInstructionProvider;

public class CoreSystemInstructionsMdFileProvider implements SystemInstructionProvider {
    private boolean enabled = true;
    private static final String SYSTEM_INSTRUCTIONS;

    static {
        SYSTEM_INSTRUCTIONS = loadManual("system-instructions.md");
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
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<Part> getInstructionParts() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        String processedManual = SYSTEM_INSTRUCTIONS
                .replace("${work.dir}", GeminiConfig.getWorkingFolder().getAbsolutePath());
        return Collections.singletonList(Part.fromText(processedManual));
    }
    
    private static String loadManual(String resourceName) {
        try (InputStream is = GeminiConfig.class.getResourceAsStream(resourceName)) {
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
