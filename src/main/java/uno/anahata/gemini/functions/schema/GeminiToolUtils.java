package uno.anahata.gemini.functions.schema;

import com.google.genai.types.FunctionCall;
import com.google.gson.Gson;
import uno.anahata.gemini.functions.AIToolMethod;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import static com.google.genai.types.Type.Known.OBJECT;
import java.nio.file.Files;
import java.util.HashMap;
import org.apache.commons.lang3.exception.ExceptionUtils;


/**
 * Record to hold both parametersSchema and responseSchema for a
 * FunctionDeclaration.
 */
/*
record FunctionDeclarationSchema(
        Schema parametersSchema,
        Map<String, Object> responseSchema
) {}
 */
/**
 * Utility class for generating FunctionDeclaration and FunctionResponse for
 * Google Generative AI SDK (1.16.0) using reflection and @AITool annotations.
 * Separates parametersSchema and responseSchema.
 */
public class GeminiToolUtils {

    private static final Gson GSON = new Gson();

    /**
     * Creates a FunctionDeclaration and its schemas for a method annotated with
     *
     * @AITool. Returns a record with parametersSchema (SDK Schema) and
     * responseSchema (JSON schema Map).
     */
    public static FunctionDeclaration createFunctionDeclaration(Method method) {
        String toolName = makeToolName(method);
        AIToolMethod methodAnnotation = method.getAnnotation(AIToolMethod.class);
        if (methodAnnotation == null) {
            throw new IllegalArgumentException("Method must be annotated with @AIToolMethod: " + method.getName());
        }

        // Build parameters schema
        //Map<String, Map<String,String>> properties = new LinkedHashMap<>();
        List<String> allParams = Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .collect(Collectors.toList()); // Assume all parameters are required

        Map<String, Schema> paramSchemas = new HashMap<>();

        for (Parameter param : method.getParameters()) {
            AIToolMethod paramAnnotation = param.getAnnotation(AIToolMethod.class);
            String description = paramAnnotation != null ? paramAnnotation.value() : param.getName();
            Schema type = GeminiSchemaGenerator2.generateSchema(param.getType(), description);
            paramSchemas.put(param.getName(), type);
        }

        // Convert to SDK Schema
        Schema paramsSchema = Schema.builder().type(Type.Known.OBJECT).properties(paramSchemas).build();

        // Build response schema
        // Combine method description with response schema
        String toolDescription = makeToolDescription(method);

        Schema responseSchema = GeminiSchemaGenerator2.generateSchema(method.getReturnType(), toolDescription);

        FunctionDeclaration declaration = FunctionDeclaration.builder()
                .name(toolName)
                .description(toolDescription)
                .parameters(paramsSchema)
                .response(responseSchema)
                .build();

        return declaration;
    }

    /**
     * Executes a method and creates a FunctionResponse with the serialized
     * result.
     *
     * @param instance the object instance to invoke the method on
     * @param args the arguments from the FunctionCall
     */
    public static FunctionResponse createFunctionResponse(String id, Method method, Object instance, Object[] args) throws Exception {
        // Execute the method
        String toolName = makeToolName(method);
        Object result;
        try {
            result = method.invoke(instance, args);
        } catch (Exception e) {
            // Serialize exception as error response
            Map errorMap = Map.of("error", ExceptionUtils.getStackTrace(e));
            return FunctionResponse.builder().id(id).name(toolName).response(errorMap).build();
        }

        // Serialize result to JSON
        String jsonResponse = result == null ? "{}" : GSON.toJson(result);
        Map responseMap = Map.of("output", jsonResponse);

        // Use ClassName_methodName as response name
        return FunctionResponse.builder().name(toolName).response(responseMap).build();
    }

    public static String makeToolName(Method method) {
        String className = method.getDeclaringClass().getName();
        String toolName = className + "." + method.getName();
        return toolName;
    }

    public static String makeToolDescription(Method method) {
        if (method.isAnnotationPresent(AIToolMethod.class)) {
            String val = method.getAnnotation(AIToolMethod.class).value();
            if (val == null || val.trim().isEmpty()) {
                return "no description provided, ask the user";
            }
            return val;
        }

        throw new IllegalArgumentException(method.toString() + " lacks description in @AIToolMethod");
    }

    

    // Example usage for testing
    public static void main(String[] args) throws NoSuchMethodException {
        // Example: Get declaration for Files.readFile
        Method readFileMethod = Files.class.getMethod("readFile", String.class);
        FunctionDeclaration declSchema = createFunctionDeclaration(readFileMethod);

        // Example: Get declaration for Files.listDirectory
        Method listDirMethod = Files.class.getMethod("listDirectory", String.class);
        FunctionDeclaration listDirDecl = createFunctionDeclaration(listDirMethod);

    }
}
