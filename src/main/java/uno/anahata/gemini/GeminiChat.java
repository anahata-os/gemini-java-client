package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.functions.FunctionPrompter;

/**
 * V3: The central orchestrator for the chat, refactored to use the robust ChatMessage model.
 * It manages the main message loop, API communication, and coordinates with the
 * ContextManager and FunctionManager to handle the full lifecycle of a conversation turn.
 * It introduces the "Active Workspace" concept to ensure stateful resources are always in context.
 * @author Anahata
 */
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
        chatStatusBlock += "- Model Id: " + config.getApi().getModelId() + "\n";
        chatStatusBlock += "- ContextManager: " + contextManager + "\n";
        chatStatusBlock += "- FunctionManager: " + functionManager + "\n";
        chatStatusBlock += "- Session Start time: " + startTime + "\n";
        chatStatusBlock += "- Functions Enabled: " + functionsEnabled + "\n";
        if (latency > 0) {
            chatStatusBlock += "- Latency (last successfull user/model round trip): " + latency + " ms.\n";
        }
        if (lastApiError != null) {
            chatStatusBlock += "- Last API Error: \n" + lastApiError + "\n";
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
        sendContent(Content.builder().role("user").parts(Part.fromText(message)).build());
    }

    public void sendContent(Content content) {
        if (content != null) {
            ChatMessage userMessage = new ChatMessage(config.getApi().getModelId(), content, null, null, null);
            contextManager.add(userMessage);
        }

        // Main processing loop
        while (true) {
            List<Content> apiContext = buildApiContext(contextManager.getContext());
            GenerateContentResponse resp = sendToModelWithRetry(apiContext);

            if (resp.candidates() == null || !resp.candidates().isPresent() || resp.candidates().get().isEmpty()) {
                logger.warning("Received response with no candidates. Possibly due to safety filters. Breaking loop.");
                Content emptyContent = Content.builder().role("model").parts(Part.fromText("[No response from model]")).build();
                ChatMessage emptyModelMessage = new ChatMessage(config.getApi().getModelId(), emptyContent, null, resp.usageMetadata().orElse(null), null);
                contextManager.add(emptyModelMessage);
                break;
            }
            
            Candidate cand = resp.candidates().get().get(0);
            if (cand.content() == null || !cand.content().isPresent()) {
                 logger.warning("Received candidate with no content. Breaking loop.");
                 break;
            }

            Content modelResponseContent = cand.content().get();
            ChatMessage modelMessage = new ChatMessage(
                config.getApi().getModelId(),
                modelResponseContent,
                null,
                resp.usageMetadata().orElse(null),
                cand.groundingMetadata().orElse(null)
            );
            contextManager.add(modelMessage);

            // Check for function calls and re-loop if necessary
            if (!processAndReloopForFunctionCalls(modelMessage)) {
                break; // No function calls, so the turn is complete.
            }
        }
    }

    private boolean processAndReloopForFunctionCalls(ChatMessage modelMessageWithCalls) {
        List<FunctionResponse> functionResponses = functionManager.processFunctionCalls(modelMessageWithCalls);

        if (functionResponses.isEmpty()) {
            return false; // No function calls were approved or executed, break the loop.
        }

        // Link responses back to the original message
        modelMessageWithCalls.setFunctionResponses(functionResponses);

        // Create a new message to hold all the function responses for this turn.
        List<Part> responseParts = functionResponses.stream()
            .map(fr -> {
                // The SDK requires a Map for the response, so we ensure it is one.
                Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
                return Part.fromFunctionResponse(fr.name().get(), responseMap);
            })
            .collect(Collectors.toList());
            
        Content functionResponseContent = Content.builder()
            .role("tool")
            .parts(responseParts)
            .build();
            
        ChatMessage functionResponseMessage = new ChatMessage(
            config.getApi().getModelId(),
            functionResponseContent,
            modelMessageWithCalls.getId(), // Link to the message that made the call
            null, // No new usage metadata for this internal step
            null
        );
        contextManager.add(functionResponseMessage);
        
        return true; // Function calls were processed, continue the loop to get the model's summary.
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
                logger.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + context.size() + " content elements. Functions enabled: " + functionsEnabled);
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

    /**
     * This is the core of the "Active Workspace" concept.
     * It converts our rich ChatMessage history into the flat List<Content>
     * that the Google API expects, while ensuring that all stateful resources
     * are consolidated and presented to the model in every turn.
     * @param chatHistory The full history of ChatMessage objects.
     * @return A List of Content objects ready for the API.
     */
    private List<Content> buildApiContext(List<ChatMessage> chatHistory) {
        // 1. Direct conversion of all messages to their raw Content.
        List<Content> apiContext = chatHistory.stream()
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 2. ACTIVE WORKSPACE CONSOLIDATION:
        //    Scan the entire history for the *latest version* of any stateful resources
        //    (like files) and consolidate them into a single, final "tool" content block.
        //    This ensures the model *always* has the most current view of the workspace.
        Map<String, Part> activeWorkspace = new java.util.HashMap<>();
        for (ChatMessage message : chatHistory) {
            if (message.getContent() == null || !message.getContent().parts().isPresent()) continue;
            
            for (Part part : message.getContent().parts().get()) {
                if (part.functionResponse().isPresent()) {
                    FunctionResponse fr = part.functionResponse().get();
                    String toolName = fr.name().orElse("");
                    
                    // This logic mirrors ContextManager's stateful check.
                    if (toolName.equals("LocalFiles.readFile") || toolName.equals("LocalFiles.writeFile")) {
                        Optional.ofNullable(fr.response().get())
                            .filter(r -> r instanceof Map)
                            .map(r -> ((Map<String, Object>) r).get("path"))
                            .filter(p -> p instanceof String)
                            .map(p -> (String) p)
                            .ifPresent(path -> activeWorkspace.put(path, part)); // Overwrites older versions
                    }
                }
            }
        }

        if (!activeWorkspace.isEmpty()) {
            logger.info("Active Workspace: Consolidating " + activeWorkspace.size() + " stateful resources.");
            Content workspaceContent = Content.builder()
                .role("tool")
                .parts(new ArrayList<>(activeWorkspace.values()))
                .build();
            apiContext.add(workspaceContent);
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
