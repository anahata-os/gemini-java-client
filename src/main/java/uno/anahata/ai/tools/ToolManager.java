package uno.anahata.ai.tools;

import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
import static uno.anahata.ai.tools.FunctionConfirmation.ALWAYS;
import static uno.anahata.ai.tools.FunctionConfirmation.NEVER;
import static uno.anahata.ai.tools.FunctionConfirmation.NO;
import static uno.anahata.ai.tools.FunctionConfirmation.YES;
import uno.anahata.ai.tools.JobInfo.JobStatus;
import uno.anahata.ai.tools.FunctionPrompter.PromptResult;
import uno.anahata.ai.internal.GsonUtils;


/**
 * Handles java method to genai tool/function conversation for a given Chat. 
 */
@Slf4j
public class ToolManager {

    private static final Gson GSON = GsonUtils.getGson();

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
    private final AtomicInteger idCounter = new AtomicInteger(1);

    private final Set<String> alwaysApproveFunctions = new HashSet<>();
    private final Set<String> neverApproveFunctions = new HashSet<>();
    
    public void resetIdCounter(int value) {
        log.info("Resetting tool call ID counter to {}", value);
        idCounter.set(value);
    }
    
    public ToolManager(Chat chat, FunctionPrompter prompter) {
        this.chat = chat;
        this.config = chat.getConfig();
        this.prompter = prompter;
        this.failureTracker = new FailureTracker(chat);
        List<Class<?>> allClasses = new ArrayList<>();
        
        if (prompter != null && config.getToolClasses() != null) {
            allClasses.addAll(config.getToolClasses());
        }
        log.info("FunctionManager scanning classes for @AIToolMethod: " + allClasses);
        this.coreTools = makeFunctionsTool(allClasses.toArray(new Class<?>[0]));
        log.info("FunctionManager created. Total Function Declarations: " + coreTools.functionDeclarations().get().size());

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
                            if (paramAnnotation != null && StringUtils.isNotBlank(paramAnnotation.value())) {
                                String paramDescription = paramAnnotation.value();
                                String typeDescription = paramSchema.description().orElse("");
                                
                                String combinedDescription = paramDescription;
                                if (StringUtils.isNotBlank(typeDescription)) {
                                    combinedDescription += "\n\n(Details: " + typeDescription + ")";
                                }
                                
                                paramSchema = paramSchema.toBuilder().description(combinedDescription).build();
                            }

                            properties.put(paramName, paramSchema);
                            requiredParams.add(paramName);
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
                                    .type(Type.Known.OBJECT)
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

    public FunctionProcessingResult processFunctionCalls(ChatMessage modelResponseMessage) {
        Content modelResponseContent = modelResponseMessage.getContent();
        
        // Step 1: Identify all function calls and assign them a short, sequential ID for this turn.
        List<IdentifiedFunctionCall> identifiedCalls = new ArrayList<>();
        modelResponseContent.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(fc -> {
                    // Prioritize the ID from the model, but fall back to our own sequential ID if it's missing.
                    String id = fc.id().orElse(String.valueOf(idCounter.getAndIncrement()));
                    identifiedCalls.add(new IdentifiedFunctionCall(fc, id, part));
                });
            }
        });

        if (identifiedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), Collections.emptyList(), "");
        }
        
        // Step 2: Determine if we can bypass the UI prompt (autopilot).
        boolean allAlwaysApproved = identifiedCalls.stream()
            .allMatch(ic -> config.getFunctionConfirmation(ic.getCall()) == FunctionConfirmation.ALWAYS);

        PromptResult promptResult;
        if (allAlwaysApproved) {
            log.info("Autopilot: All function calls are pre-approved. Skipping confirmation dialog.");
            Map<FunctionCall, FunctionConfirmation> confirmations = new LinkedHashMap<>();
            identifiedCalls.forEach(ic -> confirmations.put(ic.getCall(), FunctionConfirmation.ALWAYS));
            promptResult = new PromptResult(confirmations, "Autopilot", false);
        } else {
            promptResult = prompter.prompt(modelResponseMessage, this.chat);
        }
        
        // Step 3: Build the definitive list of outcomes for every proposed call.
        List<ToolCallOutcome> outcomes = new ArrayList<>();
        List<IdentifiedFunctionCall> approvedCalls = new ArrayList<>();

        for (IdentifiedFunctionCall idc : identifiedCalls) {
            ToolCallStatus status;
            if (promptResult.cancelled) {
                status = ToolCallStatus.CANCELLED;
            } else {
                FunctionConfirmation confirmation = promptResult.functionConfirmations.get(idc.getCall());
                if (confirmation == null) {
                    // Should not happen with a compliant prompter, but handle defensively.
                    log.warn("No confirmation found for function call: {}. Defaulting to NO.", idc.getCall().name());
                    status = ToolCallStatus.NO;
                } else {
                    switch (confirmation) {
                        case ALWAYS:
                            status = ToolCallStatus.ALWAYS;
                            approvedCalls.add(idc);
                            break;
                        case YES:
                            status = ToolCallStatus.YES;
                            approvedCalls.add(idc);
                            break;
                        case NEVER:
                            status = ToolCallStatus.NEVER;
                            break;
                        case NO:
                        default:
                            status = ToolCallStatus.NO;
                            break;
                    }
                }
            }
            outcomes.add(new ToolCallOutcome(idc, status));
        }

        if (approvedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), outcomes, promptResult.userComment);
        }

        // Step 4: Execute the approved calls.
        List<ExecutedToolCall> executedCalls = new ArrayList<>();
        for (IdentifiedFunctionCall approvedCall : approvedCalls) {
            String toolName = approvedCall.getCall().name().orElse("unknown");
            
            try {
                if (failureTracker.isBlocked(approvedCall.getCall())) {
                    throw new RuntimeException("Tool call '" + toolName + "' is temporarily blocked due to repeated failures.");
                }

                Chat.callingInstance.set(chat);
                Method method = functionCallMethods.get(toolName);
                if (method == null) {
                    throw new RuntimeException("Tool not found: '" + toolName + "' available tools: " + functionCallMethods.keySet());
                }
                
                Map<String, Object> args = new HashMap<>(approvedCall.getCall().args().get());
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
                
                Map<String, Object> responseMap = convertResultToMap(rawResult);
                
                FunctionResponse fr = FunctionResponse.builder()
                    .id(approvedCall.getId()) // Use our stable, short ID
                    .name(toolName)
                    .response(responseMap)
                    .build();
                
                executedCalls.add(new ExecutedToolCall(approvedCall.getSourcePart(), fr, rawResult));

            } catch (Exception e) {
                log.error("Error executing tool call: {}", toolName, e);
                failureTracker.recordFailure(approvedCall.getCall(), e);
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", ExceptionUtils.getStackTrace(e));
                FunctionResponse errorResponse = FunctionResponse.builder()
                    .id(approvedCall.getId()) // Use our stable, short ID
                    .name(toolName)
                    .response(errorMap)
                    .build();
                executedCalls.add(new ExecutedToolCall(approvedCall.getSourcePart(), errorResponse, e));
            } finally {
                Chat.callingInstance.remove();
            }
        }
        
        return new FunctionProcessingResult(executedCalls, outcomes, promptResult.userComment);
    }

    private Map<String, Object> convertResultToMap(Object rawResult) {
        if (rawResult == null) {
            return Collections.singletonMap("output", "");
        }
        // Let Gson handle the conversion to a generic Map, which is what FunctionResponse expects.
        JsonElement jsonElement = GSON.toJsonTree(rawResult);
        if (jsonElement.isJsonObject()) {
            return GSON.fromJson(jsonElement, Map.class);
        } else {
            // If the result is a primitive, string, or array, wrap it.
            return Collections.singletonMap("output", rawResult);
        }
    }
    
    private Object invokeFunctionMethod(Method method, Map<String, Object> argsFromModel) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] argsToInvoke = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            String paramName = p.getName();
            Object argValueFromModel = argsFromModel.get(paramName);
            Class<?> paramType = p.getType();

            if (argValueFromModel == null) {
                argsToInvoke[i] = null;
            } else {
                // Let Gson handle all conversions. It's smart enough to convert
                // a JSON number to the correct Java numeric type (e.g. Integer -> Long)
                // and other objects from Map<String, Object> to their target type.
                JsonElement jsonElement = GSON.toJsonTree(argValueFromModel);
                argsToInvoke[i] = GSON.fromJson(jsonElement, paramType);
            }
        }

        return method.invoke(null, argsToInvoke);
    }

    public Tool getFunctionTool() {
        return coreTools;
    }
    
    public List<FunctionInfo> getFunctionInfos() {
        return Collections.unmodifiableList(functionInfos);
    }

    public ToolConfig getToolConfig() {
        return toolConfig;
    }
    
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
    
    public Method getToolMethod(String toolName) {
        return functionCallMethods.get(toolName);
    }

    public Set<String> getAlwaysApproveFunctions() {
        return alwaysApproveFunctions;
    }

    public Set<String> getNeverApproveFunctions() {
        return neverApproveFunctions;
    }
}
