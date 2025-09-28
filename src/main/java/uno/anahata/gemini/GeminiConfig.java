package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class GeminiConfig {

    public static final Logger logger = Logger.getLogger(GeminiConfig.class.getName());
    private static final String SYSTEM_INSTRUCTIONS;
    
    static {
        File workDir = getWorkingFolder();
        if (!workDir.exists()) {
            workDir.mkdirs();
        } else if (!workDir.isDirectory()) {
            throw new RuntimeException("work.dir is not a directory: " + workDir);
        }
        SYSTEM_INSTRUCTIONS = loadManual("system-instructions.md");
    }

    public abstract GeminiAPI getApi();

    public abstract String getApplicationInstanceId();

    public Client getClient() {
        return getApi().getClient();
    }

    // --- NEW Building Block Methods ---

    /**
     * Returns the foundational system instructions (e.g., core principles).
     * @return 
     */
    public Part getCoreSystemInstructionPart() {
        String processedManual = SYSTEM_INSTRUCTIONS
                .replace("${work.dir}", getWorkingFolder().getAbsolutePath());
        return Part.fromText(processedManual);
    }

    /**
     * Returns a list of parts specific to the host environment (e.g., NetBeans-specific directives).
     * To be implemented by subclasses.
     * @return 
     */
    public abstract List<Part> getHostSpecificSystemInstructionParts();

    /**
     * Returns the verbose, low-priority summary of the runtime environment.
     */
    public Part getSystemInstructionsAppendix() {
        return Part.fromText(computeDynamicEnvSummary());
    }
    
    // --- End Building Block Methods ---

    public Content getStartupContent() {
        List<Part> parts = getStartupParts();
        return Content.fromParts(parts.toArray(new Part[parts.size()]));
    }
    
    public List<Part> getStartupParts() {
        return Collections.singletonList(Part.fromText("Read startup.md in your work directory"));
    }

    public List<Class<?>> getAutomaticFunctionClasses() {
        return Collections.emptyList();
    }
    
    public static File getWorkingFolder(String name) {
        File f = new File(getWorkingFolder(), name);
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    public static File getWorkingFolder() {
        File f = new File(System.getProperty("user.home") + File.separator + ".anahata" + File.separator + "ai-assistant");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
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
    
    private String computeDynamicEnvSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n# Dynamic Environment Details");
        sb.append("\n# ---------------------------\n");
        sb.append("- **GeminiConfig: **: ").append(this).append("\n");
        sb.append("- **System Properties**: ").append(System.getProperties().toString()).append("\n");
        sb.append("- **Environment variables**: ").append(System.getenv().toString()).append("\n");
        return sb.toString();
    }
    
    
}
