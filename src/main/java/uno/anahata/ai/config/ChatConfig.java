/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.config;

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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.AnahataConfig;
import uno.anahata.ai.gemini.GeminiAPI;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.tools.FunctionConfirmation;
import uno.anahata.ai.context.provider.ContextProvider;
import uno.anahata.ai.context.provider.spi.ChatStatusProvider;
import uno.anahata.ai.context.provider.spi.ContextSummaryProvider;
import uno.anahata.ai.context.provider.spi.CoreSystemInstructionsMdFileProvider;
import uno.anahata.ai.context.provider.spi.EnvironmentVariablesProvider;
import uno.anahata.ai.context.provider.spi.StatefulResourcesProvider;
import uno.anahata.ai.context.provider.spi.SystemPropertiesProvider;
import uno.anahata.ai.tools.spi.ContextWindow;
import uno.anahata.ai.tools.spi.Images;
import uno.anahata.ai.tools.spi.LocalFiles;
import uno.anahata.ai.tools.spi.LocalShell;
import uno.anahata.ai.tools.spi.RunningJVM;
import uno.anahata.ai.tools.spi.Session;

/**
 * Base configuration class for a Gemini AI chat session.
 * <p>
 * This class defines the core settings, available tools, and context providers
 * for a session. It also manages user preferences for tool execution (ALWAYS/NEVER).
 * </p>
 * <p>
 * Subclasses should provide the specific {@code sessionId} and can override
 * tool or provider lists as needed.
 * </p>
 */
@Slf4j
public abstract class ChatConfig {

    private final GeminiAPI api = new GeminiAPI(this);

    private final transient Preferences prefs = Preferences.userNodeForPackage(ChatConfig.class);

    /**
     * The list of context providers registered for this configuration.
     */
    protected final List<ContextProvider> providers = new ArrayList<>();

    /**
     * Constructs a new ChatConfig and initializes the default set of context providers.
     */
    public ChatConfig() {
        providers.add(new CoreSystemInstructionsMdFileProvider());
        providers.add(new ChatStatusProvider());
        providers.add(new ContextSummaryProvider());
        providers.add(new SystemPropertiesProvider());
        providers.add(new EnvironmentVariablesProvider());
        providers.add(new StatefulResourcesProvider());
    }

    /**
     * Gets the Gemini API adapter associated with this configuration.
     *
     * @return The GeminiAPI instance.
     */
    public GeminiAPI getApi() {
        return api;
    }

    /**
     * Gets the unique identifier for the chat session.
     *
     * @return The session ID.
     */
    public abstract String getSessionId();

    /**
     * Gets the file used for automatic session backups.
     *
     * @return The autobackup File.
     */
    public File getAutobackupFile() {
        File sessionsDir = getWorkingFolder("sessions");
        return new File(sessionsDir, "autobackup-" + getSessionId() + ".kryo");
    }

    /**
     * Gets the list of context providers.
     *
     * @return The list of ContextProvider instances.
     */
    public List<ContextProvider> getContextProviders() {
        return providers;
    }

    /**
     * Gets the startup content to be sent to the model when the session is initialized.
     *
     * @return A Content object containing startup instructions.
     */
    public Content getStartupContent() {
        List<Part> parts = getStartupParts();
        return Content.fromParts(parts.toArray(new Part[parts.size()]));
    }

    /**
     * Reads startup instructions from a {@code startup.md} file in the working directory.
     *
     * @return A list of Parts containing the startup text.
     */
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

    /**
     * Gets the list of Java classes containing methods annotated with {@code @AIToolMethod}.
     * These classes define the tools available to the model.
     *
     * @return A list of tool classes.
     */
    public List<Class<?>> getToolClasses() {
        List<Class<?>> allClasses = new ArrayList<>();
        allClasses.add(LocalFiles.class);
        allClasses.add(LocalShell.class);
        allClasses.add(RunningJVM.class);
        allClasses.add(Images.class);
        allClasses.add(ContextWindow.class);
        allClasses.add(Session.class);
        return allClasses;
    }

    /**
     * Gets a subfolder within the application's working directory.
     *
     * @param name The name of the subfolder.
     * @return The subfolder File.
     * @deprecated Use {@link AnahataConfig#getWorkingFolder(String)} directly.
     */
    @Deprecated
    public File getWorkingFolder(String name) {
        return AnahataConfig.getWorkingFolder(name);
    }

    /**
     * Gets the application's root working directory.
     *
     * @return The working directory File.
     * @deprecated Use {@link AnahataConfig#getWorkingFolder()} directly.
     */
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

    /**
     * Retrieves the stored user preference for a specific tool call.
     *
     * @param fc The function call to check.
     * @return The stored FunctionConfirmation (ALWAYS/NEVER), or {@code null} if no preference exists.
     */
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

    /**
     * Stores a user preference for a specific tool call.
     * Only {@code ALWAYS} and {@code NEVER} preferences are persisted.
     *
     * @param fc           The function call.
     * @param confirmation The preference to store.
     */
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

    /**
     * Clears any stored user preferences for a specific tool call.
     *
     * @param fc The function call to clear preferences for.
     */
    public void clearFunctionConfirmation(FunctionCall fc) {
        prefs.remove(getPreferenceKey(fc, true));
        prefs.remove(getPreferenceKey(fc, false));
    }

    // --- Configurability Methods ---
    
    /**
     * Gets the name of the file where Gemini API keys are stored.
     *
     * @return The API key file name.
     */
    public String getApiKeyFileName() {
        return "gemini-api-keys.txt";
    }

    /**
     * Gets the maximum number of consecutive failures allowed for a tool before it is blocked.
     *
     * @return The maximum failure count.
     */
    public int getFailureTrackerMaxFailures() {
        return 3;
    }

    /**
     * Gets the time window within which failures are tracked for blocking.
     *
     * @return The time window in milliseconds.
     */
    public long getFailureTrackerTimeWindowMs() {
        return 5 * 60 * 1000; // 5 minutes
    }

    /**
     * Gets the maximum number of retries for API calls.
     *
     * @return The maximum retry count.
     */
    public int getApiMaxRetries() {
        return 5;
    }

    /**
     * Gets the initial delay for exponential backoff on API retries.
     *
     * @return The initial delay in milliseconds.
     */
    public long getApiInitialDelayMillis() {
        return 1000;
    }

    /**
     * Gets the maximum delay for exponential backoff on API retries.
     *
     * @return The maximum delay in milliseconds.
     */
    public long getApiMaxDelayMillis() {
        return 30000;
    }
}