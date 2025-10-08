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
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.Executors;
import uno.anahata.gemini.JobInfo;
import uno.anahata.gemini.JobInfo.JobStatus;
import uno.anahata.gemini.functions.FunctionPrompter.PromptResult;
import uno.anahata.gemini.functions.util.GeminiSchemaGenerator;

// V2: Refactored to work with ChatMessage and the new architecture
public class FunctionManager {

    private static final Logger logger = Logger.getLogger(FunctionManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GeminiChat chat;
    private final FunctionPrompter prompter;
    private final Map<String, Method> functionCallMethods = new HashMap<>();
    private final Tool coreTools;
    private final ToolConfig toolConfig;
    private final FailureTracker failureTracker = new FailureTracker();

    private final Set<String> alwaysApproveFunctions = new HashSet<>();
    private final Set<String> neverApproveFunctions = new HashSet<>();
    
    public FunctionManager(GeminiChat chat, GeminiConfig config, FunctionPrompter prompter) {
        this.chat = chat;
        this.prompter = prompter;
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

    private Tool makeFunctionsTool(Class<?>... classes) {
        List<FunctionDeclaration> fds = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(AIToolMethod.class)) {
                    FunctionDeclaration fd = fromMethod(m);
                    functionCallMethods.put(fd.name().get(), m);
                    fds.add(fd);
                }
            }
        }
        return Tool.builder().functionDeclarations(fds).build();
    }

    /**
     * Processes function calls from a model's response message.
     * This is the main entry point for tool execution.
     * @param modelResponseMessage The ChatMessage containing the model's response.
     * @return A List of FunctionResponse objects, ready to be sent back to the model.
     */
    public List<FunctionResponse> processFunctionCalls(ChatMessage modelResponseMessage) {
        Content modelResponseContent = modelResponseMessage.getContent();
        int contentIdx = chat.getContextManager().getContext().indexOf(modelResponseMessage);
        
        List<FunctionCall> allProposedCalls = new ArrayList<>();
        modelResponseContent.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(allProposedCalls::add);
            }
        });

        if (allProposedCalls.isEmpty()) {
            return Collections.emptyList();
        }
        
        // User Approval Step
        PromptResult promptResult = prompter.prompt(modelResponseMessage, contentIdx, alwaysApproveFunctions, neverApproveFunctions);
        List<FunctionCall> allApprovedCalls = promptResult.approvedFunctions;

        if (allApprovedCalls.isEmpty()) {
            // TODO: Handle denied calls and user comments by creating new ChatMessages
            return Collections.emptyList();
        }

        List<FunctionResponse> responses = new ArrayList<>();
        for (FunctionCall approvedCall : allApprovedCalls) {
            // Generate a stable ID for the call-response link
            String anahataId = approvedCall.id().orElse(UUID.randomUUID().toString());
            
            try {
                // FailureTracker Check
                if (failureTracker.isBlocked(approvedCall)) {
                    throw new RuntimeException("Tool call is temporarily blocked due to repeated failures.");
                }

                GeminiChat.currentChat.set(chat);
                Method method = functionCallMethods.get(approvedCall.name().get());
                if (method == null) {
                    throw new RuntimeException("Tool not found: " + approvedCall.name());
                }
                
                // ASYNC HANDLING LOGIC START
                Map<String, Object> args = new HashMap<>(approvedCall.args().get()); // Make a mutable copy
                Object asyncFlag = args.remove("asynchronous"); // Remove the artificial parameter
                boolean isAsync = asyncFlag instanceof Boolean && (Boolean) asyncFlag;

                Object funcResponsePayload;

                if (isAsync) {
                    final String jobId = UUID.randomUUID().toString();
                    funcResponsePayload = new JobInfo(jobId, JobStatus.STARTED, "Starting background task for " + approvedCall.name().get(), null);
                    
                    Executors.cachedThreadPool.submit(() -> {
                        try {
                            GeminiChat.currentChat.set(chat); // Propagate ThreadLocal
                            Object result = invokeFunctionMethod(method, args);
                            // TODO: Proactively notify the model of job completion via GeminiChat
                            logger.info("Asynchronous job " + jobId + " completed successfully with result: " + result);
                        } catch (Exception e) {
                            // TODO: Proactively notify the model of job failure via GeminiChat
                            logger.log(Level.SEVERE, "Asynchronous job " + jobId + " failed.", e);
                        } finally {
                            GeminiChat.currentChat.remove();
                        }
                    });
                } else {
                    funcResponsePayload = invokeFunctionMethod(method, args);
                }
                // ASYNC HANDLING LOGIC END
                
                Map<String, Object> responseMap;
                if (funcResponsePayload instanceof JobInfo) {
                    // Special handling for async jobs
                    responseMap = GSON.fromJson(GSON.toJson(funcResponsePayload), Map.class);
                } else if (funcResponsePayload != null && !(funcResponsePayload instanceof String || funcResponsePayload instanceof Number || funcResponsePayload instanceof Boolean || funcResponsePayload instanceof Collection || funcResponsePayload.getClass().isArray())) {
                     JsonElement jsonElement = GSON.toJsonTree(funcResponsePayload);
                     responseMap = GSON.fromJson(jsonElement, Map.class);
                } else {
                    responseMap = new HashMap<>();
                    responseMap.put("output", funcResponsePayload == null ? "" : funcResponsePayload);
                }
                
                responses.add(FunctionResponse.builder()
                    .id(anahataId)
                    .name(approvedCall.name().get())
                    .response(responseMap)
                    .build());

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
        
        return responses;
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
            throw new IllegalArgumentException("Only static methods are supported.");
        }
        AIToolMethod methodAnnotation = method.getAnnotation(AIToolMethod.class);
        String functionDescription = methodAnnotation.value();

        Map<String, Schema> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter p : method.getParameters()) {
            String paramName = p.getName();
            AIToolParam paramAnnotation = p.getAnnotation(AIToolParam.class);
            String paramDescription = (paramAnnotation != null) ? paramAnnotation.value() : "No description";
            
            properties.put(paramName, GeminiSchemaGenerator.generateSchema(p.getType(), paramDescription));
            required.add(paramName);
        }
        
        // Add the "asynchronous" artificial parameter
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
                .description(functionDescription)
                .parameters(paramsSchema)
                .build();
    }

    private static ToolConfig makeToolConfigForFunctionCalling() {
        return ToolConfig.builder()
                .functionCallingConfig(FunctionCallingConfig.builder().mode(FunctionCallingConfigMode.Known.AUTO).build())
                .build();
    }

    public Tool getFunctionTool() {
        return coreTools;
    }

    public ToolConfig getToolConfig() {
        return toolConfig;
    }
}
