package uno.anahata.gemini;

import com.google.genai.Client;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays; // Import for Arrays.asList
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A client for interacting with the Google Gemini API, managing chat history.
 */
public class GeminiAPI {

    private static final Logger logger = Logger.getLogger(GeminiAPI.class.getName());

    // Hardcoded list of available model IDs for the combobox
    private static final List<String> AVAILABLE_MODEL_IDS = Arrays.asList(
            "gemini-2.5-pro",
            "gemini-flash-latest",            
            "gemini-flash-lite-latest",            
            "learnlm-2.0-flash-experimental"
    );

    private String modelId = "gemini-2.5-flash"; // Now mutable

    private String[] keyPool;
    private int round = 0; // Initialize round to 0, it will be set by Random after keyPool is populated

    /**
     * Constructs a new GeminiClient.
     */
    public GeminiAPI(File workFolder) {
        loadApiKeys(workFolder);
        if (keyPool.length > 0) {
            round = new Random().nextInt(keyPool.length);
        } else {
            logger.log(Level.SEVERE, "No API keys loaded. GeminiAPI will not function correctly.");
        }
    }

    private void loadApiKeys(File workFolder) {

        Path keysFilePath = new File(workFolder, "gemini-api-keys.txt").toPath();
        List<String> keys = new ArrayList<>();
        try {
            keys = Files.lines(keysFilePath)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("//")) // Ignore empty lines and full-line comments
                    .map(line -> {
                        int commentIndex = line.indexOf("//");
                        return (commentIndex != -1) ? line.substring(0, commentIndex).trim() : line;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load Gemini API keys from " + keysFilePath + ". Please ensure the file exists and is readable.", e);
        }
        this.keyPool = keys.toArray(new String[0]);
    }

    public synchronized Client getClient() {
        if (keyPool.length == 0) {
            throw new IllegalStateException("No API keys available. Cannot create Gemini client.");
        }
        int nextIdx = round++ % keyPool.length;
        String key = keyPool[nextIdx];
        logger.info(round + " round. poolIndex " + nextIdx + " : " + key.substring(key.length() - 5, key.length()));
        return new Client.Builder().apiKey(key).build();
    }

    public String getModelId() {
        return modelId;
    }

    /**
     * Sets the currently active model ID.
     * @param modelId The new model ID to use.
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
        logger.log(Level.INFO, "Gemini model ID set to: {0}", modelId);
    }

    /**
     * Returns a list of available Gemini model IDs.
     * @return A List of String representing available model IDs.
     */
    public List<String> getAvailableModelIds() {
        return AVAILABLE_MODEL_IDS;
    }
}
