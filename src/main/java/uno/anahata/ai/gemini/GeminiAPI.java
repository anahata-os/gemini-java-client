/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.gemini;

import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import uno.anahata.ai.config.ChatConfig;
import com.google.genai.Client;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the connection to the Gemini API, including API key rotation, model discovery, and metadata caching.
 * <p>
 * This class loads API keys from a configuration file and provides a round-robin
 * mechanism for selecting a key for each request, helping to distribute load
 * and stay within rate limits. It also dynamically fetches available models
 * and their metadata from the Gemini API.
 * </p>
 */
@Slf4j
public class GeminiAPI {

    /**
     * Default model ID used if no other model is selected.
     */
    public static final String DEFAULT_MODEL_ID = "gemini-3-flash-preview";

    private String modelId = DEFAULT_MODEL_ID;
            
    private String[] keyPool;
    private int round = 0;
    private final ChatConfig config;
    
    /**
     * Cache for model metadata to minimize API calls.
     */
    private final Map<String, Model> modelCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new GeminiAPI instance and loads API keys from the configuration.
     *
     * @param config The chat configuration.
     */
    public GeminiAPI(ChatConfig config) {
        this.config = config;
        loadApiKeys();
        if (keyPool.length > 0) {
            round = new Random().nextInt(keyPool.length);
        } else {
            log.error("No API keys loaded. GeminiAPI will not function correctly.");
        }
    }

    /**
     * Loads API keys from the file specified in the configuration.
     * Keys are filtered for comments and empty lines, and then shuffled.
     */
    private void loadApiKeys() {
        Path keysFilePath = config.getWorkingFolder().toPath().resolve(config.getApiKeyFileName());
        List<String> keys = new ArrayList<>();
        try {
            if (Files.exists(keysFilePath)) {
                keys = Files.lines(keysFilePath)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("//") && !line.startsWith("#"))
                        .map(line -> {
                            int commentIndex = line.indexOf("//");
                            if (commentIndex == -1) {
                                commentIndex = line.indexOf("#");
                            }
                            return (commentIndex != -1) ? line.substring(0, commentIndex).trim() : line;
                        })
                        .collect(Collectors.toList());
            } else {
                log.warn("API keys file not found at: {}", keysFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to load Gemini API keys from " + keysFilePath, e);
        }
        Collections.shuffle(keys);
        this.keyPool = keys.toArray(new String[0]);
    }
    
    /**
     * Checks if any API keys were successfully loaded.
     * 
     * @return true if the key pool is not empty.
     */
    public boolean isHasKeys() {
        return keyPool != null && keyPool.length > 0;
    }

    /**
     * Reloads the API keys from disk and clears the model metadata cache.
     */
    public void reload() {
        log.info("Reloading GeminiAPI...");
        loadApiKeys(); 
        modelCache.clear();
        log.info("GeminiAPI reloaded. Key pool size: {}", keyPool.length);
    }

    /**
     * Gets a new {@link Client} instance using the next available API key in the pool (round-robin).
     *
     * @return A configured Gemini Client.
     * @throws IllegalStateException if no API keys are available.
     */
    public synchronized Client getClient() {
        if (keyPool == null || keyPool.length == 0) {
            throw new IllegalStateException("No API keys available. Cannot create Gemini client.");
        }
        int nextIdx = round++ % keyPool.length;
        String key = keyPool[nextIdx];
        log.info("{} round. poolIndex {} : {}", round, nextIdx, key.substring(Math.max(0, key.length() - 5)));
        return new Client.Builder().apiKey(key).build();
    }

    /**
     * Gets the currently selected model ID.
     *
     * @return The model ID (e.g., "gemini-3-flash-preview").
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * Sets the model ID to be used for subsequent requests.
     *
     * @param modelId The new model ID.
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
        log.info("Gemini model ID set to: {}", modelId);
    }

    /**
     * Gets the list of all available model IDs by querying the Gemini API.
     * 
     * @return A list of model names (e.g., ["models/gemini-3-flash", ...]).
     */
    public List<String> getAvailableModelIds() {
        return getAvailableModels().stream()
                .map(m -> m.name().orElse(""))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Fetches the full list of available models and their metadata from the Gemini API.
     * The results are cached to avoid redundant API calls.
     * 
     * @return A list of {@link Model} objects.
     */
    public List<Model> getAvailableModels() {
        if (modelCache.isEmpty()) {
            try {
                log.info("Fetching available models from Gemini API...");
                Iterable<Model> pager = getClient().models.list(ListModelsConfig.builder().build());
                List<Model> models = StreamSupport.stream(pager.spliterator(), false)
                        .collect(Collectors.toList());
                for (Model m : models) {
                    if (m.name().isPresent()) {
                        modelCache.put(m.name().get(), m);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch models from Gemini API", e);
            }
        }
        return new ArrayList<>(modelCache.values());
    }
    
    /**
     * Returns a list of models that support the 'generateContent' action, 
     * filtered for relevance to the chat assistant.
     * 
     * @return A filtered list of Model objects.
     */
    public List<Model> getFilteredModels() {
        return getAvailableModels().stream()
                .filter(m -> m.supportedActions().isPresent() && m.supportedActions().get().contains("generateContent"))
                .sorted((m1, m2) -> m1.displayName().orElse("").compareTo(m2.displayName().orElse("")))
                .collect(Collectors.toList());
    }
    
    /**
     * Retrieves metadata for a specific model, using the cache if available.
     * 
     * @param modelId The model ID (e.g., "gemini-3-flash-preview").
     * @return The Model metadata object, or null if it could not be retrieved.
     */
    public Model getModelMetadata(String modelId) {
        String targetId = modelId.startsWith("models/") ? modelId : "models/" + modelId;
        if (!modelCache.containsKey(targetId)) {
            getAvailableModels(); // Trigger a full fetch if the cache is empty or missing the ID
        }
        return modelCache.get(targetId);
    }
}
