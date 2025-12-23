package uno.anahata.ai;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.ai.gemini.GeminiAdapter;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.config.ConfigManager;
import uno.anahata.ai.context.ContextListener;
import uno.anahata.ai.context.ContextManager;
import uno.anahata.ai.context.provider.ContentFactory;
import uno.anahata.ai.context.provider.ContextPosition;
import uno.anahata.ai.tools.ExecutedToolCall;
import uno.anahata.ai.tools.FunctionProcessingResult;
import uno.anahata.ai.tools.ToolManager;
import uno.anahata.ai.tools.FunctionPrompter;
import uno.anahata.ai.tools.JobInfo;
import uno.anahata.ai.tools.MultiPartResponse;
import uno.anahata.ai.tools.ToolCallOutcome;
import uno.anahata.ai.tools.ToolCallStatus;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusListener;
import uno.anahata.ai.status.StatusManager;

@Slf4j
@Getter
public class Chat {

    private static final Gson GSON = GsonUtils.getGson();
    public static final ThreadLocal<Chat> callingInstance = new ThreadLocal<>();

    private final ToolManager functionManager;
    private final ChatConfig config;
    private final ContextManager contextManager;
    @Getter
    private final ConfigManager configManager;
    @Getter
    private final ContentFactory contentFactory;
    @Getter
    private final StatusManager statusManager;
    
    /**
     * A user-defined nickname for the chat session for easier identification.
     */
    @Setter
    private String nickname;
    
    /**
     * Thread pool for the chat.
     */
    @Getter
    private final ExecutorService executor;

    private long latency = -1;
    @Setter
    private boolean functionsEnabled = true;
    private volatile boolean isProcessing = false;
    private volatile boolean shutdown = false;
    private Date startTime = new Date();
    
    private final AtomicLong messageCounter = new AtomicLong(0);

    public void resetMessageCounter(long value) {
        log.info("Resetting message counter to {}", value);
        messageCounter.set(value);
    }
    
    public Chat(
            ChatConfig config,
            FunctionPrompter prompter) {
        this.config = config;
        this.executor = AnahataExecutors.newCachedThreadPoolExecutor(config.getSessionId());
        this.functionManager = new ToolManager(this, prompter);
        this.contextManager = new ContextManager(this);
        this.configManager = new ConfigManager(this);
        this.contentFactory = new ContentFactory(this);
        this.statusManager = new StatusManager(this);
        statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
    }

    public void shutdown() {
        log.info("Shutting down Chat for session {}", config.getSessionId());
        this.shutdown = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public void addContextListener(ContextListener listener) {
        this.contextManager.addListener(listener);
    }

    public void addStatusListener(StatusListener listener) {
        statusManager.addListener(listener);
    }

    public void removeStatusListener(StatusListener listener) {
        statusManager.removeListener(listener);
    }

    public static Chat getCallingInstance() {
        return callingInstance.get();
    }

    public void init() {
        startTime = new Date();
        Content startupContent = config.getStartupContent();
        if (startupContent != null && startupContent.parts().isPresent() && !startupContent.parts().get().isEmpty()) {
            log.info("Sending one-time startup instructions to the model.");
            sendContent(startupContent);
        }
        statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
    }

    public void clear() {
        contextManager.clear();
        statusManager.reset();
    }

    public void sendText(String message) {
        sendContent(Content.builder().role("user").parts(Part.fromText(message)).build());
    }
    
    private ChatMessage buildChatMessage(Content content, GenerateContentResponseUsageMetadata usage, GroundingMetadata grounding) {
        return ChatMessage.builder()
            .sequentialId(messageCounter.incrementAndGet())
            .modelId(config.getApi().getModelId())
            .content(content)
            .usageMetadata(usage)
            .groundingMetadata(grounding)
            .build();
    }

    public void sendContent(Content content) {
        if (isProcessing) {
            log.warn("A request is already in progress. Ignoring new request.");
            return;
        }
        isProcessing = true;
        statusManager.recordUserInputTime();
        statusManager.setStatus(ChatStatus.API_CALL_IN_PROGRESS);
        try {
            ChatMessage userMessage = buildChatMessage(content, null, null);
            contextManager.add(userMessage);

            processModelResponseLoop();
        } catch (Exception e) {
            log.error("An unhandled exception occurred during the processing loop.", e);
            // If an exception bubbles all the way up, it's a critical failure.
            // The MAX_RETRIES_REACHED status would have already been set inside the loop.
            // We don't want the finally block to override it.
        } finally {
            isProcessing = false;
            // Only reset to IDLE if we are not in a terminal error state.
            if (statusManager.getCurrentStatus() != ChatStatus.MAX_RETRIES_REACHED) {
                statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
            }
        }
    }

    private void processModelResponseLoop() {
        while (true) {
            if (shutdown) {
                log.info("Shutdown flag detected. Breaking processing loop for session {}.", config.getSessionId());
                break;
            }

            List<Content> apiContext = buildApiContext(contextManager.getContext());
            GenerateContentResponse resp = sendToModelWithRetry(apiContext);

            if (resp.candidates() == null || !resp.candidates().isPresent() || resp.candidates().get().isEmpty()) {
                log.warn("Received response with no candidates. Possibly due to safety filters. Breaking loop.");
                Content emptyContent = Content.builder().role("model").parts(Part.fromText("[No response from model]")).build();
                ChatMessage emptyModelMessage = buildChatMessage(emptyContent, resp.usageMetadata().orElse(null), null);
                contextManager.add(emptyModelMessage);
                break;
            }

            Candidate cand = resp.candidates().get().get(0);
            if (cand.content() == null || !cand.content().isPresent()) {
                log.warn("Received candidate with no content. Breaking loop.");
                break;
            }

            Content originalContent = cand.content().get();
            // Sanitize the content to ensure all FunctionCalls have an ID before adding to context.
            Content sanitizedContent = GeminiAdapter.sanitize(originalContent, functionManager.getIdCounter());

            ChatMessage modelMessage = buildChatMessage(
                sanitizedContent, 
                resp.usageMetadata().orElse(null), 
                cand.groundingMetadata().orElse(null)
            );
            contextManager.add(modelMessage);

            if (!processAndReloopForFunctionCalls(modelMessage)) {
                break;
            }
        }
    }

    private boolean processAndReloopForFunctionCalls(ChatMessage modelMessageWithCalls) {
        List<FunctionCall> allProposedCalls = modelMessageWithCalls.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionCall().isPresent())
                .map(part -> part.functionCall().get())
                .collect(Collectors.toList());

        if (allProposedCalls.isEmpty()) {
            return false;
        }

        if (!functionsEnabled) {
            String feedback = allProposedCalls.stream()
                .map(fc -> String.format("[%s id=N/A %s]", fc.name().orElse("unknown"), ToolCallStatus.DISABLED))
                .collect(Collectors.joining(" "));
            String feedbackText = "User Feedback: " + feedback;
            
            Content feedbackContent = Content.builder().role("user").parts(Part.fromText(feedbackText)).build();
            ChatMessage feedbackMessage = buildChatMessage(feedbackContent, null, null);
            contextManager.add(feedbackMessage);
            return false;
        }

        statusManager.setStatus(ChatStatus.TOOL_EXECUTION_IN_PROGRESS);

        FunctionProcessingResult processingResult = functionManager.processFunctionCalls(modelMessageWithCalls);
        List<ExecutedToolCall> executedCalls = processingResult.getExecutedCalls();

        Map<Part, List<Part>> modelDependencies = new HashMap<>();
        Map<Part, List<Part>> toolDependencies = new HashMap<>();
        Map<Part, List<Part>> userFeedbackDependencies = new HashMap<>();

        List<Part> extraUserParts = new ArrayList<>();
        List<ChatMessage> messagesToAdd = new ArrayList<>();

        if (!executedCalls.isEmpty()) {
            List<Part> responseParts = new ArrayList<>();
            for (ExecutedToolCall etc : executedCalls) {
                Part sourceCallPart = etc.getSourceCallPart();
                FunctionResponse fr = etc.getResponse();
                
                // CRITICAL FIX: Use the Part.builder() to ensure the entire FunctionResponse object,
                // including its ID, is preserved. Do not use the lossy Part.fromFunctionResponse() factory.
                Part responsePart = Part.builder().functionResponse(fr).build();
                responseParts.add(responsePart);

                modelDependencies.computeIfAbsent(sourceCallPart, k -> new ArrayList<>()).add(responsePart);
                toolDependencies.computeIfAbsent(responsePart, k -> new ArrayList<>()).add(sourceCallPart);

                if (etc.getRawResult() instanceof MultiPartResponse) {
                    MultiPartResponse mpr = (MultiPartResponse) etc.getRawResult();
                    for (String filePath : mpr.getFilePaths()) {
                        try {
                            Part imagePart = PartUtils.toPart(new File(filePath));
                            extraUserParts.add(imagePart);
                            toolDependencies.computeIfAbsent(responsePart, k -> new ArrayList<>()).add(imagePart);
                            userFeedbackDependencies.computeIfAbsent(imagePart, k -> new ArrayList<>()).add(responsePart);
                        } catch (Exception e) {
                            log.error("Failed to create Part from file path: {}", filePath, e);
                        }
                    }
                }
            }

            for (Map.Entry<Part, List<Part>> entry : modelDependencies.entrySet()) {
                modelMessageWithCalls.addDependencies(entry.getKey(), entry.getValue());
            }

            Content functionResponseContent = Content.builder().role("tool").parts(responseParts).build();
            ChatMessage toolMessage = buildChatMessage(functionResponseContent, null, null);
            toolMessage.setDependencies(toolDependencies); // Set dependencies after creation
            messagesToAdd.add(toolMessage);
        }

        // Always create a feedback message if there were any tool calls proposed.
        if (!processingResult.getOutcomes().isEmpty()) {
            String toolFeedback = processingResult.getOutcomes().stream()
                .map(outcome -> {
                    String toolName = outcome.getIdentifiedCall().getCall().name().orElse("unknown");
                    String id = outcome.getIdentifiedCall().getId();
                    String status = outcome.getStatus().name();
                    if (outcome.getStatus() == ToolCallStatus.YES || outcome.getStatus() == ToolCallStatus.ALWAYS) {
                        status = "";
                    } 
                    
                    return String.format("[%s id=%s %s]", toolName, id, status);
                })
                .collect(Collectors.joining(" "));
            
            StringBuilder feedbackText = new StringBuilder("Tool Feedback: ").append(toolFeedback);
            
            if (StringUtils.isNotBlank(processingResult.getUserComment())) {
                feedbackText.append("\nUser Comment: '").append(processingResult.getUserComment()).append("'");
            }

            List<Part> userFeedbackParts = new ArrayList<>();
            userFeedbackParts.add(Part.fromText(feedbackText.toString()));
            userFeedbackParts.addAll(extraUserParts);

            Content feedbackContent = Content.builder().role("user").parts(userFeedbackParts).build();
            ChatMessage feedbackMessage = buildChatMessage(feedbackContent, null, null);
            feedbackMessage.setDependencies(userFeedbackDependencies); // Set dependencies after creation
            messagesToAdd.add(feedbackMessage);
        }

        for (ChatMessage message : messagesToAdd) {
            contextManager.add(message);
        }

        return !executedCalls.isEmpty();
    }

    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = config.getApiMaxRetries();
        long initialDelayMillis = config.getApiInitialDelayMillis();
        long maxDelayMillis = config.getApiMaxDelayMillis();
        long backoffAmount = 0;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Client client = getGoogleGenAIClient();
            try {
                statusManager.setStatus(ChatStatus.API_CALL_IN_PROGRESS);
                GenerateContentConfig gcc = configManager.makeGenerateContentConfig();
                
                List<Content> finalContext = new ArrayList<>(context);

                List<Part> workspaceParts = contentFactory.produceParts(ContextPosition.AUGMENTED_WORKSPACE, true);
                if (!workspaceParts.isEmpty()) {
                    log.info("Augmenting context with {} parts from workspace providers.", workspaceParts.size());
                    List<Part> augmentedMessageParts = new ArrayList<>();
                    augmentedMessageParts.add(Part.fromText("--- Augmented Workspace Context ---\n"
                            + "The following is high-salience, just-in-time context provided by the host environment for this turn. "
                            + "It is not part of the permanent conversation history.\n"));
                    augmentedMessageParts.addAll(workspaceParts);

                    Content ragContent = Content.builder().role("user").parts(augmentedMessageParts).build();

                    // Check if the last message is a user message. It should be, but we check to be safe.
                    if (!finalContext.isEmpty() && finalContext.get(finalContext.size() - 1).role().orElse("").equals("user")) {
                        // The last message is the user's current input. We want to insert the RAG message before it.
                        Content userMessage = finalContext.remove(finalContext.size() - 1);
                        finalContext.add(ragContent);
                        finalContext.add(userMessage);
                    } else {
                        // If the last message is not a user message (e.g., model or tool), or the context is empty, append.
                        finalContext.add(ragContent);
                    }
                }

                log.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + finalContext.size() + " content elements. Functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = client.models.generateContent(config.getApi().getModelId(), finalContext, gcc);
                latency = System.currentTimeMillis() - ts;
                log.info(latency + " ms. Received response from model for " + finalContext.size() + " content elements.");
                
                statusManager.clearApiErrors();
                statusManager.setLastUsage(ret.usageMetadata().orElse(null));
                
                return ret;
            } catch (Exception e) {
                log.warn("Api Error on attempt {}: {}", attempt, e.toString());
                String apiKey = client.apiKey();
                String apiKeyLast5 = StringUtils.right(apiKey, 5);
                statusManager.recordApiError(config.getApi().getModelId(), apiKeyLast5, attempt, backoffAmount, e);

                if (e.toString().contains("429") || e.toString().contains("503") || e.toString().contains("500")) {
                    if (attempt == maxRetries - 1) {
                        log.error("Max retries reached. Aborting.", e);
                        statusManager.setStatus(ChatStatus.MAX_RETRIES_REACHED);
                        throw new RuntimeException("Failed to get response from model after " + maxRetries + " attempts.", e);
                    }
                    long delayMillis = (long) (initialDelayMillis * Math.pow(2, attempt));
                    delayMillis = Math.min(delayMillis, maxDelayMillis);
                    long jitter = (long) (Math.random() * 500);
                    backoffAmount = delayMillis + jitter;
                    try {
                        Thread.sleep(backoffAmount);
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
        List<Content> context = chatHistory.stream()
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        if (context.isEmpty() || !context.get(0).role().orElse("").equals("user")) {
            context.add(0, Content.builder().role("user").parts(Part.fromText("")).build());
        }

        return context;
    }

    public Client getGoogleGenAIClient() {
        return config.getApi().getClient();
    }

    public List<ChatMessage> getContext() {
        return contextManager.getContext();
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

        ChatMessage jobResultMessage = buildChatMessage(notificationContent, null, null);
        contextManager.add(jobResultMessage);
    }
    
    public String getShortId() {
        String sessionId = config.getSessionId();
        return sessionId.substring(sessionId.length() - 7);
    }
    
    public float getContextWindowUsage() {
        try {
            int tokenCount = contextManager.getTotalTokenCount();
            int tokenThreshold = contextManager.getTokenThreshold();
            if (tokenThreshold > 0) {
                return (float) tokenCount / tokenThreshold;
            }
        } catch (Exception e) {
            log.warn("Could not calculate context window usage", e);
        }
        return 0.0f;
    }

    public String getContextWindowUsageFormatted() {
        float usage = getContextWindowUsage();
        if (usage > 0.0f) {
            return String.format("%.1f%%", usage * 100);
        }
        return "N/A";
    }
}