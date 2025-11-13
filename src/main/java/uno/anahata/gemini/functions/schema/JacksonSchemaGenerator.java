package uno.anahata.gemini.functions.schema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.functions.schema.test.Tree;
import uno.anahata.gemini.functions.spi.pojos.FileInfo;


/**
 * Generates a standard, self-contained JSON Schema string from a given Java class using the Jackson and Swagger libraries.
 * This class is responsible for creating a complete schema document, including the `components` section
 * with all necessary definitions, which is required for subsequent processing.
 */
@Slf4j
public class JacksonSchemaGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a complete, pretty-printed JSON Schema string for a given class,
     * including all component definitions.
     *
     * @param clazz The class to generate the schema for.
     * @return A string containing the complete JSON Schema with a `components` section.
     * @throws Exception if schema generation fails.
     */
    public static String generateSchema(Class<?> clazz) throws Exception {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            return null;
        }
        
        // Use readAll() to get the main schema AND all component schemas (definitions)
        Map<String, Schema> swaggerSchemas = ModelConverters.getInstance().readAll(new AnnotatedType(clazz));
        
        if (swaggerSchemas.isEmpty()) {
            return null;
        }
        
        // The main schema is usually the one with the class's simple name, but we find it dynamically.
        // The root schema is the one that is not just a reference to another schema.
        String rootSchemaName = findRootSchemaName(swaggerSchemas, clazz.getSimpleName());
        Schema rootSchema = swaggerSchemas.get(rootSchemaName);

        // Build the final JSON object that will be serialized
        Map<String, Object> finalSchemaMap = new LinkedHashMap<>();
        
        // Convert the main schema to a map and add its properties to our final map
        Map<String, Object> rootSchemaMap = GSON.fromJson(GSON.toJson(rootSchema), Map.class);
        finalSchemaMap.putAll(rootSchemaMap);

        // Create the "components" and "schemas" sections
        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> componentSchemas = new LinkedHashMap<>();
        
        for (Map.Entry<String, Schema> entry : swaggerSchemas.entrySet()) {
            // Add all schemas (including the root one, as it might be referenced) to the components
            componentSchemas.put(entry.getKey(), GSON.fromJson(GSON.toJson(entry.getValue()), Map.class));
        }
        
        components.put("schemas", componentSchemas);
        finalSchemaMap.put("components", components);
        
        return GSON.toJson(finalSchemaMap);
    }
    
    private static String findRootSchemaName(Map<String, Schema> schemas, String preferredName) {
        if (schemas.size() == 1) {
            return schemas.keySet().iterator().next();
        }
        // The root schema is typically the one that is not a simple $ref.
        // Or, if multiple are complex, the one matching the class name is preferred.
        if (schemas.containsKey(preferredName) && schemas.get(preferredName).get$ref() == null) {
            return preferredName;
        }
        // Fallback: find the first non-ref schema
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            if (entry.getValue().get$ref() == null) {
                return entry.getKey();
            }
        }
        // If all else fails, return the preferred name
        return preferredName;
    }

    /**
     * A simple main method for testing the schema generation.
     */
    public static void main(String[] args) {
        try {
            System.out.println("--- Generating Schema for Tree ---");
            String treeSchema = generateSchema(Tree.class);
            System.out.println(treeSchema);

            System.out.println("\n--- Generating Schema for FileInfo ---");
            String fileInfoSchema = generateSchema(FileInfo.class);
            System.out.println(fileInfoSchema);

        } catch (Exception e) {
            log.error("Error generating schema for testing", e);
        }
    }
}
