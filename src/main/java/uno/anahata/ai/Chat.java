/* Licensed under the Apache License, Version 2.0 */
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
import uno.anahata.ai.tools.UserFeedback;
import uno.anahata.ai.internal.GsonUtils;
import uno.anahata.ai.internal.PartUtils;
import uno.anahata.ai.status.ChatStatus;
import uno.anahata.ai.status.StatusListener;
import uno.anahata.ai.status.StatusManager;

/**
 * The central orchestrator for a Gemini AI chat session.
 * <p>
 * This class manages the conversation flow, including sending user input to the model,
 * handling model responses, executing local tools (function calling), and maintaining
 * the conversation context.
 * </p>
 * <p>
 * It integrates various components such as {@link ContextManager} for history,
 * {@link ToolManager} for function execution, and {@link StatusManager} for state tracking.
 * </p>
 */
@Slf4j
@Getter
public class Chat {

    private static final Gson GSON = GsonUtils.getGson();
    
    /**
     * A ThreadLocal reference to the Chat instance currently executing a tool.
     * This allows tools to access the chat context without explicit passing.
     */
    public static final ThreadLocal<Chat> callingInstance = new ThreadLocal<>();

    private final ToolManager toolManager;
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
     * The executor service used for asynchronous operations within this chat session.
     */
    @Getter
    private final ExecutorService executor;

    private long latency = -1;
    
    /**
     * Flag to enable or disable local function calling (tools).
     */
    @Setter
    private boolean functionsEnabled = true;

    /**
     * Flag to enable or disable server-side tools (e.g., Google Search).
     */
    @Setter
    private boolean serverToolsEnabled = false;
    
    private volatile boolean isProcessing = false;
    private volatile boolean shutdown = false;
    private Date startTime = new Date();
    
    private final AtomicLong messageCounter = new AtomicLong(0);

    private volatile Thread processingThread;

    /**
     * Resets the sequential message counter to a specific value.
     * Useful when restoring a session from persistent storage.
     *
     * @param value The new starting value for the message counter.
     */
    public void resetMessageCounter(long value) {
        log.info("Resetting message counter to {}", value);
        messageCounter.set(value);
    }
    
    /**
     * Constructs a new Chat instance with the specified configuration and prompter.
     *
     * @param config   The configuration for this chat session.
     * @param prompter The prompter used to handle user confirmation for tool calls.
     */
    public Chat(
            ChatConfig config,
            FunctionPrompter prompter) {
        this.config = config;
        this.executor = AnahataExecutors.newCachedThreadPoolExecutor(config.getSessionId());
        this.toolManager = new ToolManager(this, prompter);
        this.contextManager = new ContextManager(this);
        this.configManager = new ConfigManager(this);
        this.contentFactory = new ContentFactory(this);
        this.statusManager = new StatusManager(this);
        statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
    }

    /**
     * Shuts down the chat session and its associated executor service.
     */
    public void shutdown() {
        log.info("Shutting down Chat for session {}", config.getSessionId());
        this.shutdown = true;
        kill();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * Interrupts the current processing thread, effectively cancelling any ongoing API call or tool execution.
     */
    public void kill() {
        Thread t = processingThread;
        if (t != null) {
            log.info("Killing active processing thread: {}", t.getName());
            t.interrupt();
        }
    }

    /**
     * Adds a listener to be notified of changes in the conversation context.
     *
     * @param listener The listener to add.
     */
    public void addContextListener(ContextListener listener) {
        this.contextManager.addListener(listener);
    }

    /**
     * Adds a listener to be notified of changes in the chat's operational status.
     *
     * @param listener The listener to add.
     */
    public void addStatusListener(StatusListener listener) {
        statusManager.addListener(listener);
    }

    /**
     * Removes a previously added status listener.
     *
     * @param listener The listener to remove.
     */
    public void removeStatusListener(StatusListener listener) {
        statusManager.removeListener(listener);
    }

    /**
     * Gets the Chat instance associated with the current thread.
     *
     * @return The active Chat instance, or {@code null} if not in a tool execution context.
     */
    public static Chat getCallingInstance() {
        return callingInstance.get();
    }

    /**
     * Initializes the chat session, sending any configured startup instructions to the model.
     */
    public void init() {
        startTime = new Date();
        Content startupContent = config.getStartupContent();
        if (startupContent != null && startupContent.parts().isPresent() && !startupContent.parts().get().isEmpty()) {
            log.info("Sending one-time startup instructions to the model.");
            sendContent(startupContent);
        }
        statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
    }

    /**
     * Clears the conversation history and resets the status manager.
     */
    public void clear() {
        contextManager.clear();
        statusManager.reset();
    }

    /**
     * Sends a simple text message from the user to the model.
     *
     * @param message The text message to send.
     */
    public void sendText(String message) {
        sendContent(Content.builder().role("user").parts(Part.fromText(message)).build());
    }
    
    private ChatMessage buildChatMessage(Content content, GenerateContentResponseUsageMetadata usage, GroundingMetadata grounding) {
        return buildChatMessage(content, usage, grounding, false);
    }

    private ChatMessage buildChatMessage(Content content, GenerateContentResponseUsageMetadata usage, GroundingMetadata grounding, boolean toolFeedback) {
        return ChatMessage.builder()
            .sequentialId(messageCounter.incrementAndGet())
            .modelId(config.getApi().getModelId())
            .content(content)
            .usageMetadata(usage)
            .groundingMetadata(grounding)
            .toolFeedback(toolFeedback)
            .build();
    }

    /**
     * Sends a structured {@link Content} object to the model and initiates the processing loop.
     *
     * @param content The content to send.
     */
    public void sendContent(Content content) {
        if (isProcessing) {
            log.warn("A request is already in progress. Ignoring new request.");
            return;
        }
        isProcessing = true;
        processingThread = Thread.currentThread();
        statusManager.recordUserInputTime();
        statusManager.setStatus(ChatStatus.API_CALL_IN_PROGRESS);
        try {
            ChatMessage userMessage = buildChatMessage(content, null, null);
            contextManager.add(userMessage);

            processModelResponseLoop();
        } catch (Exception e) {
            if (Thread.interrupted() || e.getCause() instanceof InterruptedException) {
                log.info("Processing loop interrupted/killed.");
            } else {
                log.error("An unhandled exception occurred during the processing loop.", e);
            }
            // If an exception bubbles all the way up, it's a critical failure.
            // The MAX_RETRIES_REACHED status would have already been set inside the loop.
            // We don't want the finally block to override it.
        } finally {
            isProcessing = false;
            processingThread = null;
            // Only reset to IDLE if we are not in a terminal error state.
            if (statusManager.getCurrentStatus() != ChatStatus.MAX_RETRIES_REACHED) {
                statusManager.setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
            }
        }
    }

    private void processModelResponseLoop() {
        while (true) {
            if (shutdown || Thread.interrupted()) {
                log.info("Shutdown or interrupt flag detected. Breaking processing loop for session {}.", config.getSessionId());
                break;
            }

            List<Content> apiContext = buildApiContext(contextManager.getContext());
            GenerateContentResponse resp = sendToModelWithRetry(apiContext);

            if (resp == null) {
                break; // Interrupted
            }

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
            // Prepare the content for the application (e.g., adding missing IDs and enriching args).
            Content preparedContent = toolManager.prepareForApplication(originalContent);

            ChatMessage modelMessage = buildChatMessage(
                preparedContent, 
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
            ChatMessage feedbackMessage = buildChatMessage(feedbackContent, null, null, true);
            contextManager.add(feedbackMessage);
            return false;
        }

        statusManager.setStatus(ChatStatus.TOOL_EXECUTION_IN_PROGRESS);

        FunctionProcessingResult processingResult = toolManager.processFunctionCalls(modelMessageWithCalls);
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
            String feedbackText = processingResult.getFeedbackMessage();

            List<Part> userFeedbackParts = new ArrayList<>();
            userFeedbackParts.add(Part.fromText(feedbackText));
            userFeedbackParts.addAll(extraUserParts);

            Content feedbackContent = Content.builder().role("user").parts(userFeedbackParts).build();
            ChatMessage feedbackMessage = buildChatMessage(feedbackContent, null, null, true);
            feedbackMessage.setDependencies(userFeedbackDependencies); // Set dependencies after creation
            messagesToAdd.add(feedbackMessage);
        }

        for (ChatMessage message : messagesToAdd) {
            contextManager.add(message);
        }

        boolean anyKilled = processingResult.getOutcomes().stream()
                .anyMatch(outcome -> outcome.getStatus() == ToolCallStatus.KILLED);

        return !executedCalls.isEmpty() && !anyKilled;
    }

    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = config.getApiMaxRetries();
        long initialDelayMillis = config.getApiInitialDelayMillis();
        long maxDelayMillis = config.getApiMaxDelayMillis();
        long backoffAmount = 0;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (Thread.interrupted()) {
                return null;
            }
            Client client = getGoogleGenAIClient();
            try {
                statusManager.setStatus(ChatStatus.AUGMENTING_CONTEXT);
                GenerateContentConfig gcc = configManager.makeGenerateContentConfig();
                
                List<Content> finalContext = new ArrayList<>(context);

                List<Part> workspaceParts = contentFactory.produceParts(ContextPosition.AUGMENTED_WORKSPACE, true);
                
                if (Thread.interrupted()) {
                    return null;
                }
                
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

                statusManager.setStatus(ChatStatus.API_CALL_IN_PROGRESS);
                log.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + finalContext.size() + " content elements. Functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = client.models.generateContent(config.getApi().getModelId(), finalContext, gcc);
                latency = System.currentTimeMillis() - ts;
                log.info(latency + " ms. Received response from model for " + finalContext.size() + " content elements.");
                
                statusManager.clearApiErrors();
                statusManager.setLastUsage(ret.usageMetadata().orElse(null));
                
                return ret;
            } catch (Exception e) {
                if (Thread.interrupted() || e.getCause() instanceof InterruptedException) {
                    return null;
                }
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
                        return null;
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
                .map(GeminiAdapter::prepareForApi) // CRITICAL: Final Gate to purify POJOs
                .collect(Collectors.toCollection(ArrayList::new));

        if (context.isEmpty() || !context.get(0).role().orElse("").equals("user")) {
            context.add(0, Content.builder().role("user").parts(Part.fromText("")).build());
        }

        return context;
    }

    /**
     * Gets the underlying Google GenAI client used by this chat session.
     *
     * @return The Google GenAI client.
     */
    public Client getGoogleGenAIClient() {
        return config.getApi().getClient();
    }

    /**
     * Gets the current conversation history as a list of {@link ChatMessage} objects.
     *
     * @return The conversation history.
     */
    public List<ChatMessage> getContext() {
        return contextManager.getContext();
    }

    /**
     * Notifies the chat session that an asynchronous job has completed.
     * The result is added to the conversation context as a tool response.
     *
     * @param jobInfo Information about the completed job.
     */
    public void notifyJobCompletion(JobInfo jobInfo) {
        if (isProcessing) {
            log.info("Chat is busy. Job completion notification for {} will be queued.", jobInfo.getJobId());
            return;
        }

        log.info("Job {} completed. Adding result to context passively.", jobInfo.getJobId());
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("output", jobInfo);
        FunctionResponse fr = FunctionResponse.builder().name("async_job_result").response(responseMap).build();
        Part part = Part.builder().functionResponse(fr).build();
        Content notificationContent = Content.builder().role("tool").parts(part).build();

        ChatMessage jobResultMessage = buildChatMessage(notificationContent, null, null);
        contextManager.add(jobResultMessage);
    }
    
    /**
     * Gets a short, human-readable identifier for the chat session.
     *
     * @return A short session ID.
     */
    public String getShortId() {
        String sessionId = config.getSessionId();
        return sessionId.substring(sessionId.length() - 7);
    }

    /**
     * Gets a human-readable display name for the chat session.
     * It returns the nickname if set, otherwise the shortened session ID.
     *
     * @return The display name.
     */
    public String getDisplayName() {
        return StringUtils.isNotBlank(nickname) ? nickname : getShortId();
    }
    
    /**
     * Calculates the current context window usage as a percentage of the token threshold.
     *
     * @return The usage percentage (0.0 to 1.0).
     */
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

    /**
     * Gets a formatted string representing the current context window usage percentage.
     *
     * @return A formatted usage string (e.g., "45.2%").
     */
    public String getContextWindowUsageFormatted() {
        float usage = getContextWindowUsage();
        if (usage > 0.0f) {
            return String.format("%.1f%%", usage * 100);
        }
        return "N/A";
    }
}
