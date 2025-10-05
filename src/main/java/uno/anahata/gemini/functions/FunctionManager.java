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
import uno.anahata.gemini.functions.FunctionPrompter.PromptResult;
import uno.anahata.gemini.functions.util.GeminiSchemaGenerator;

public class FunctionManager {

    private static final Logger logger = Logger.getLogger(FunctionManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GeminiChat chat;
    private final FunctionPrompter prompter;
    private final Map<String, Method> functionCallMethods = new HashMap<>();
    private final Tool coreTools;
    private final ToolConfig toolConfig;

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

    public List<Content> processFunctionCalls(Content modelResponse, int contentIdx) {
        List<Content> results = new ArrayList<>();
        List<FunctionCall> allProposedCalls = new ArrayList<>();
        modelResponse.parts().ifPresent(parts -> {
            for (Part part : parts) {
                part.functionCall().ifPresent(allProposedCalls::add);
            }
        });

        if (allProposedCalls.isEmpty()) {
            return Collections.emptyList(); // No function calls present
        }
        
        Content promptContent = modelResponse;
        boolean otherPartsPresent = false;
        List<FunctionCall> preApprovedCalls = new ArrayList<>();
        List<FunctionCall> notPreapprovedCalls = new ArrayList<>();
        for (Part part : modelResponse.parts().get()) {
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                if (alwaysApproveFunctions.contains(fc.name().get())) {
                    preApprovedCalls.add(fc);
                } else {
                    notPreapprovedCalls.add(fc);
                }
            } else {
                otherPartsPresent = true;
            }
        }

        boolean skipPrompt = notPreapprovedCalls.isEmpty() && !otherPartsPresent;
        
        logger.info("skip prompt: " + skipPrompt);

        PromptResult promptResult = skipPrompt 
                ? new PromptResult(preApprovedCalls, notPreapprovedCalls, "")
                : prompter.prompt(promptContent, contentIdx, alwaysApproveFunctions, neverApproveFunctions);

        
        List<FunctionCall> allApprovedCalls = promptResult.approvedFunctions;

        if (allApprovedCalls.isEmpty() && promptResult.deniedFunctions.isEmpty() && (promptResult.userComment == null || promptResult.userComment.trim().isEmpty())) {
            return Collections.emptyList(); // User cancelled and provided no comment.
        }

        // 1. Handle approved function calls and their responses
        ImmutableList.Builder<Part> functionResponsePartsBuilder = ImmutableList.builder();
        for (FunctionCall approvedCall : allApprovedCalls) {
            try {
                GeminiChat.currentChat.set(chat);
                Method method = functionCallMethods.get(approvedCall.name().get());
                if (method == null) {
                    throw new RuntimeException("Tool not found: " + approvedCall.name());
                }
                Object funcResponse = invokeFunctionMethod(method, approvedCall.args().get());
                
                Map<String, Object> responseMap;
                if (funcResponse != null && !(funcResponse instanceof String || funcResponse instanceof Number || funcResponse instanceof Boolean || funcResponse instanceof Collection || funcResponse.getClass().isArray())) {
                     JsonElement jsonElement = GSON.toJsonTree(funcResponse);
                     responseMap = GSON.fromJson(jsonElement, Map.class);
                } else {
                    responseMap = new HashMap<>();
                    responseMap.put("output", funcResponse == null ? "" : funcResponse);
                }
                
                functionResponsePartsBuilder.add(Part.fromFunctionResponse(approvedCall.name().get(), responseMap));

            } catch (Exception e) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", ExceptionUtils.getStackTrace(e));
                functionResponsePartsBuilder.add(Part.fromFunctionResponse(approvedCall.name().get(), errorMap));
            } finally {
                GeminiChat.currentChat.remove();
            }
        }
        ImmutableList<Part> functionResponseParts = functionResponsePartsBuilder.build();
        if (!functionResponseParts.isEmpty()) {
            results.add(Content.builder().role("function").parts(functionResponseParts).build());
        }

        // 2. Handle denied functions
        if (!promptResult.deniedFunctions.isEmpty()) {
            ImmutableList.Builder<Part> deniedPartsBuilder = ImmutableList.builder();
            for (FunctionCall deniedCall : promptResult.deniedFunctions) {
                String denialMessage = String.format(
                        "User denied function call: %s",
                        deniedCall.name().get()
                );
                deniedPartsBuilder.add(Part.fromText(denialMessage));
            }
            results.add(Content.builder().role("model").parts(deniedPartsBuilder.build()).build());
        }


        // 3. Handle user comment
        if (promptResult.userComment != null && !promptResult.userComment.trim().isEmpty()) {
            results.add(Content.builder().role("user").parts(Part.fromText(promptResult.userComment)).build());
        }

        return results;
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
