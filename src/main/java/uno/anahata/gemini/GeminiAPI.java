package uno.anahata.gemini;

import com.google.genai.Client;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GeminiAPI {

    private static final Logger logger = Logger.getLogger(GeminiAPI.class.getName());

    private static final List<String> AVAILABLE_MODEL_IDS = Arrays.asList(
            "gemini-2.5-pro",
            "gemini-flash-latest",            
            "gemini-flash-lite-latest",            
            "learnlm-2.0-flash-experimental"
    );

    private String modelId = "gemini-2.5-pro";
    private String[] keyPool;
    private int round = 0;

    public GeminiAPI(GeminiConfig config) {
        loadApiKeys(config);
        if (keyPool.length > 0) {
            round = new Random().nextInt(keyPool.length);
        } else {
            logger.log(Level.SEVERE, "No API keys loaded. GeminiAPI will not function correctly.");
        }
    }

    private void loadApiKeys(GeminiConfig config) {
        Path keysFilePath = GeminiConfig.getWorkingFolder().toPath().resolve(config.getApiKeyFileName());
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
            logger.log(Level.SEVERE, "Failed to load Gemini API keys from " + keysFilePath, e);
        }
        this.keyPool = keys.toArray(new String[0]);
    }

    public synchronized Client getClient() {
        if (keyPool.length == 0) {
            throw new IllegalStateException("No API keys available. Cannot create Gemini client.");
        }
        int nextIdx = round++ % keyPool.length;
        String key = keyPool[nextIdx];
        logger.info(round + " round. poolIndex " + nextIdx + " : " + key.substring(key.length() - 5));
        return new Client.Builder().apiKey(key).build();
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        logger.log(Level.INFO, "Gemini model ID set to: {0}", modelId);
    }

    public List<String> getAvailableModelIds() {
        return AVAILABLE_MODEL_IDS;
    }
}
