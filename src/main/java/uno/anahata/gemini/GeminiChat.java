package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.functions.FunctionManager;

// V2: Refactored to use ChatMessage and the new architecture
public class GeminiChat {

    private static final Logger logger = Logger.getLogger(GeminiChat.class.getName());
    public static final ThreadLocal<GeminiChat> currentChat = new ThreadLocal<>();

    private final FunctionManager functionManager;
    private final GeminiConfig config;
    private final ContextManager contextManager;
    private long latency = -1;
    private boolean functionsEnabled = true;
    private String lastApiError = null;
    private Date startTime;
        
    public GeminiChat(
            GeminiConfig config,
            FunctionPrompter prompter,
            ContextListener listener) {
        this.config = config;
        this.functionManager = new FunctionManager(this, config, prompter);
        this.contextManager = new ContextManager(config, listener);
    }

    public long getLatency() {
        return latency;
    }

    public boolean isFunctionsEnabled() {
        return functionsEnabled;
    }

    public void setFunctionsEnabled(boolean functionsEnabled) {
        logger.info("User toggled functions: " + functionsEnabled);
        this.functionsEnabled = functionsEnabled;
    }
    
    public static GeminiChat get() {
        return currentChat.get();
    }
    
    private Content buildSystemInstructions() {
        List<Part> parts = new ArrayList<>();
        parts.add(config.getCoreSystemInstructionPart());
        parts.addAll(config.getHostSpecificSystemInstructionParts());
        
        String chatStatusBlock = "- Chat: " + this + "\n";
        chatStatusBlock += "- Model Id: " + config.getApi().getModelId()+ "\n";
        chatStatusBlock += "- ContextManager: " + contextManager+ "\n";
        chatStatusBlock += "- FunctionManager: " + functionManager+ "\n";
        chatStatusBlock += "- Session Start time: " + startTime + "\n";
        chatStatusBlock += "- Functions Enabled: " + functionsEnabled + "\n";
        if (latency > 0) {
            chatStatusBlock += "- Latency (last successfull user/model round trip): " + latency + " ms.\n";
        }
        if (lastApiError != null) {
            chatStatusBlock += "- Last API Error: \n" + lastApiError + " ms.\n";
        }
        parts.add(Part.fromText(chatStatusBlock));
        
        String contextStatusBlock = "\nContext id:" + contextManager.getContextId();
        contextStatusBlock += String.format("\nTotal Token Count: %d\nToken Threshold: %d\n",
            contextManager.getTotalTokenCount(),
            uno.anahata.gemini.functions.spi.ContextWindow.getTokenThreshold()
        );
        contextStatusBlock += "\n";
        contextStatusBlock += contextManager.getSummaryAsString();
        contextStatusBlock += "\n-------------------------------------------------------------------";
        parts.add(Part.fromText(contextStatusBlock));
        
        parts.add(config.getSystemInstructionsAppendix());
        
        return Content.builder().parts(parts).build();
    }
    
    private GenerateContentConfig makeGenerateContentConfig() {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .systemInstruction(buildSystemInstructions())
                .temperature(0f);
        
        if (functionsEnabled) {
            builder
                    .tools(functionManager.getFunctionTool())
                    .toolConfig(functionManager.getToolConfig());
        } else {
            Tool googleTools = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();
            builder.tools(googleTools);
        }
        
        return builder.build();
    }

    public void init() {
        startTime = new Date();
        Content startupContent = config.getStartupContent();
        if (startupContent != null && startupContent.parts().isPresent() && !startupContent.parts().get().isEmpty()) {
            logger.info("Sending one-time startup instructions to the model.");
            sendContent(startupContent);
        }
    }

    public void clear() {
        contextManager.clear();
    }

    public void sendText(String message) {
        sendContent(Content.fromParts(Part.fromText(message)));
    }

    public void sendContent(Content content) {
        if (content != null) {
            ChatMessage userMessage = new ChatMessage(config.getApi().getModelId(), content, null, null, null);
            contextManager.add(userMessage);
        }

        while (true) {
            List<Content> apiContext = buildApiContext(contextManager.getContext());
            GenerateContentResponse resp = sendToModelWithRetry(apiContext);

            if (resp.candidates().isPresent() && !resp.candidates().get().isEmpty()) {
                Candidate cand = resp.candidates().get().get(0);
                if (cand.content().isPresent()) {
                    Content modelResponseContent = cand.content().get();
                    ChatMessage modelMessage = new ChatMessage(
                        config.getApi().getModelId(),
                        modelResponseContent,
                        null, // Responses will be linked later
                        resp.usageMetadata().orElse(null),
                        cand.groundingMetadata().orElse(null)
                    );
                    contextManager.add(modelMessage);
                    
                    if (!processAndReloopForFunctionCalls(modelMessage)) {
                        break; 
                    }
                } else {
                    break;
                }
            } else {
                // Handle cases with no candidates (e.g., safety filters)
                ChatMessage emptyModelMessage = new ChatMessage(config.getApi().getModelId(), null, null, resp.usageMetadata().orElse(null), null);
                contextManager.add(emptyModelMessage);
                break;
            }
        }
    }

    private boolean processAndReloopForFunctionCalls(ChatMessage modelMessage) {
        List<FunctionResponse> functionResponses = functionManager.processFunctionCalls(modelMessage);

        if (functionResponses.isEmpty()) {
            return false; // No function calls, break the loop
        }

        // Create a new message to hold the function responses
        Content functionResponseContent = Content.builder()
            .role("tool") // Use "tool" role for function responses
            .parts(functionResponses.stream().map(fr -> (Part) Part.fromFunctionResponse(fr.name().get(), (java.util.Map)fr.response().get())).collect(java.util.stream.Collectors.toList()))
            .build();
            
        ChatMessage functionResponseMessage = new ChatMessage(
            config.getApi().getModelId(),
            functionResponseContent,
            null, // This message *is* the response, no further responses to link
            null, // No new usage metadata for this internal step
            null
        );
        contextManager.add(functionResponseMessage);
        
        return true; // Function calls were processed, continue the loop
    }
    
    private void recordLastApiError(Exception e) {
        this.lastApiError = new Date() + "\n" + ExceptionUtils.getStackTrace(e);
    }

    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = 5;
        long initialDelayMillis = 1000;
        long maxDelayMillis = 30000;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                GenerateContentConfig gcc = makeGenerateContentConfig();
                logger.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + context.size() + " content elements. functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = getGoogleGenAIClient().models.generateContent(config.getApi().getModelId(), context, gcc);
                lastApiError = null;
                latency = System.currentTimeMillis() - ts;
                logger.info(latency + " ms. Received response from model for " + context.size() + " content elements.");
                return ret;
            } catch (Exception e) {
                recordLastApiError(e);
                if (e.toString().contains("429") || e.toString().contains("503") || e.toString().contains("500")) {
                    if (attempt == maxRetries - 1) {
                        logger.log(Level.SEVERE, "Max retries reached. Aborting.", e);
                        throw new RuntimeException("Failed to get response from model after " + maxRetries + " attempts.", e);
                    }
                    long delayMillis = (long) (initialDelayMillis * Math.pow(2, attempt));
                    delayMillis = Math.min(delayMillis, maxDelayMillis);
                    long jitter = (long) (Math.random() * 500);
                    try {
                        Thread.sleep(delayMillis + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Chat was interrupted during retry delay.", ie);
                    }
                } else {
                    logger.log(Level.SEVERE, "Unknown error from Google's servers", e);
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalStateException("Exited retry loop unexpectedly.");
    }
    
    private List<Content> buildApiContext(List<ChatMessage> chatHistory) {
        List<Content> apiContext = new ArrayList<>();
        // TODO: Implement the "Active Workspace" consolidation here
        for (ChatMessage message : chatHistory) {
            if (message.getContent() != null) {
                apiContext.add(message.getContent());
            }
        }
        return apiContext;
    }

    public Client getGoogleGenAIClient() {
        return config.getClient();
    }

    public List<ChatMessage> getContext() {
        return contextManager.getContext();
    }
    
    public ContextManager getContextManager() {
        return contextManager;
    }
}
