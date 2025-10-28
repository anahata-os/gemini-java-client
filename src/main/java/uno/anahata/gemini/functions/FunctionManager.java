package uno.anahata.gemini.functions;

import com.google.common.collect.ImmutableList;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.logging.Logger;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.GeminiConfig;
import uno.anahata.gemini.functions.spi.*;
import static com.google.common.collect.ImmutableList.toImmutableList;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.Executors;
import uno.anahata.gemini.functions.JobInfo.JobStatus;
import uno.anahata.gemini.functions.FunctionPrompter.PromptResult;
import uno.anahata.gemini.functions.schema.GeminiSchemaGenerator;

public class FunctionManager {

    private static final Logger logger = Logger.getLogger(FunctionManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GeminiChat chat;
    private final FunctionPrompter prompter;
    private final Map<String, Method> functionCallMethods = new HashMap<>();
    private final Tool coreTools;
    private final ToolConfig toolConfig;
    private final FailureTracker failureTracker;
    private final List<FunctionInfo> functionInfos = new ArrayList<>();

    private final Set<String> alwaysApproveFunctions = new HashSet<>();
    private final Set<String> neverApproveFunctions = new HashSet<>();
    
    @Getter
    @AllArgsConstructor
    public static class FunctionInfo {
        private final FunctionDeclaration declaration;
        private final Method method;
    }
    
    @Getter
    @AllArgsConstructor
    public static class FunctionProcessingResult {
        private final List<FunctionResponse> responses;
        private final List<FunctionCall> deniedCalls;
        private final String userComment;
        // NEW FIELD: Maps a generated FunctionResponse to the Part that contained its FunctionCall.
        private final Map<FunctionResponse, Part> responseToCallLinks;
    }
    
    public FunctionManager(GeminiChat chat, GeminiConfig config, FunctionPrompter prompter) {
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
        logger.info("FunctionManager scanning classes for @AIToolMethod: " + allClasses);
        this.coreTools = makeFunctionsTool(allClasses.toArray(new Class<?>[0]));
        logger.info("FunctionManager created. Total Function Declarations: " + coreTools.functionDeclarations().get().size());

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
        
        List<FunctionCall> allProposedCalls = new ArrayList<>();
        modelResponseContent.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(allProposedCalls::add);
            }
        });

        if (allProposedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), Collections.emptyList(), "", Collections.emptyMap());
        }
        
        GeminiConfig config = chat.getContextManager().getConfig();
        boolean allAlwaysApproved = true;
        for (FunctionCall fc : allProposedCalls) {
            if (config.getFunctionConfirmation(fc) != FunctionConfirmation.ALWAYS) {
                allAlwaysApproved = false;
                break;
            }
        }

        PromptResult promptResult;
        if (allAlwaysApproved) {
            logger.info("Autopilot: All function calls are pre-approved. Skipping confirmation dialog.");
            promptResult = new PromptResult(allProposedCalls, Collections.emptyList(), "Autopilot");
        } else {
            promptResult = prompter.prompt(modelResponseMessage, this.chat);
        }
        
        List<FunctionCall> allApprovedCalls = promptResult.approvedFunctions;

        if (allApprovedCalls.isEmpty()) {
            return new FunctionProcessingResult(Collections.emptyList(), promptResult.deniedFunctions, promptResult.userComment, Collections.emptyMap());
        }

        // NEW: Map to store the links from the generated response back to the call part.
        Map<FunctionResponse, Part> responseToCallLinks = new HashMap<>();
        List<? extends Part> originalParts = modelResponseMessage.getContent().parts().get();

        List<FunctionResponse> responses = new ArrayList<>();
        for (FunctionCall approvedCall : allApprovedCalls) {
            String anahataId = approvedCall.id().orElse(UUID.randomUUID().toString());
            
            try {
                if (failureTracker.isBlocked(approvedCall)) {
                    throw new RuntimeException("Tool call '" + approvedCall.name().get() + "' is temporarily blocked due to repeated failures.");
                }

                GeminiChat.currentChat.set(chat);
                Method method = functionCallMethods.get(approvedCall.name().get());
                if (method == null) {
                    throw new RuntimeException("Tool not found: '" + approvedCall.name().get() + "' available tools: " + functionCallMethods.keySet());
                }
                
                Map<String, Object> args = new HashMap<>(approvedCall.args().get());
                Object asyncFlag = args.remove("asynchronous");
                boolean isAsync = asyncFlag instanceof Boolean && (Boolean) asyncFlag;

                Object funcResponsePayload;

                if (isAsync) {
                    final String jobId = UUID.randomUUID().toString();
                    funcResponsePayload = new JobInfo(jobId, JobStatus.STARTED, "Starting background task for " + approvedCall.name().get(), null);
                    
                    Executors.cachedThreadPool.submit(() -> {
                        JobInfo completedJobInfo = new JobInfo(jobId, null, "Task for " + approvedCall.name().get(), null);
                        try {
                            GeminiChat.currentChat.set(chat);
                            Object result = invokeFunctionMethod(method, args);
                            completedJobInfo.setStatus(JobStatus.COMPLETED);
                            completedJobInfo.setResult(result);
                            logger.info("Asynchronous job " + jobId + " completed successfully.");
                        } catch (Exception e) {
                            completedJobInfo.setStatus(JobStatus.FAILED);
                            completedJobInfo.setResult(ExceptionUtils.getStackTrace(e));
                            logger.log(Level.SEVERE, "Asynchronous job " + jobId + " failed.", e);
                        } finally {
                            chat.notifyJobCompletion(completedJobInfo);
                            GeminiChat.currentChat.remove();
                        }
                    });
                } else {
                    funcResponsePayload = invokeFunctionMethod(method, args);
                }
                
                Map<String, Object> responseMap;
                if (funcResponsePayload instanceof JobInfo) {
                    responseMap = GSON.fromJson(GSON.toJson(funcResponsePayload), Map.class);
                } else if (funcResponsePayload != null && !(funcResponsePayload instanceof String || funcResponsePayload instanceof Number || funcResponsePayload instanceof Boolean || funcResponsePayload instanceof Collection || funcResponsePayload.getClass().isArray())) {
                     JsonElement jsonElement = GSON.toJsonTree(funcResponsePayload);
                     responseMap = GSON.fromJson(jsonElement, Map.class);
                } else {
                    responseMap = new HashMap<>();
                    responseMap.put("output", funcResponsePayload == null ? "" : funcResponsePayload);
                }
                
                FunctionResponse fr = FunctionResponse.builder()
                    .id(anahataId)
                    .name(approvedCall.name().get())
                    .response(responseMap)
                    .build();
                responses.add(fr);

                // NEW: Find the original Part and create the link.
                for (Part part : originalParts) {
                    if (part.functionCall().isPresent() && part.functionCall().get() == approvedCall) {
                        responseToCallLinks.put(fr, part);
                        break; 
                    }
                }

            } catch (Exception e) {
                failureTracker.recordFailure(approvedCall, e);
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", ExceptionUtils.getStackTrace(e));
                responses.add(FunctionResponse.builder()
                    .id(anahataId)
                    .name(approvedCall.name().get())
                    .response(errorMap)
                    .build());
            } finally {
                GeminiChat.currentChat.remove();
            }
        }
        
        return new FunctionProcessingResult(responses, promptResult.deniedFunctions, promptResult.userComment, responseToCallLinks);
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
        String returnTypeSchema = GeminiSchemaGenerator.generateSchemaAsString(method.getReturnType());
        
        if (returnTypeSchema != null) {
            descriptionBuilder.append("\n\nReturns an object with the following JSON schema:\n").append(returnTypeSchema);
        }

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
                .type("OBJECT")
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
