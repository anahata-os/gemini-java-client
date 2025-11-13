package uno.anahata.gemini.functions.schema;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.internal.GsonUtils;

/**
 * Final schema generator that combines the power of Swagger annotation processing
 * with the necessary post-processing to make schemas compatible with the Gemini API.
 * <p>
 * It performs a two-step process:
 * 1. Generate a standard JSON Schema with definitions/components and $refs using Jackson/Swagger.
 * 2. Inline all the $refs recursively to produce a single, flat schema that the Gemini API requires.
 */
@Slf4j
public class SwaggerSchemaParser {

    private static final Gson GSON = GsonUtils.getGson();

    /**
     * Generates a Gemini-compatible, pretty-printed JSON schema string for a given class.
     *
     * @param clazz The class to generate the schema for.
     * @return A string containing the fully inlined JSON Schema, or null.
     * @throws Exception if schema generation or processing fails.
     */
    public static String generateInlinedSchema(Class<?> clazz) throws Exception {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            return null;
        }

        String standardSchemaJson = JacksonSchemaGenerator.generateSchema(clazz);
        if (standardSchemaJson == null) {
            return null;
        }

        Map<String, Object> schemaMap = GSON.fromJson(standardSchemaJson, Map.class);
        Map<String, Object> definitions = getDefinitions(schemaMap);
        Map<String, Object> inlinedSchemaMap = inlineSchemaRecursive(schemaMap, definitions, new HashSet<>());
        
        if (inlinedSchemaMap != null) {
            inlinedSchemaMap.remove("components");
            inlinedSchemaMap.remove("definitions");
        }

        return GSON.toJson(inlinedSchemaMap);
    }
    
    /**
     * Generates a Gemini-compatible `Schema` object for a given class.
     *
     * @param clazz The class to generate the schema for.
     * @return A `com.google.genai.types.Schema` object, or null.
     * @throws Exception if schema generation or processing fails.
     */
    public static com.google.genai.types.Schema generateInlinedSchemaObject(Class<?> clazz) throws Exception {
        String schemaJson = generateInlinedSchema(clazz);
        if (schemaJson == null) {
            return null;
        }
        return com.google.genai.types.Schema.fromJson(schemaJson);
    }

    private static Map<String, Object> getDefinitions(Map<String, Object> rootSchema) {
        if (rootSchema == null) return new LinkedHashMap<>();
        
        if (rootSchema.containsKey("components")) {
            Map<String, Object> components = (Map<String, Object>) rootSchema.get("components");
            if (components != null && components.containsKey("schemas")) {
                return (Map<String, Object>) components.get("schemas");
            }
        }
        if (rootSchema.containsKey("definitions")) {
            return (Map<String, Object>) rootSchema.get("definitions");
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> inlineSchemaRecursive(Map<String, Object> schema, Map<String, Object> definitions, Set<String> seen) {
        if (schema == null) {
            return null;
        }

        if (schema.containsKey("$ref")) {
            String refPath = (String) schema.get("$ref");
            String refName = refPath.substring(refPath.lastIndexOf('/') + 1);
            
            // Cycle detected! Return a simple object to break the recursion.
            if (seen.contains(refName)) {
                Map<String, Object> cycleBreaker = new LinkedHashMap<>();
                cycleBreaker.put("type", "object");
                cycleBreaker.put("description", "Recursive reference to " + refName);
                return cycleBreaker;
            }
            
            Map<String, Object> definition = (Map<String, Object>) definitions.get(refName);
            if (definition != null) {
                seen.add(refName); // Mark as seen before recursing
                Map<String, Object> result = inlineSchemaRecursive(new LinkedHashMap<>(definition), definitions, seen);
                seen.remove(refName); // Unmark after recursion for this branch is done
                return result;
            }
            return new LinkedHashMap<>(); // Ref not found
        }

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            Map<String, Object> inlinedProperties = new LinkedHashMap<>();
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    inlinedProperties.put(entry.getKey(), inlineSchemaRecursive((Map<String, Object>) entry.getValue(), definitions, seen));
                }
            }
            schema.put("properties", inlinedProperties);
        }

        if (schema.containsKey("items")) {
            schema.put("items", inlineSchemaRecursive((Map<String, Object>) schema.get("items"), definitions, seen));
        }

        return schema;
    }

    /**
     * A simple main method for testing the inlined schema generation.
     */
    public static void main(String[] args) {
        try {
            System.out.println("Generating inlined schema for Tree.class...");
            String schema = generateInlinedSchema(uno.anahata.gemini.functions.schema.test.Tree.class);
            System.out.println(schema);
        } catch (Exception e) {
            log.error("Error generating inlined schema for testing", e);
        }
    }
}
