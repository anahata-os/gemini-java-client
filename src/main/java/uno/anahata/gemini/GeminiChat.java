package uno.anahata.gemini;

import uno.anahata.gemini.functions.JobInfo;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.context.ContextListener;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.functions.FunctionManager.FunctionProcessingResult;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;

@Slf4j
public class GeminiChat {

    private static final Gson GSON = GsonUtils.getGson();
    public static final ThreadLocal<GeminiChat> callingInstance = new ThreadLocal<>();

    private final FunctionManager functionManager;
    private final GeminiConfig config;
    private final ContextManager contextManager;
    private long latency = -1;
    private boolean functionsEnabled = true;
    private String lastApiError = null;
    private volatile boolean isProcessing = false;
    private Date startTime;
    private List<SystemInstructionProvider> systemInstructionProviders;

    public GeminiChat(
            GeminiConfig config,
            FunctionPrompter prompter,
            ContextListener listener) {
        this.config = config;
        this.contextManager = new ContextManager(this, config, listener);
        this.functionManager = new FunctionManager(this, config, prompter);
        this.contextManager.setFunctionManager(this.functionManager);
        // Initialize providers here to take a copy from config
        this.systemInstructionProviders = new ArrayList<>(config.getSystemInstructionProviders());
    }

    public long getLatency() {
        return latency;
    }

    public boolean isFunctionsEnabled() {
        return functionsEnabled;
    }

    public void setFunctionsEnabled(boolean functionsEnabled) {
        log.info("User toggled functions: " + functionsEnabled);
        this.functionsEnabled = functionsEnabled;
    }

    public static GeminiChat getCallingInstance() {
        return callingInstance.get();
    }
    
    public List<SystemInstructionProvider> getSystemInstructionProviders() {
        // Return the list initialized in the constructor
        return systemInstructionProviders;
    }

    private Content buildSystemInstructions() {
        List<Part> parts = new ArrayList<>();
        
        for (SystemInstructionProvider provider : getSystemInstructionProviders()) {
            if (provider.isEnabled()) {
                try {
                    parts.addAll(provider.getInstructionParts(this));
                } catch (Exception e) {
                    log.warn("SystemInstructionProvider " + provider.getId() + " threw an exception", e);
                    parts.add(Part.fromText("Error in " + provider.getDisplayName() + ": " + e.getMessage()));
                }
            }
        }

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
            log.info("Sending one-time startup instructions to the model.");
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
        if (isProcessing) {
            log.warn("A request is already in progress. Ignoring new request.");
            return;
        }
        isProcessing = true;
        try {
            if (content != null) {
                ChatMessage userMessage = new ChatMessage(config.getApi().getModelId(), content, null, null, null);
                contextManager.add(userMessage);
            }

            while (true) {
                List<Content> apiContext = buildApiContext(contextManager.getContext());
                GenerateContentResponse resp = sendToModelWithRetry(apiContext);

                if (resp.candidates() == null || !resp.candidates().isPresent() || resp.candidates().get().isEmpty()) {
                    log.warn("Received response with no candidates. Possibly due to safety filters. Breaking loop.");
                    Content emptyContent = Content.builder().role("model").parts(Part.fromText("[No response from model]")).build();
                    ChatMessage emptyModelMessage = new ChatMessage(config.getApi().getModelId(), emptyContent, null, resp.usageMetadata().orElse(null), null);
                    contextManager.add(emptyModelMessage);
                    break;
                }

                Candidate cand = resp.candidates().get().get(0);
                if (cand.content() == null || !cand.content().isPresent()) {
                    log.warn("Received candidate with no content. Breaking loop.");
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

                if (!processAndReloopForFunctionCalls(modelMessage)) {
                    break; 
                }
            }
        } finally {
            isProcessing = false;
        }
    }

    private boolean processAndReloopForFunctionCalls(ChatMessage modelMessageWithCalls) {
        // Step 1: Check for function calls and if they are enabled.
        List<FunctionCall> allProposedCalls = modelMessageWithCalls.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionCall().isPresent())
                .map(part -> part.functionCall().get())
                .collect(Collectors.toList());

        if (allProposedCalls.isEmpty()) {
            return false; // No function calls, so we break the loop.
        }

        if (!functionsEnabled) {
            String attemptedCalls = allProposedCalls.stream()
                    .map(fc -> fc.name().orElse("unnamed function"))
                    .collect(Collectors.joining(", "));
            String feedbackText = "User Feedback: The model attempted to call the following tool(s) while local functions were disabled: [" + attemptedCalls + "]. The calls were ignored.";
            Content feedbackContent = Content.builder().role("user").parts(Part.fromText(feedbackText)).build();
            ChatMessage feedbackMessage = new ChatMessage(config.getApi().getModelId(), feedbackContent, null, null, null);
            contextManager.add(feedbackMessage);
            return false; // Stop processing and do not re-loop.
        }

        // Step 2: Process the function calls (prompt user, execute, etc.)
        FunctionProcessingResult processingResult = functionManager.processFunctionCalls(modelMessageWithCalls);
        List<FunctionResponse> functionResponses = processingResult.responses;

        // Step 3: If there are responses, add the TOOL message to the context IMMEDIATELY.
        // This is critical to maintain the [MODEL: FunctionCall] -> [TOOL: FunctionResponse] sequence.
        if (!functionResponses.isEmpty()) {
            modelMessageWithCalls.setFunctionResponses(functionResponses);

            Map<Part, Part> partLinks = new HashMap<>();
            List<Part> responseParts = new ArrayList<>();

            for (FunctionResponse fr : functionResponses) {
                Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
                Part responsePart = Part.fromFunctionResponse(fr.name().get(), responseMap);
                responseParts.add(responsePart);

                Part sourceCallPart = processingResult.responseToCallLinks.get(fr);
                if (sourceCallPart != null) {
                    partLinks.put(responsePart, sourceCallPart);
                }
            }

            Content functionResponseContent = Content.builder()
                    .role("tool")
                    .parts(responseParts)
                    .build();

            ChatMessage functionResponseMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    config.getApi().getModelId(),
                    functionResponseContent,
                    modelMessageWithCalls.getId(),
                    null,
                    null,
                    partLinks
            );
            contextManager.add(functionResponseMessage);
        }

        // Step 4: Now, formulate and add the USER feedback message (if any).
        boolean wasAutopilot = "Autopilot".equals(processingResult.userComment);
        boolean hasDeniedCalls = processingResult.deniedCalls != null && !processingResult.deniedCalls.isEmpty();
        boolean hasNonAutopilotComment = processingResult.userComment != null && !processingResult.userComment.trim().isEmpty() && !wasAutopilot;

        if (wasAutopilot || hasDeniedCalls || hasNonAutopilotComment) {
            StringBuilder feedbackText = new StringBuilder("User has provided feedback on the proposed tool calls:\n");
            
            if (wasAutopilot) {
                int approvedCount = processingResult.responses.size();
                feedbackText.append("- Autopilot: ").append(approvedCount).append(" tool call(s) were pre-approved and executed automatically.\n");
            }
            
            if (hasDeniedCalls) {
                String denied = processingResult.deniedCalls.stream()
                        .map(fc -> fc.name().orElse("unnamed function"))
                        .collect(Collectors.joining(", "));
                feedbackText.append("- The following calls were denied: [").append(denied).append("]\n");
            }
            
            if (hasNonAutopilotComment) {
                feedbackText.append("- User's comment: '").append(processingResult.userComment).append("'\n");
            }

            Content feedbackContent = Content.builder().role("user").parts(Part.fromText(feedbackText.toString())).build();
            ChatMessage feedbackMessage = new ChatMessage(config.getApi().getModelId(), feedbackContent, null, null, null);
            contextManager.add(feedbackMessage);
        }

        // Step 5: Decide whether to re-loop.
        // We re-loop if we executed any functions OR if the user provided feedback (e.g., denied a call).
        return !functionResponses.isEmpty() || hasDeniedCalls || hasNonAutopilotComment;
    }

    private void recordLastApiError(Exception e) {
        this.lastApiError = new Date() + "\n" + ExceptionUtils.getStackTrace(e);
    }

    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = config.getApiMaxRetries();
        long initialDelayMillis = config.getApiInitialDelayMillis();
        long maxDelayMillis = config.getApiMaxDelayMillis();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                GenerateContentConfig gcc = makeGenerateContentConfig();
                log.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + context.size() + " content elements. Functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = getGoogleGenAIClient().models.generateContent(config.getApi().getModelId(), context, gcc);
                lastApiError = null;
                latency = System.currentTimeMillis() - ts;
                log.info(latency + " ms. Received response from model for " + context.size() + " content elements.");
                return ret;
            } catch (Exception e) {
                recordLastApiError(e);
                if (e.toString().contains("429") || e.toString().contains("503") || e.toString().contains("500")) {
                    if (attempt == maxRetries - 1) {
                        log.error("Max retries reached. Aborting.", e);
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
                    log.error("Unknown error from Google's servers", e);
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalStateException("Exited retry loop unexpectedly.");
    }

    private List<Content> buildApiContext(List<ChatMessage> chatHistory) {
        return chatHistory.stream()
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public GeminiConfig getConfig() {
        return config;
    }

    public String getLastApiError() {
        return lastApiError;
    }

    public boolean isIsProcessing() {
        return isProcessing;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Client getGoogleGenAIClient() {
        return config.getApi().getClient();
    }

    public List<ChatMessage> getContext() {
        return contextManager.getContext();
    }

    public ContextManager getContextManager() {
        return contextManager;
    }
    
    public FunctionManager getFunctionManager() {
        return functionManager;
    }

    public void notifyJobCompletion(JobInfo jobInfo) {
        if (isProcessing) {
            log.info("Chat is busy. Job completion notification for {} will be queued.", jobInfo.getJobId());
            return;
        }

        log.info("Job {} completed. Adding result to context passively.", jobInfo.getJobId());
        Map<String, Object> responseMap = GSON.fromJson(GSON.toJson(jobInfo), Map.class);
        FunctionResponse fr = FunctionResponse.builder().name("async_job_result").response(responseMap).build();
        Part part = Part.fromFunctionResponse(fr.name().get(), (Map<String, Object>) fr.response().get());
        Content notificationContent = Content.builder().role("tool").parts(part).build();
        
        ChatMessage jobResultMessage = new ChatMessage(config.getApi().getModelId(), notificationContent, null, null, null);
        contextManager.add(jobResultMessage);
    }
}
