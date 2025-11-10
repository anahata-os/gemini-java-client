package uno.anahata.gemini.config;

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
import java.util.prefs.Preferences;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.AnahataConfig;
import uno.anahata.gemini.GeminiAPI;
import uno.anahata.gemini.functions.FunctionConfirmation;
import uno.anahata.gemini.config.systeminstructions.SystemInstructionProvider;
import uno.anahata.gemini.systeminstructions.spi.ChatStatusProvider;
import uno.anahata.gemini.systeminstructions.spi.ContextSummaryProvider;
import uno.anahata.gemini.systeminstructions.spi.CoreSystemInstructionsMdFileProvider;
import uno.anahata.gemini.systeminstructions.spi.EnvironmentVariablesProvider;
import uno.anahata.gemini.systeminstructions.spi.StatefulResourcesProvider;
import uno.anahata.gemini.systeminstructions.spi.SystemPropertiesProvider;

@Slf4j
public abstract class ChatConfig {

    private final GeminiAPI api = new GeminiAPI(this);

    private final transient Preferences prefs = Preferences.userNodeForPackage(ChatConfig.class);

    
    public GeminiAPI getApi() {
        return api;
    }

    public abstract String getSessionId();
    
    public File getAutobackupFile() {
        File sessionsDir = getWorkingFolder("sessions");
        return new File(sessionsDir, "autobackup-" + getSessionId() + ".kryo");
    }

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
                log.error("Exception reading " + startupDotMd + " no startup message will be sent to the model", e);
                return Collections.EMPTY_LIST;
            }

        } else {
            log.info("File  " + startupDotMd + " does not exist, no startup message will be sent to the model");
            return Collections.EMPTY_LIST;
        }
    }
    
    public List<Part> getLiveWorkspaceParts() {
        return Collections.emptyList();
    }

    public List<Class<?>> getAutomaticFunctionClasses() {
        return Collections.emptyList();
    }
    
    @Deprecated
    public File getWorkingFolder(String name) {
        return AnahataConfig.getWorkingFolder(name);
    }
    
    @Deprecated
    public File getWorkingFolder() {
        return AnahataConfig.getWorkingFolder();
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
