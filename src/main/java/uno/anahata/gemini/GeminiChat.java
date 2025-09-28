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
import uno.anahata.gemini.functions.FunctionManager; // Use the new FunctionManager2
import uno.anahata.gemini.functions.spi.ContextWindow;

public class GeminiChat {

    private static final Logger logger = Logger.getLogger(GeminiChat.class.getName());
    public static final ThreadLocal<GeminiChat> currentChat = new ThreadLocal<>();

    private final FunctionManager functionManager; // Use the new FunctionManager2
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
        this.functionManager = new FunctionManager(this, config, prompter); // Instantiate the new FunctionManager2
        this.contextManager = new ContextManager(config, listener);
    }

    public long getLatency() {
        return latency;
    }

    public boolean isFunctionsEnabled() {
        return functionsEnabled;
    }

    public void setFunctionsEnabled(boolean functionsEnabled) {
        this.functionsEnabled = functionsEnabled;
    }
    
    /**
     * Helper method for the assistant 
     * 
     * @return the current chat
     */
    public static GeminiChat get() {
        return currentChat.get();
    }
    
    private Content buildSystemInstructions() {
        List<Part> parts = new ArrayList<>();
        
        // 1. Core Principles from file
        parts.add(config.getCoreSystemInstructionPart());
        
        // 2. Host-Specific Role & Directives (e.g., NetBeans info, IDE alerts)
        parts.addAll(config.getHostSpecificSystemInstructionParts());
        
        
        // 3. AI Operational Status
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
        
        // 4. AI Context Index
        
        String contextStatusBlock = "\nContext id:" + contextManager.getContextId();
        contextStatusBlock += String.format("\nTotal Token Count: %d\nToken Threshold: %d\n",
            contextManager.getTotalTokenCount(),
            ContextWindow.TOKEN_THRESHOLD
        );
        
        contextStatusBlock += "\n";
        contextStatusBlock += contextManager.getSummaryAsString();
        contextStatusBlock += "\n-------------------------------------------------------------------";
        
        parts.add(Part.fromText(contextStatusBlock));
        
        // 4. Reference Appendix (Verbose environment details)
        parts.add(config.getSystemInstructionsAppendix());
        
        for (Part part : parts) {
            logger.info("-SystemInstruction:[" + parts.indexOf(part) + "]" + part.text().get());
        }
        return Content.builder().parts(parts).build();
    }
    
    

    private GenerateContentConfig makeGenerateContentConfig() {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .systemInstruction(buildSystemInstructions())
                .thinkingConfig(ThinkingConfig.builder().thinkingBudget(-1))
                .temperature(0f);
        if (functionsEnabled) {
            builder.tools(functionManager.getFunctionTool()).toolConfig(functionManager.getToolConfig());
        } else {
            builder.tools(Tool.builder().googleSearch(GoogleSearch.builder()).build());
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
            contextManager.add(null, content);
        }

        // This is the main loop that sends the context to the model and processes the response.
        // It will continue to loop as long as the model returns function calls.
        // A final non-function-call response will break the loop.
        while (true) {
            GenerateContentResponse resp = sendToModelWithRetry(contextManager.getContext());
            //here we should unblock the UI
            if (resp.candidates().isPresent() && !resp.candidates().get().isEmpty()) {
                Candidate cand = resp.candidates().get().get(0);
                if (cand.content().isPresent()) {
                    Content modelResponse = cand.content().get();
                    contextManager.add(resp.usageMetadata().orElse(null), modelResponse);
                    
                    // Check if the response contains function calls
                    if (!checkResponseForFunctionCalls(modelResponse)) {
                        // If no function calls, we are done with this turn.
                        break; 
                    }
                    // If there were function calls, the loop continues to send the results back to the model.
                    
                } else {
                    // No content in the candidate, so we are done.
                    break;
                }
            } else {
                // No candidates in the response, so we are done.
                contextManager.add(resp.usageMetadata().orElse(null), null);
                break;
            }
        }
    }

    /**
     * Processes function calls from a model's response.
     * @param modelResponse The response from the model.
     * @return true if function calls were processed, false otherwise.
     */
    private boolean checkResponseForFunctionCalls(Content modelResponse) {
        int contentIdx = contextManager.getContext().indexOf(modelResponse);
        List<Content> invocationResults = functionManager.processFunctionCalls(modelResponse, contentIdx);

        if (invocationResults.isEmpty()) {
            return false;
        }

        contextManager.addAll(invocationResults);
        return true;
    }
    
    private void recordLastApiError(Exception e) {
        this.lastApiError = new Date() + "\n" + ExceptionUtils.getStackTrace(e);
    }


    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = 5;
        long initialDelayMillis = 1000; // Start with 1 second
        long maxDelayMillis = 30000;    // Cap at 30 seconds

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                GenerateContentConfig gcc = makeGenerateContentConfig();
                logger.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + context.size() + " content elements. functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = getGoogleGenAIClient().models.generateContent(config.getApi().getModelId(), context, gcc);
                lastApiError = null;
                ts = System.currentTimeMillis() - ts;
                latency = ts;
                logger.info(ts + " ms. Received response from model for " + context.size() + " content elements. functions enabled: " + functionsEnabled);
                return ret; // Success
            } catch (Exception e) {
                recordLastApiError(e);
                if (e.toString().contains("429") || e.toString().contains("503") || e.toString().contains("500")) {
                    if (attempt == maxRetries - 1) {
                        logger.log(Level.SEVERE, "Quota exceeded or server overloaded. Max retries reached. Aborting.", e);
                        throw new RuntimeException("Failed to get response from model after " + maxRetries + " attempts.", e);
                    }

                    // Calculate delay with exponential backoff and jitter
                    long delayMillis = (long) (initialDelayMillis * Math.pow(2, attempt));
                    delayMillis = Math.min(delayMillis, maxDelayMillis); // Cap the delay
                    long jitter = (long) (Math.random() * 500); // Add up to 500ms jitter
                    long totalDelay = delayMillis + jitter;

                    try {
                        logger.info("Quota exceeded or server overloaded. Retrying in " + totalDelay + " ms. " + e);
                        // This sleep is on the SwingWorker's background thread, which is acceptable.
                        Thread.sleep(totalDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Preserve the interrupted status
                        logger.log(Level.WARNING, "Retry delay was interrupted.", ie);
                        throw new RuntimeException("Chat was interrupted during retry delay.", ie);
                    }
                } else {
                    logger.log(Level.SEVERE, "Unknown error received from google's servers", e);
                    throw new RuntimeException(e);
                }
            }
        }
        // This part should be unreachable, but the compiler might require a return statement.
        throw new IllegalStateException("Exited retry loop unexpectedly.");
    }
    
    public Client getGoogleGenAIClient() {
        return config.getClient();
    }

    public List<Content> getContext() {
        return contextManager.getContext();
    }
    
    public ContextManager getContextManager() {
        return contextManager;
    }
    
    
}
