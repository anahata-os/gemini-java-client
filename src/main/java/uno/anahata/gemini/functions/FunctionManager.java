package uno.anahata.gemini.functions;

import com.google.common.collect.ImmutableList;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.ChatConfig;
import uno.anahata.gemini.functions.spi.*;
import static com.google.common.collect.ImmutableList.toImmutableList;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.Executors;
import uno.anahata.gemini.functions.JobInfo.JobStatus;
import uno.anahata.gemini.functions.FunctionPrompter.PromptResult;
import uno.anahata.gemini.functions.schema.GeminiSchemaGenerator;
import uno.anahata.gemini.internal.GsonUtils;


/**
 * Handles java method to genai tool/function conversation for a given GeminiChat. 
 */
@Slf4j
public class FunctionManager {

    private static final Gson GSON = GsonUtils.getGson();

    @Getter
    private final GeminiChat chat;
    @Getter
    private final FunctionPrompter prompter;
    private final Map<String, Method> functionCallMethods = new HashMap<>();
    private final Tool coreTools;
    private final ToolConfig toolConfig;
    private final FailureTracker failureTracker;
    private final List<FunctionInfo> functionInfos = new ArrayList<>();

    private final Set<String> alwaysApproveFunctions = new HashSet<>();
    private final Set<String> neverApproveFunctions = new HashSet<>();
    
    /**
     * Maps a FunctionDeclaration to its declaring method.
     */
    @AllArgsConstructor
    public static class FunctionInfo {
        public final FunctionDeclaration declaration;
        public final Method method;
    }
    
    @Getter
    @AllArgsConstructor
    public static class ExecutedToolCall {
        public final Part sourceCallPart;
        public final FunctionResponse response;
        public final Object rawResult;
    }
    
    /**
     * The result of a function call prompt containing the prompt for several functions
     */
    @Getter
    @AllArgsConstructor
    public static class FunctionProcessingResult {
        public final List<ExecutedToolCall> executedCalls;
        public final List<Part> deniedCallParts;
        public final String userComment;
    }
    
    public FunctionManager(GeminiChat chat, ChatConfig config, FunctionPrompter prompter) {
        this.chat = chat;
        this.prompter = prompter;
        this.failureTracker = new FailureTracker(config);
        List<Class<?>> allClasses = new ArrayList<>();
        allClasses.add(LocalFiles.class);
        allClasses.add(LocalShell.class);
        allClasses.add(RunningJVM.class);
        allClasses.add(Images.class);
        allClasses.add(ContextWindow.class);
        allClasses.add(Session.class);
        if (prompter != null && config.getAutomaticFunctionClasses() != null) {
            allClasses.addAll(config.getAutomaticFunctionClasses());
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

    private Tool makeFunctionsTool(Class<?>... classes) {
        List<FunctionDeclaration> fds = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(AIToolMethod.class)) {
                    FunctionDeclaration fd = fromMethod(m);
                    functionCallMethods.put(fd.name().get(), m);
                    fds.add(fd);
                    functionInfos.add(new FunctionInfo(fd, m));
                }
            }
        }
        return Tool.builder().functionDeclarations(fds).build();
    }

    public FunctionProcessingResult processFunctionCalls(ChatMessage modelResponseMessage) {
        Content modelResponseContent = modelResponseMessage.getContent();
        
        Map<FunctionCall, Part> callToPartMap = new HashMap<>();
        modelResponseContent.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(fc -> callToPartMap.put(fc, part));
            }
        });

        if (callToPartMap.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), Collections.emptyList(), "");
        }
        
        List<FunctionCall> allProposedCalls = new ArrayList<>(callToPartMap.keySet());
        
        ChatConfig config = chat.getContextManager().getConfig();
        boolean allAlwaysApproved = true;
        for (FunctionCall fc : allProposedCalls) {
            if (config.getFunctionConfirmation(fc) != FunctionConfirmation.ALWAYS) {
                allAlwaysApproved = false;
                break;
            }
        }

        PromptResult promptResult;
        if (allAlwaysApproved) {
            log.info("Autopilot: All function calls are pre-approved. Skipping confirmation dialog.");
            promptResult = new PromptResult(allProposedCalls, Collections.emptyList(), "Autopilot");
        } else {
            promptResult = prompter.prompt(modelResponseMessage, this.chat);
        }
        
        List<FunctionCall> allApprovedCalls = promptResult.approvedFunctions;
        List<Part> deniedCallParts = promptResult.deniedFunctions.stream()
                .map(callToPartMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (allApprovedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), deniedCallParts, promptResult.userComment);
        }

        List<ExecutedToolCall> executedCalls = new ArrayList<>();
        for (FunctionCall approvedCall : allApprovedCalls) {
            String anahataId = approvedCall.id().orElse(UUID.randomUUID().toString());
            String toolName = approvedCall.name().orElse("unknown");
            Part sourceCallPart = callToPartMap.get(approvedCall);
            
            try {
                if (failureTracker.isBlocked(approvedCall)) {
                    throw new RuntimeException("Tool call '" + toolName + "' is temporarily blocked due to repeated failures.");
                }

                GeminiChat.callingInstance.set(chat);
                Method method = functionCallMethods.get(toolName);
                if (method == null) {
                    throw new RuntimeException("Tool not found: '" + toolName + "' available tools: " + functionCallMethods.keySet());
                }
                
                Map<String, Object> args = new HashMap<>(approvedCall.args().get());
                Object asyncFlag = args.remove("asynchronous");
                boolean isAsync = asyncFlag instanceof Boolean && (Boolean) asyncFlag;

                Object rawResult;

                if (isAsync) {
                    final String jobId = UUID.randomUUID().toString();
                    rawResult = new JobInfo(jobId, JobStatus.STARTED, "Starting background task for " + toolName, null);
                    
                    Executors.cachedThreadPool.submit(() -> {
                        JobInfo completedJobInfo = new JobInfo(jobId, null, "Task for " + toolName, null);
                        try {
                            GeminiChat.callingInstance.set(chat);
                            Object result = invokeFunctionMethod(method, args);
                            completedJobInfo.setStatus(JobStatus.COMPLETED);
                            completedJobInfo.setResult(result);
                            log.info("Asynchronous job " + jobId + " completed successfully.");
                        } catch (Exception e) {
                            completedJobInfo.setStatus(JobStatus.FAILED);
                            completedJobInfo.setResult(ExceptionUtils.getStackTrace(e));
                            log.error("Asynchronous job " + jobId + " failed.", e);
                        } finally {
                            chat.notifyJobCompletion(completedJobInfo);
                            GeminiChat.callingInstance.remove();
                        }
                    });
                } else {
                    rawResult = invokeFunctionMethod(method, args);
                }
                
                Map<String, Object> responseMap;
                if (rawResult instanceof JobInfo) {
                    responseMap = GSON.fromJson(GSON.toJson(rawResult), Map.class);
                } else if (rawResult != null && !(rawResult instanceof String || rawResult instanceof Number || rawResult instanceof Boolean || rawResult instanceof Collection || rawResult.getClass().isArray())) {
                     JsonElement jsonElement = GSON.toJsonTree(rawResult);
                     responseMap = GSON.fromJson(jsonElement, Map.class);
                } else {
                    responseMap = new HashMap<>();
                    responseMap.put("output", rawResult == null ? "" : rawResult);
                }
                
                FunctionResponse fr = FunctionResponse.builder()
                    .id(anahataId)
                    .name(toolName)
                    .response(responseMap)
                    .build();
                
                executedCalls.add(new ExecutedToolCall(sourceCallPart, fr, rawResult));

            } catch (Exception e) {
                log.error("Error executing tool call: {}", toolName, e);
                failureTracker.recordFailure(approvedCall, e);
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", ExceptionUtils.getStackTrace(e));
                FunctionResponse errorResponse = FunctionResponse.builder()
                    .id(anahataId)
                    .name(toolName)
                    .response(errorMap)
                    .build();
                executedCalls.add(new ExecutedToolCall(sourceCallPart, errorResponse, e));
            } finally {
                GeminiChat.callingInstance.remove();
            }
        }
        
        return new FunctionProcessingResult(executedCalls, deniedCallParts, promptResult.userComment);
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
                continue;
            }

            if (paramType.isPrimitive() || Number.class.isAssignableFrom(paramType) || Boolean.class.isAssignableFrom(paramType) || String.class.equals(paramType)) {
                 argsToInvoke[i] = argValueFromModel;
            } else {
                String json = GSON.toJson(argValueFromModel);
                argsToInvoke[i] = GSON.fromJson(json, paramType);
            }
        }

        return method.invoke(null, argsToInvoke);
    }

    public FunctionDeclaration fromMethod(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Only static methods are supported. Not static: " + method);
        }
        AIToolMethod methodAnnotation = method.getAnnotation(AIToolMethod.class);
        
        StringBuilder descriptionBuilder = new StringBuilder(methodAnnotation.value());
        
        // Generate and append the full method signature to the description
        String signature = Modifier.toString(method.getModifiers())
                + " " + method.getReturnType().getSimpleName()
                + " " + method.getName() + "("
                + Stream.of(method.getParameters())
                        .map(p -> p.getType().getSimpleName() + " " + p.getName())
                        .collect(Collectors.joining(", "))
                + ")";

        if (method.getExceptionTypes().length > 0) {
            signature += " throws " + Stream.of(method.getExceptionTypes())
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "));
        }
        descriptionBuilder.append("\n\njava method signature: ").append(signature);
        
        Schema responseSchema = GeminiSchemaGenerator.generateSchema(method.getReturnType(), "Schema for " + method.getReturnType().getSimpleName());

        Map<String, Schema> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter p : method.getParameters()) {
            String paramName = p.getName();
            AIToolParam paramAnnotation = p.getAnnotation(AIToolParam.class);
            String paramDescription = (paramAnnotation != null) ? paramAnnotation.value() : "No description";
            
            properties.put(paramName, GeminiSchemaGenerator.generateSchema(p.getType(), paramDescription));
            required.add(paramName);
        }
        
        properties.put("asynchronous", Schema.builder().type(Type.Known.BOOLEAN).description("Set to true to run this task in the background and return a job ID immediately.").build());

        Schema paramsSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(properties)
                .required(required)
                .build();
        
        String finalToolName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        if (!methodAnnotation.requiresApproval()) {
            alwaysApproveFunctions.add(finalToolName);
        }

        return FunctionDeclaration.builder()
                .name(finalToolName)
                .description(descriptionBuilder.toString())
                .parameters(paramsSchema)
                .response(responseSchema)
                .build();
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
