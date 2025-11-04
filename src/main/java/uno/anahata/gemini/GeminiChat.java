package uno.anahata.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.context.ContextManager;
import uno.anahata.gemini.context.ContextListener;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.functions.FunctionManager.ExecutedToolCall;
import uno.anahata.gemini.functions.FunctionManager.FunctionProcessingResult;
import uno.anahata.gemini.functions.FunctionPrompter;
import uno.anahata.gemini.functions.JobInfo;
import uno.anahata.gemini.functions.MultiPartResponse;
import uno.anahata.gemini.internal.GsonUtils;
import uno.anahata.gemini.internal.PartUtils;
import uno.anahata.gemini.systeminstructions.SystemInstructionProvider;

@Slf4j
@Getter
public class GeminiChat {

    private static final Gson GSON = GsonUtils.getGson();
    public static final ThreadLocal<GeminiChat> callingInstance = new ThreadLocal<>();

    private final FunctionManager functionManager;
    private final GeminiConfig config;
    private final ContextManager contextManager;
    private long latency = -1;
    @Setter
    private boolean functionsEnabled = true;
    @Getter
    @Setter
    private volatile boolean liveWorkspaceEnabled = true;
    private String lastApiError = "";
    private volatile boolean isProcessing = false;
    private Date startTime = new Date();
    private List<SystemInstructionProvider> systemInstructionProviders;

    public GeminiChat(
            GeminiConfig config,
            FunctionPrompter prompter) {
        this.config = config;
        this.functionManager = new FunctionManager(this, config, prompter);
        this.contextManager = new ContextManager(this);
        // Initialize providers here to take a copy from config
        this.systemInstructionProviders = new ArrayList<>(config.getSystemInstructionProviders());
    }

    public void addContextListener(ContextListener listener) {
        this.contextManager.addListener(listener);
    }

    public static GeminiChat getCallingInstance() {
        return callingInstance.get();
    }

    private Content buildSystemInstructions() {
        List<Part> parts = new ArrayList<>();

        for (SystemInstructionProvider provider : getSystemInstructionProviders()) {
            if (provider.isEnabled()) {
                try {

                    List<Part> generated = provider.getInstructionParts(this);
                    parts.add(Part.fromText("Instruction Provider: " + provider.getDisplayName() + " (id: " + provider.getId() + "): (" + generated.size() + " parts)"));
                    parts.addAll(generated);
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
                ChatMessage userMessage = ChatMessage.builder()
                        .modelId(config.getApi().getModelId())
                        .content(content)
                        .build();
                contextManager.add(userMessage);
            }

            while (true) {
                List<Content> apiContext = buildApiContext(contextManager.getContext());
                GenerateContentResponse resp = sendToModelWithRetry(apiContext);

                if (resp.candidates() == null || !resp.candidates().isPresent() || resp.candidates().get().isEmpty()) {
                    log.warn("Received response with no candidates. Possibly due to safety filters. Breaking loop.");
                    Content emptyContent = Content.builder().role("model").parts(Part.fromText("[No response from model]")).build();
                    ChatMessage emptyModelMessage = ChatMessage.builder()
                            .modelId(config.getApi().getModelId())
                            .content(emptyContent)
                            .usageMetadata(resp.usageMetadata().orElse(null))
                            .build();
                    contextManager.add(emptyModelMessage);
                    break;
                }

                Candidate cand = resp.candidates().get().get(0);
                if (cand.content() == null || !cand.content().isPresent()) {
                    log.warn("Received candidate with no content. Breaking loop.");
                    break;
                }

                Content modelResponseContent = cand.content().get();
                ChatMessage modelMessage = ChatMessage.builder()
                        .modelId(config.getApi().getModelId())
                        .content(modelResponseContent)
                        .usageMetadata(resp.usageMetadata().orElse(null))
                        .groundingMetadata(cand.groundingMetadata().orElse(null))
                        .build();
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
        List<FunctionCall> allProposedCalls = modelMessageWithCalls.getContent().parts().orElse(Collections.emptyList()).stream()
                .filter(part -> part.functionCall().isPresent())
                .map(part -> part.functionCall().get())
                .collect(Collectors.toList());

        if (allProposedCalls.isEmpty()) {
            return false;
        }

        if (!functionsEnabled) {
            String attemptedCalls = allProposedCalls.stream()
                    .map(fc -> fc.name().orElse("unnamed function"))
                    .collect(Collectors.joining(", "));
            String feedbackText = "User Feedback: The model attempted to call the following tool(s) while local functions were disabled: [" + attemptedCalls + "]. The calls were ignored.";
            sendContent(Content.builder().role("user").parts(Part.fromText(feedbackText)).build());
            return false;
        }

        FunctionProcessingResult processingResult = functionManager.processFunctionCalls(modelMessageWithCalls);
        List<ExecutedToolCall> executedCalls = processingResult.getExecutedCalls();

        Map<Part, List<Part>> modelDependencies = new HashMap<>();
        Map<Part, List<Part>> toolDependencies = new HashMap<>();
        Map<Part, List<Part>> userFeedbackDependencies = new HashMap<>();

        List<Part> extraUserParts = new ArrayList<>();
        List<ChatMessage> messagesToAdd = new ArrayList<>();
        ChatMessage toolMessage = null;

        if (!executedCalls.isEmpty()) {
            List<Part> responseParts = new ArrayList<>();
            for (ExecutedToolCall etc : executedCalls) {
                Part sourceCallPart = etc.getSourceCallPart();
                FunctionResponse fr = etc.getResponse();
                Map<String, Object> responseMap = (Map<String, Object>) fr.response().get();
                Part responsePart = Part.fromFunctionResponse(fr.name().get(), responseMap);
                responseParts.add(responsePart);

                // 1. Model Dependency (Forward Link: Call -> Response)
                modelDependencies.computeIfAbsent(sourceCallPart, k -> new ArrayList<>()).add(responsePart);

                // 2. Tool Dependency (Reverse Link: Response -> Call)
                toolDependencies.computeIfAbsent(responsePart, k -> new ArrayList<>()).add(sourceCallPart);

                // 3. Handle MultiPartResponse (Links between tool and user messages)
                if (etc.getRawResult() instanceof MultiPartResponse) {
                    MultiPartResponse mpr = (MultiPartResponse) etc.getRawResult();
                    for (String filePath : mpr.getFilePaths()) {
                        try {
                            Part imagePart = PartUtils.toPart(new File(filePath));
                            extraUserParts.add(imagePart);

                            // Tool Dependency (Link to Image): Response -> ImagePart
                            toolDependencies.computeIfAbsent(responsePart, k -> new ArrayList<>()).add(imagePart);

                            // User Feedback Dependency (Link from Image): ImagePart -> Response
                            userFeedbackDependencies.computeIfAbsent(imagePart, k -> new ArrayList<>()).add(responsePart);

                        } catch (Exception e) {
                            log.error("Failed to create Part from file path: {}", filePath, e);
                        }
                    }
                }
            }

            // Update the model message in place (via mutability and merging)
            for (Map.Entry<Part, List<Part>> entry : modelDependencies.entrySet()) {
                modelMessageWithCalls.addDependencies(entry.getKey(), entry.getValue());
            }
            // contextManager.notifyHistoryChange() is intentionally removed here as it will be triggered by contextManager.add() below.

            // Prepare the tool message to be added later
            Content functionResponseContent = Content.builder().role("tool").parts(responseParts).build();
            toolMessage = ChatMessage.builder()
                    .modelId(config.getApi().getModelId())
                    .content(functionResponseContent)
                    .dependencies(toolDependencies)
                    .build();
            messagesToAdd.add(toolMessage);
        }

        boolean wasAutopilot = "Autopilot".equals(processingResult.getUserComment());
        boolean hasDeniedCalls = !processingResult.getDeniedCallParts().isEmpty();
        boolean hasNonAutopilotComment = processingResult.getUserComment() != null && !processingResult.getUserComment().trim().isEmpty() && !wasAutopilot;

        if (wasAutopilot || hasDeniedCalls || hasNonAutopilotComment || !extraUserParts.isEmpty()) {
            List<Part> userFeedbackParts = new ArrayList<>();
            StringBuilder feedbackText = new StringBuilder("User has provided feedback on the proposed tool calls:\n");

            if (wasAutopilot) {
                feedbackText.append("- Autopilot: ").append(executedCalls.size()).append(" tool call(s) were pre-approved and executed automatically.\n");
            }

            if (hasDeniedCalls) {
                String denied = processingResult.getDeniedCallParts().stream()
                        .map(part -> part.functionCall().get().name().orElse("unnamed"))
                        .collect(Collectors.joining(", "));
                feedbackText.append("- The following calls were denied: [").append(denied).append("]\n");
            }

            if (hasNonAutopilotComment) {
                feedbackText.append("- User's comment: '").append(processingResult.getUserComment()).append("'\n");
            }

            userFeedbackParts.add(Part.fromText(feedbackText.toString()));
            userFeedbackParts.addAll(extraUserParts);

            Content feedbackContent = Content.builder().role("user").parts(userFeedbackParts).build();
            ChatMessage feedbackMessage = ChatMessage.builder()
                    .modelId(config.getApi().getModelId())
                    .content(feedbackContent)
                    .dependencies(userFeedbackDependencies)
                    .build();
            messagesToAdd.add(feedbackMessage);
        }

        // Add all new messages at the end
        for (ChatMessage message : messagesToAdd) {
            contextManager.add(message);
        }

        return !executedCalls.isEmpty() || hasDeniedCalls;
    }

    private void recordLastApiError(Exception e, int attempts) {
        this.lastApiError += "\nAttempt: " + attempts
                + "\nTime: " + new Date()
                + "\nError: " + ExceptionUtils.getStackTrace(e)
                + "\n-----------------------------------";
    }

    private GenerateContentResponse sendToModelWithRetry(List<Content> context) {
        int maxRetries = config.getApiMaxRetries();
        long initialDelayMillis = config.getApiInitialDelayMillis();
        long maxDelayMillis = config.getApiMaxDelayMillis();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                GenerateContentConfig gcc = makeGenerateContentConfig();

                List<Content> finalContext = context;
                if (liveWorkspaceEnabled) {
                    log.info("Live Workspace is enabled. Augmenting context.");
                    List<Part> workspaceParts = config.getLiveWorkspaceParts();
                    if (!workspaceParts.isEmpty()) {
                        List<Content> augmentedContext = new ArrayList<>(context);
                        augmentedContext.add(Content.builder().role("user").parts(workspaceParts).build());
                        finalContext = augmentedContext;
                    }
                }

                log.info("Sending to model (attempt " + (attempt + 1) + "/" + maxRetries + "). " + finalContext.size() + " content elements. Functions enabled: " + functionsEnabled);
                long ts = System.currentTimeMillis();
                GenerateContentResponse ret = getGoogleGenAIClient().models.generateContent(config.getApi().getModelId(), finalContext, gcc);
                lastApiError = "";
                latency = System.currentTimeMillis() - ts;
                log.info(latency + " ms. Received response from model for " + finalContext.size() + " content elements.");
                return ret;
            } catch (Exception e) {
                recordLastApiError(e, attempt);
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

        ChatMessage jobResultMessage = ChatMessage.builder()
                .modelId(config.getApi().getModelId())
                .content(notificationContent)
                .build();
        contextManager.add(jobResultMessage);
    }
}
