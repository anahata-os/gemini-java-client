/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import uno.anahata.ai.Chat;
import uno.anahata.ai.config.ChatConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.ai.gemini.GeminiAdapter;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.Executors;
import uno.anahata.ai.tools.JobInfo.JobStatus;
import uno.anahata.ai.tools.FunctionPrompter.PromptResult;
import uno.anahata.ai.internal.JacksonUtils;


/**
 * Manages the registration, identification, and execution of local tools (functions).
 * <p>
 * This class uses reflection to scan provided classes for methods annotated with
 * {@link AIToolMethod}. It converts these methods into {@link FunctionDeclaration}s
 * for the Gemini API and handles the invocation of these methods when the model
 * requests a tool call.
 * </p>
 * <p>
 * It also manages tool call IDs, user confirmation workflows via {@link FunctionPrompter},
 * and asynchronous tool execution.
 * </p>
 */
@Slf4j
public class ToolManager {

    @Getter
    private final Chat chat;
    private final ChatConfig config;
    @Getter
    private final FunctionPrompter prompter;
    private final Map<String, Method> functionCallMethods = new HashMap<>();
    private final Tool coreTools;
    private final ToolConfig toolConfig;
    private final FailureTracker failureTracker;
    private final List<FunctionInfo> functionInfos = new ArrayList<>();
    
    /**
     * A sequential counter used to assign unique IDs to tool calls within a turn.
     */
    @Getter
    private final AtomicInteger idCounter = new AtomicInteger(1);

    private final Set<String> alwaysApproveFunctions = new HashSet<>();
    private final Set<String> neverApproveFunctions = new HashSet<>();
    
    /**
     * Resets the tool call ID counter to a specific value.
     *
     * @param value The new starting value.
     */
    public void resetIdCounter(int value) {
        log.info("Resetting tool call ID counter to {}", value);
        idCounter.set(value);
    }
    
    /**
     * Constructs a new ToolManager for the given Chat instance and prompter.
     *
     * @param chat     The Chat instance.
     * @param prompter The prompter for user confirmation.
     */
    public ToolManager(Chat chat, FunctionPrompter prompter) {
        this.chat = chat;
        this.config = chat.getConfig();
        this.prompter = prompter;
        this.failureTracker = new FailureTracker(chat);
        List<Class<?>> allClasses = new ArrayList<>();
        
        if (prompter != null && config.getToolClasses() != null) {
            allClasses.addAll(config.getToolClasses());
        }
        log.info("ToolManager scanning classes for @AIToolMethod: " + allClasses);
        this.coreTools = makeFunctionsTool(allClasses.toArray(new Class<?>[0]));
        log.info("ToolManager created. Total Function Declarations: " + coreTools.functionDeclarations().get().size());

        this.toolConfig = makeToolConfigForFunctionCalling();
    }
    
    private static ToolConfig makeToolConfigForFunctionCalling() {
        return ToolConfig.builder()
                .functionCallingConfig(FunctionCallingConfig.builder().mode(FunctionCallingConfigMode.Known.AUTO).build())
                .build();
    }

    private Tool makeFunctionsTool(Class<?>... classes)  {
        List<FunctionDeclaration> fds = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(AIToolMethod.class)) {
                    try {
                        AIToolMethod annotation = m.getAnnotation(AIToolMethod.class);
                        StringBuilder descriptionBuilder = new StringBuilder(annotation.value());
                        
                        Map<String, Schema> properties = new LinkedHashMap<>();
                        List<String> requiredParams = new ArrayList<>();

                        for (Parameter p : m.getParameters()) {
                            String paramName = p.getName();
                            Schema paramSchema = GeminiAdapter.getGeminiSchema(p.getParameterizedType());

                            AIToolParam paramAnnotation = p.getAnnotation(AIToolParam.class);
                            boolean isRequired = true;
                            if (paramAnnotation != null) {
                                isRequired = paramAnnotation.required();
                                if (StringUtils.isNotBlank(paramAnnotation.value())) {
                                    String paramDescription = paramAnnotation.value();
                                    String typeDescription = paramSchema.description().orElse("");

                                    String combinedDescription = paramDescription;
                                    if (StringUtils.isNotBlank(typeDescription)) {
                                        combinedDescription += "\n\n(Details: " + typeDescription + ")";
                                    }

                                    paramSchema = paramSchema.toBuilder().description(combinedDescription).build();
                                }
                            }

                            properties.put(paramName, paramSchema);
                            if (isRequired) {
                                requiredParams.add(paramName);
                            }
                        }
                        
                        // Append the full FQN method signature to the description
                        String signature = Modifier.toString(m.getModifiers())
                                + " " + m.getGenericReturnType().getTypeName()
                                + " " + m.getName() + "("
                                + Stream.of(m.getParameters())
                                        .map(p -> p.getParameterizedType().getTypeName() + " " + p.getName())
                                        .collect(java.util.stream.Collectors.joining(", "))
                                + ")";

                        if (m.getExceptionTypes().length > 0) {
                            signature += " throws " + Stream.of(m.getExceptionTypes())
                                    .map(Class::getCanonicalName)
                                    .collect(java.util.stream.Collectors.joining(", "));
                        }
                        descriptionBuilder.append("\n\njava method signature: ").append(signature);
                        descriptionBuilder.append("\ncontext behavior: ").append(annotation.behavior());

                        FunctionDeclaration.Builder fdb = FunctionDeclaration.builder()
                                .name(c.getSimpleName() + "." + m.getName())
                                .description(descriptionBuilder.toString());
                        
                        if (!properties.isEmpty()) {
                            Schema paramsSchema = Schema.builder()
                                    .type(com.google.genai.types.Type.Known.OBJECT)
                                    .properties(properties)
                                    .required(requiredParams)
                                    .build();
                            fdb.parameters(paramsSchema);
                        }
                        
                        Schema responseSchema = GeminiAdapter.getGeminiSchema(m.getGenericReturnType());
                        if (responseSchema != null) {
                            fdb.response(responseSchema);
                        }

                        FunctionDeclaration fd = fdb.build();
                        functionCallMethods.put(fd.name().get(), m);
                        fds.add(fd);
                        functionInfos.add(new FunctionInfo(fd, m));
                        if (!annotation.requiresApproval()) {
                            alwaysApproveFunctions.add(fd.name().get());
                        }
                    } catch (Exception e) {
                        log.error("Could not register tool: " + m, e);
                    }
                }
            }
        }
        return Tool.builder().functionDeclarations(fds).build();
    }

    /**
     * Prepares a Content object received from the model for use within the application.
     * This involves ensuring every FunctionCall part has a stable ID and converting
     * its arguments into rich POJOs.
     *
     * @param originalContent The raw content received from the model.
     * @return The original content if no changes were needed, or a new, enriched Content object.
     */
    public Content prepareForApplication(Content originalContent) {
        if (originalContent == null || !originalContent.parts().isPresent()) {
            return originalContent;
        }

        List<Part> originalParts = originalContent.parts().get();
        List<Part> newParts = new ArrayList<>(originalParts.size());
        boolean wasModified = false;

        for (Part originalPart : originalParts) {
            if (originalPart.functionCall().isPresent()) {
                FunctionCall originalFc = originalPart.functionCall().get();
                FunctionCall.Builder builder = originalFc.toBuilder();
                boolean fcModified = false;

                if (originalFc.id().isEmpty()) {
                    String newId = String.valueOf(idCounter.getAndIncrement());
                    builder.id(newId);
                    fcModified = true;
                    log.info("Assigned ID '{}' to FunctionCall '{}'", newId, originalFc.name().get());
                }

                if (originalFc.args().isPresent()) {
                    Method method = getToolMethod(originalFc.name().get());
                    if (method != null) {
                        Map<String, Object> rawArgs = originalFc.args().get();
                        Map<String, Object> pojoArgs = new HashMap<>();
                        for (Parameter p : method.getParameters()) {
                            String paramName = p.getName();
                            if (rawArgs.containsKey(paramName)) {
                                pojoArgs.put(paramName, JacksonUtils.toPojo(rawArgs.get(paramName), p.getParameterizedType()));
                            }
                        }
                        builder.args(pojoArgs);
                        fcModified = true;
                        log.info("Enriched arguments for FunctionCall '{}' into POJOs.", originalFc.name().get());
                    }
                }

                if (fcModified) {
                    newParts.add(originalPart.toBuilder().functionCall(builder.build()).build());
                    wasModified = true;
                } else {
                    newParts.add(originalPart);
                }
            } else {
                newParts.add(originalPart);
            }
        }

        return wasModified ? originalContent.toBuilder().parts(newParts).build() : originalContent;
    }

    /**
     * Processes a list of function calls proposed by the model.
     * <p>
     * This method assigns IDs to the calls, prompts the user for confirmation (if necessary),
     * and executes the approved calls.
     * </p>
     *
     * @param modelResponseMessage The message from the model containing the function calls.
     * @return A {@link FunctionProcessingResult} containing the outcomes and execution results.
     */
    public FunctionProcessingResult processFunctionCalls(ChatMessage modelResponseMessage) {
        Content modelResponseContent = modelResponseMessage.getContent();
        
        // Step 1: Identify all function calls. IDs and POJO args are already prepared by prepareForApplication.
        List<IdentifiedFunctionCall> identifiedCalls = new ArrayList<>();
        modelResponseContent.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(fc -> {
                    String id = fc.id().orElseThrow(() -> new IllegalStateException("FunctionCall missing ID after preparation."));
                    identifiedCalls.add(new IdentifiedFunctionCall(fc, id, part));
                });
            }
        });

        if (identifiedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), Collections.emptyList(), "", false, "");
        }
        
        // Step 2: Determine if we can bypass the UI prompt (all were ALWAYS preapproved).
        log.info("Checking batch approval for {} tool calls.", identifiedCalls.size());
        boolean allAlwaysApproved = true;
        for (IdentifiedFunctionCall ic : identifiedCalls) {
            FunctionConfirmation pref = config.getFunctionConfirmation(ic.getCall());
            log.info("Tool: {}, Preference: {}", ic.getCall().name().orElse("?"), pref);
            if (pref != FunctionConfirmation.ALWAYS) {
                allAlwaysApproved = false;
            }
        }

        PromptResult promptResult;
        boolean dialogShown = false;
        if (allAlwaysApproved) {
            log.info("All function calls are pre-approved. Skipping confirmation dialog.");
            Map<FunctionCall, FunctionConfirmation> confirmations = new LinkedHashMap<>();
            identifiedCalls.forEach(ic -> confirmations.put(ic.getCall(), FunctionConfirmation.ALWAYS));
            promptResult = new PromptResult(confirmations, "", false);
        } else {
            promptResult = prompter.prompt(modelResponseMessage, this.chat);
            dialogShown = true;
        }
        
        // Step 3: Execute the approved calls and collect outcomes.
        List<ExecutedToolCall> executedCalls = new ArrayList<>();
        List<ToolCallOutcome> outcomes = new ArrayList<>();

        for (IdentifiedFunctionCall idc : identifiedCalls) {
            ToolCallStatus status;
            if (promptResult.cancelled) {
                status = ToolCallStatus.CANCELLED;
            } else {
                FunctionConfirmation confirmation = promptResult.functionConfirmations.get(idc.getCall());
                if (confirmation == null) {
                    log.warn("No confirmation found for function call: {}. Defaulting to NO.", idc.getCall().name());
                    status = ToolCallStatus.NO;
                } else {
                    switch (confirmation) {
                        case ALWAYS: status = ToolCallStatus.ALWAYS; break;
                        case YES:    status = ToolCallStatus.YES;    break;
                        case NEVER:  status = ToolCallStatus.NEVER;  break;
                        case NO:
                        default:     status = ToolCallStatus.NO;     break;
                    }
                }
            }

            String executionFeedback = null;

            // Execute if approved
            if (status == ToolCallStatus.ALWAYS || status == ToolCallStatus.YES) {
                String toolName = idc.getCall().name().orElse("unknown");
                try {
                    if (failureTracker.isBlocked(idc.getCall())) {
                        throw new RuntimeException("Tool call '" + toolName + "' is temporarily blocked due to repeated failures.");
                    }

                    Chat.callingInstance.set(chat);
                    Method method = functionCallMethods.get(toolName);
                    if (method == null) {
                        throw new RuntimeException("Tool not found: '" + toolName + "' available tools: " + functionCallMethods.keySet());
                    }
                    
                    Map<String, Object> args = new HashMap<>(idc.getCall().args().get());
                    Object asyncFlag = args.remove("asynchronous");
                    boolean isAsync = asyncFlag instanceof Boolean && (Boolean) asyncFlag;

                    Object rawResult;
                    chat.getStatusManager().setExecutingToolName(toolName);

                    if (isAsync) {
                        final String jobId = UUID.randomUUID().toString();
                        rawResult = new JobInfo(jobId, JobStatus.STARTED, "Starting background task for " + toolName, null);
                        
                        Executors.cachedThreadPool.submit(() -> {
                            JobInfo completedJobInfo = new JobInfo(jobId, null, "Task for " + toolName, null);
                            try {
                                Chat.callingInstance.set(chat);
                                Object result = invokeFunctionMethod(method, args);
                                completedJobInfo.setStatus(JobStatus.COMPLETED);
                                completedJobInfo.setResult(result);
                                log.info("Asynchronous job {} completed successfully.", jobId);
                            } catch (Exception e) {
                                completedJobInfo.setStatus(JobStatus.FAILED);
                                completedJobInfo.setResult(ExceptionUtils.getStackTrace(e));
                                log.error("Asynchronous job {} failed.", jobId, e);
                            } finally {
                                chat.notifyJobCompletion(completedJobInfo);
                                Chat.callingInstance.remove();
                            }
                        });
                    } else {
                        rawResult = invokeFunctionMethod(method, args);
                    }
                    
                    if (rawResult instanceof UserFeedback) {
                        executionFeedback = ((UserFeedback) rawResult).getUserFeedback();
                    }

                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("output", rawResult);
                    
                    FunctionResponse fr = FunctionResponse.builder()
                        .id(idc.getId())
                        .name(toolName)
                        .response(responseMap)
                        .build();
                    
                    executedCalls.add(new ExecutedToolCall(idc.getSourcePart(), fr, rawResult));

                } catch (Exception e) {
                    log.error("Error executing tool call: {}", toolName, e);
                    failureTracker.recordFailure(idc.getCall(), e);
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("error", ExceptionUtils.getStackTrace(e));
                    FunctionResponse errorResponse = FunctionResponse.builder()
                        .id(idc.getId())
                        .name(toolName)
                        .response(errorMap)
                        .build();
                    executedCalls.add(new ExecutedToolCall(idc.getSourcePart(), errorResponse, e));
                } finally {
                    Chat.callingInstance.remove();
                }
            }

            outcomes.add(new ToolCallOutcome(idc, status, executionFeedback));
        }
        
        String feedbackMessage = generateFeedbackMessage(outcomes, promptResult.userComment, dialogShown);
        return new FunctionProcessingResult(executedCalls, outcomes, promptResult.userComment, dialogShown, feedbackMessage);
    }

    private String generateFeedbackMessage(List<ToolCallOutcome> outcomes, String userComment, boolean dialogShown) {
        StringBuilder feedbackText = new StringBuilder();
        
        // 1. Popup Comment (Highest priority for the model to see)
        if (dialogShown && StringUtils.isNotBlank(userComment)) {
            feedbackText.append("Tool confirmation popup comment: '").append(userComment).append("'\n\n");
        }

        // 2. Tool Feedback (The core outcomes)
        String toolFeedback = outcomes.stream()
            .map(outcome -> outcome.toFeedbackString(dialogShown))
            .collect(Collectors.joining(" "));
        
        feedbackText.append("Tool Feedback: ").append(toolFeedback);
        
        // 3. System Notes (Lower priority)
        if (!dialogShown) {
            feedbackText.append("\n\nNote: The tool confirmation popup was not displayed as all tool calls were auto-approved.");
        } else if (StringUtils.isBlank(userComment)) {
            feedbackText.append("\n\nNote: The tool confirmation popup was displayed, but the user did not provide any additional feedback in the comment field.");
        }

        return feedbackText.toString();
    }

    private Object invokeFunctionMethod(Method method, Map<String, Object> argsFromModel) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] argsToInvoke = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            // Arguments are already converted to POJOs by prepareForApplication.
            argsToInvoke[i] = argsFromModel.get(p.getName());
        }

        return method.invoke(null, argsToInvoke);
    }

    /**
     * Gets the {@link Tool} object containing all registered function declarations.
     *
     * @return The Tool object.
     */
    public Tool getFunctionTool() {
        return coreTools;
    }
    
    /**
     * Gets a list of information about all registered functions.
     *
     * @return An unmodifiable list of FunctionInfo objects.
     */
    public List<FunctionInfo> getFunctionInfos() {
        return Collections.unmodifiableList(functionInfos);
    }

    /**
     * Gets the tool configuration for API calls.
     *
     * @return The ToolConfig object.
     */
    public ToolConfig getToolConfig() {
        return toolConfig;
    }
    
    /**
     * Gets the context behavior (EPHEMERAL or STATEFUL_REPLACE) for a specific tool.
     *
     * @param toolName The name of the tool.
     * @return The ContextBehavior.
     */
    public ContextBehavior getContextBehavior(String toolName) {
        Method method = functionCallMethods.get(toolName);
        if (method != null) {
            AIToolMethod annotation = method.getAnnotation(AIToolMethod.class);
            if (annotation != null) {
                return annotation.behavior();
            }
        }
        return ContextBehavior.EPHEMERAL;
    }
    
    /**
     * Gets the Java Method associated with a specific tool name.
     *
     * @param toolName The name of the tool.
     * @return The Method object, or {@code null} if not found.
     */
    public Method getToolMethod(String toolName) {
        return functionCallMethods.get(toolName);
    }

    /**
     * Gets the set of tool names that are configured to be always approved.
     *
     * @return The set of always-approve tool names.
     */
    public Set<String> getAlwaysApproveFunctions() {
        return alwaysApproveFunctions;
    }

    /**
     * Gets the set of tool names that are configured to be never approved.
     *
     * @return The set of never-approve tool names.
     */
    public Set<String> getNeverApproveFunctions() {
        return neverApproveFunctions;
    }
}
