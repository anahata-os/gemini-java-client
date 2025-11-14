package uno.anahata.gemini.functions.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A comprehensive, model-agnostic schema provider that correctly handles complex and recursive object models by
 * leveraging the powerful Jackson and Swagger libraries.
 * <p>
 * This class is designed to produce a standard, flattened JSON Schema string from a Java class. It has no knowledge of
 * any specific AI model's schema types.
 * <p>
 * It implements a two-stage process:
 * <ol>
 *   <li><b>Standard Schema Generation:</b> It uses Jackson and Swagger's `ModelConverters` to generate a high-fidelity
 *       schema. This process fully respects annotations like {@code @Schema}, {@code @JsonProperty}, etc.,
 *       and produces a standard schema with a {@code components/schemas} section and {@code $ref} pointers.</li>
 *   <li><b>Recursive Inlining with Cycle Detection:</b> It then recursively processes the generated schema to "inline" all {@code $ref}
 *       pointers, replacing them with the content of their definitions. It includes cycle detection to prevent infinite
 *       recursion with self-referencing models (e.g., tree structures).</li>
 * </ol>
 */
public class SchemaProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FQN_EXTENSION_KEY = "x-java-fqn";

    /**
     * A custom ModelConverter that 'tags' each generated schema with the fully qualified class name (FQN)
     * using a standard extension property. This is a safe and reliable way to pass metadata through the
     * conversion chain.
     */
    private static class FqnModelConverter implements ModelConverter {
        @Override
        public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
            Schema schema = null;
            if (chain.hasNext()) {
                schema = chain.next().resolve(type, context, chain);
            }

            if (schema != null && type.getType() instanceof Class) {
                Class<?> cls = (Class<?>) type.getType();
                schema.addExtension(FQN_EXTENSION_KEY, cls.getCanonicalName());
            }
            return schema;
        }
    }

    /**
     * Generates a standard, pretty-printed JSON Schema string for a given class, including all component definitions.
     * <p>
     * This method produces the "source of truth" schema. It correctly handles complex and recursive objects by creating
     * a {@code components/schemas} section and using {@code $ref} pointers, making it compatible with standards like OpenAPI.
     *
     * @param clazz The class to generate the schema for.
     * @return A string containing the complete, standards-compliant JSON Schema.
     * @throws JsonProcessingException if schema generation or serialization fails.
     */
    public static String generateStandardSchema(Class<?> clazz) throws JsonProcessingException {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new FqnModelConverter());
        converters.addConverter(new ModelResolver(OBJECT_MAPPER));

        Map<String, Schema> swaggerSchemas = converters.readAll(new AnnotatedType(clazz));
        if (swaggerSchemas.isEmpty()) {
            return null;
        }

        // Post-processing step: Iterate through the generated schemas and use the FQN from the extension
        // to build the final, rich description. This is more reliable than modifying it during resolution.
        for (Schema schema : swaggerSchemas.values()) {
            if (schema.getExtensions() != null && schema.getExtensions().containsKey(FQN_EXTENSION_KEY)) {
                String fqn = (String) schema.getExtensions().get(FQN_EXTENSION_KEY);
                String currentDescription = schema.getDescription();
                // Set the primary description to be the FQN description.
                String newDescription = "Schema for " + fqn;
                if (currentDescription != null && !currentDescription.isEmpty()) {
                    newDescription += "\n" + currentDescription;
                }
                schema.setDescription(newDescription);
            }
        }

        String rootSchemaName = findRootSchemaName(swaggerSchemas, clazz.getSimpleName());
        Schema rootSchema = swaggerSchemas.get(rootSchemaName);

        Map<String, Object> finalSchemaMap = new LinkedHashMap<>();
        finalSchemaMap.putAll(OBJECT_MAPPER.convertValue(rootSchema, Map.class));

        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> componentSchemas = new LinkedHashMap<>();
        for (Map.Entry<String, Schema> entry : swaggerSchemas.entrySet()) {
            componentSchemas.put(entry.getKey(), OBJECT_MAPPER.convertValue(entry.getValue(), Map.class));
        }

        components.put("schemas", componentSchemas);
        finalSchemaMap.put("components", components);

        return GSON.toJson(finalSchemaMap);
    }

    /**
     * Generates a fully "inlined" or "flattened" JSON schema string for a given class.
     * <p>
     * This method takes the standard schema produced by Jackson/Swagger and recursively replaces all {@code $ref} pointers
     * with their corresponding definitions. The resulting schema has no {@code $ref} keywords and is suitable for use
     * with APIs that do not support schema references.
     *
     * @param clazz The class to generate the schema for.
     * @return A string containing the fully inlined JSON Schema, or null if the class is void.
     * @throws JsonProcessingException if schema processing fails.
     */
    public static String generateInlinedSchemaString(Class<?> clazz) throws JsonProcessingException {
        String standardSchemaJson = generateStandardSchema(clazz);
        if (standardSchemaJson == null) return null;

        JsonNode rootNode = OBJECT_MAPPER.readTree(standardSchemaJson);
        JsonNode definitions = rootNode.path("components").path("schemas");

        JsonNode inlinedNode = inlineDefinitionsRecursive(rootNode, definitions, new HashSet<>());
        
        if (inlinedNode instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) inlinedNode;
            objectNode.remove("components");
            
            // DEFINITIVE FIX 1: Ensure the top-level description is always present.
            if (!objectNode.has("description")) {
                 String description = "Schema for " + clazz.getCanonicalName();
                 objectNode.put("description", description);
            }
        }
        
        return GSON.toJson(OBJECT_MAPPER.treeToValue(inlinedNode, Map.class));
    }

    private static JsonNode inlineDefinitionsRecursive(JsonNode currentNode, JsonNode definitions, Set<String> visitedRefs) {
        if (currentNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) currentNode.deepCopy();
            if (objectNode.has("$ref")) {
                String refPath = objectNode.get("$ref").asText();
                
                if (visitedRefs.contains(refPath)) {
                    String defName = refPath.substring(refPath.lastIndexOf('/') + 1);
                    JsonNode definition = definitions.path(defName);
                    
                    // DEFINITIVE FIX: Use the robust x-java-fqn extension, not brittle string parsing.
                    String fqn = "N/A";
                    if (definition.has(FQN_EXTENSION_KEY)) {
                        fqn = definition.get(FQN_EXTENSION_KEY).asText();
                    }
                    
                    String description = String.format("Recursive reference to %s (%s)", fqn, refPath);
                    return OBJECT_MAPPER.createObjectNode().put("type", "object").put("description", description);
                }
                
                visitedRefs.add(refPath);
                
                String defName = refPath.substring(refPath.lastIndexOf('/') + 1);
                JsonNode definition = definitions.path(defName);
                
                JsonNode result = inlineDefinitionsRecursive(definition, definitions, visitedRefs);
                
                visitedRefs.remove(refPath);
                return result;
                
            } else {
                Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    field.setValue(inlineDefinitionsRecursive(field.getValue(), definitions, visitedRefs));
                }
                return objectNode;
            }
        } else if (currentNode.isArray()) {
            List<JsonNode> newItems = new ArrayList<>();
            for (JsonNode item : currentNode) {
                newItems.add(inlineDefinitionsRecursive(item, definitions, visitedRefs));
            }
            return OBJECT_MAPPER.createArrayNode().addAll(newItems);
        }
        return currentNode;
    }

    private static String findRootSchemaName(Map<String, Schema> schemas, String preferredName) {
        if (schemas.size() == 1) {
            return schemas.keySet().iterator().next();
        }
        if (schemas.containsKey(preferredName) && schemas.get(preferredName).get$ref() == null) {
            return preferredName;
        }
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            if (entry.getValue().get$ref() == null) {
                return entry.getKey();
            }
        }
        return preferredName;
    }
}