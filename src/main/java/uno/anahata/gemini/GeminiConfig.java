package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import uno.anahata.gemini.functions.FunctionConfirmation;

public abstract class GeminiConfig {

    public static final Logger logger = Logger.getLogger(GeminiConfig.class.getName());
    private static final String SYSTEM_INSTRUCTIONS;
    
    private final transient Preferences prefs = Preferences.userNodeForPackage(GeminiConfig.class);

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

    public Part getCoreSystemInstructionPart() {
        String processedManual = SYSTEM_INSTRUCTIONS
                .replace("${work.dir}", getWorkingFolder().getAbsolutePath());
        return Part.fromText(processedManual);
    }

    public abstract List<Part> getHostSpecificSystemInstructionParts();

    public Part getSystemInstructionsAppendix() {
        return Part.fromText(computeDynamicEnvSummary());
    }

    public Content getStartupContent() {
        List<Part> parts = getStartupParts();
        return Content.fromParts(parts.toArray(new Part[parts.size()]));
    }

    public List<Part> getStartupParts() {
        File startupDotMd = new File(getWorkingFolder(), "startup.md");
        if (startupDotMd.exists()) {
            try {
                return Collections.singletonList(Part.fromText(Files.readString(startupDotMd.toPath())));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Exception reading " + startupDotMd + " no startup message will be sent to the model", e);
                return Collections.EMPTY_LIST;
            }

        } else {
            logger.info("File  " + startupDotMd + " does not exist, no startup message will be sent to the model");
            return Collections.EMPTY_LIST;
        }

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

    // --- Preference Management ---
    private String getPreferenceKey(FunctionCall fc, boolean includeArgs) {
        String name = fc.name().orElse("");
        if (!includeArgs) {
            return name;
        }
        // Sort args by key to ensure a stable key regardless of argument order
        Map<String, Object> sortedArgs = new TreeMap<>(fc.args().orElse(Collections.emptyMap()));
        return name + ":" + sortedArgs.toString();
    }

    public FunctionConfirmation getFunctionConfirmation(FunctionCall fc) {
        String key = getPreferenceKey(fc, false); // Preferences are only stored for the function name
        String value = prefs.get(key, null);
        if (value != null) {
            try {
                return FunctionConfirmation.valueOf(value);
            } catch (IllegalArgumentException e) {
                // Stored value is no longer a valid enum constant, remove it
                prefs.remove(key);
                return null;
            }
        }
        return null; // No preference set
    }

    public void setFunctionConfirmation(FunctionCall fc, FunctionConfirmation confirmation) {
        String key;
        switch (confirmation) {
            case ALWAYS:
            case NEVER:
                key = getPreferenceKey(fc, false); // Store preference for the function name only
                prefs.put(key, confirmation.name());
                break;
            default:
                // Don't store volatile confirmations like YES or NO
                return;
        }
    }
    
    public void clearFunctionConfirmation(FunctionCall fc) {
        prefs.remove(getPreferenceKey(fc, true));
        prefs.remove(getPreferenceKey(fc, false));
    }

    // --- Configurability Methods ---
    public String getApiKeyFileName() {
        return "gemini-api-keys.txt";
    }

    public int getFailureTrackerMaxFailures() {
        return 3;
    }

    public long getFailureTrackerTimeWindowMs() {
        return 5 * 60 * 1000; // 5 minutes
    }

    public int getApiMaxRetries() {
        return 5;
    }

    public long getApiInitialDelayMillis() {
        return 1000;
    }

    public long getApiMaxDelayMillis() {
        return 30000;
    }
}
