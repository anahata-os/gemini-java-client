package uno.anahata.gemini;

import uno.anahata.gemini.config.ChatConfig;
import com.google.genai.Client;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeminiAPI {

    private static final List<String> AVAILABLE_MODEL_IDS = Arrays.asList(
            "gemini-pro-latest",
            "gemini-flash-latest",            
            "gemini-flash-lite-latest",            
            "gemini-2.5-flash-image"
    );

    private String modelId = AVAILABLE_MODEL_IDS.getFirst();
            
    private String[] keyPool;
    private int round = 0;

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
        this.keyPool = keys.toArray(new String[0]);
    }

    public synchronized Client getClient() {
        if (keyPool.length == 0) {
            throw new IllegalStateException("No API keys available. Cannot create Gemini client.");
        }
        int nextIdx = round++ % keyPool.length;
        String key = keyPool[nextIdx];
        log.info(round + " round. poolIndex " + nextIdx + " : " + key.substring(key.length() - 5));
        return new Client.Builder().apiKey(key).build();
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        log.info("Gemini model ID set to: {}", modelId);
    }

    public List<String> getAvailableModelIds() {
        return AVAILABLE_MODEL_IDS;
    }
}
