package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.systeminstructions.spi.ChatStatusProvider;
import uno.anahata.gemini.systeminstructions.spi.ContextSummaryProvider;
import uno.anahata.gemini.systeminstructions.spi.CoreSystemInstructionsMdFileProvider;
import uno.anahata.gemini.systeminstructions.spi.EnvironmentVariablesProvider;
import uno.anahata.gemini.systeminstructions.spi.StatefulResourcesProvider;
import uno.anahata.gemini.systeminstructions.spi.SystemPropertiesProvider;

public abstract class GeminiConfig {

    private final GeminiAPI api = new GeminiAPI(this);

    public static final Logger logger = Logger.getLogger(GeminiConfig.class.getName());

    private final transient Preferences prefs = Preferences.userNodeForPackage(GeminiConfig.class);

    static {
        File workDir = getWorkingFolder();
        if (!workDir.exists()) {
            workDir.mkdirs();
        } else if (!workDir.isDirectory()) {
            throw new RuntimeException("work.dir is not a directory: " + workDir);
        }
    }

    public GeminiAPI getApi() {
        return api;
    }

    public abstract String getApplicationInstanceId();

    public List<SystemInstructionProvider> getSystemInstructionProviders() {
        List<SystemInstructionProvider> providers = new ArrayList<>();
        // Core Providers
        providers.add(new CoreSystemInstructionsMdFileProvider());
        providers.add(new ChatStatusProvider());
        providers.add(new ContextSummaryProvider());
        providers.add(new SystemPropertiesProvider());
        providers.add(new EnvironmentVariablesProvider());
        providers.add(new StatefulResourcesProvider());
        
        // Application Specific Providers
        providers.addAll(getApplicationSpecificInstructionProviders());
        return providers;
    }

    public List<SystemInstructionProvider> getApplicationSpecificInstructionProviders() {
        return Collections.emptyList();
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
