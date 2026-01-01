/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.gemini;

import uno.anahata.ai.config.ChatConfig;
import com.google.genai.Client;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the connection to the Gemini API, including API key rotation and model selection.
 * <p>
 * This class loads API keys from a configuration file and provides a round-robin
 * mechanism for selecting a key for each request, helping to distribute load
 * and stay within rate limits.
 * </p>
 */
@Slf4j
public class GeminiAPI {

    /**
     * The list of Gemini model IDs supported by this client.
     */
    private static final List<String> AVAILABLE_MODEL_IDS = Arrays.asList(
            "gemini-3-flash-preview",
            "gemini-3-flash",
            "gemini-pro-latest",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-3-pro",
            "gemini-3-pro-preview",
            "gemini-flash-latest",            
            "gemini-flash-lite-latest",            
            "gemini-2.5-flash-image"
    );

    private String modelId = AVAILABLE_MODEL_IDS.getFirst();
            
    private String[] keyPool;
    private int round = 0;

    /**
     * Constructs a new GeminiAPI instance and loads API keys from the configuration.
     *
     * @param config The chat configuration.
     */
    public GeminiAPI(ChatConfig config) {
        loadApiKeys(config);
        if (keyPool.length > 0) {
            round = new Random().nextInt(keyPool.length);
        } else {
            log.error("No API keys loaded. GeminiAPI will not function correctly.");
        }
    }

    private void loadApiKeys(ChatConfig config) {
        Path keysFilePath = config.getWorkingFolder().toPath().resolve(config.getApiKeyFileName());
        List<String> keys = new ArrayList<>();
        try {
            keys = Files.lines(keysFilePath)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                    .map(line -> {
                        int commentIndex = line.indexOf("//");
                        return (commentIndex != -1) ? line.substring(0, commentIndex).trim() : line;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to load Gemini API keys from " + keysFilePath, e);
        }
        Collections.shuffle(keys);
        this.keyPool = keys.toArray(new String[0]);
    }

    /**
     * Gets a new {@link Client} instance using the next available API key in the pool.
     *
     * @return A configured Gemini Client.
     * @throws IllegalStateException if no API keys are available.
     */
    public synchronized Client getClient() {
        if (keyPool.length == 0) {
            throw new IllegalStateException("No API keys available. Cannot create Gemini client.");
        }
        int nextIdx = round++ % keyPool.length;
        String key = keyPool[nextIdx];
        log.info(round + " round. poolIndex " + nextIdx + " : " + key.substring(key.length() - 5));
        return new Client.Builder().apiKey(key).build();
    }

    /**
     * Gets the currently selected model ID.
     *
     * @return The model ID.
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
     * Gets the list of all available model IDs.
     *
     * @return The list of model IDs.
     */
    public List<String> getAvailableModelIds() {
        return AVAILABLE_MODEL_IDS;
    }
}