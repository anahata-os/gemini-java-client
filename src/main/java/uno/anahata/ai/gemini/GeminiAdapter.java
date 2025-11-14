package uno.anahata.ai.gemini;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import uno.anahata.gemini.functions.schema.SchemaProvider;

/**
 * Adapts the model-agnostic JSON schema from SchemaProvider into formats
 * specific to the Google Gemini API.
 */
public class GeminiAdapter {

    private static final Gson GSON = new Gson();
    private static final Map<Class<?>, Type.Known> PRIMITIVE_MAP = new HashMap<>();
    
    // A constant for a valid, empty schema for void return types.
    private static final Schema VOID_SCHEMA = Schema.builder().build();

    static {
        PRIMITIVE_MAP.put(String.class, Type.Known.STRING);
        PRIMITIVE_MAP.put(Integer.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(int.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(Long.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(long.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(Float.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(float.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(Double.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(double.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(Boolean.class, Type.Known.BOOLEAN);
        PRIMITIVE_MAP.put(boolean.class, Type.Known.BOOLEAN);
    }

    /**
     * A convenience method that performs the full conversion from a Java class
     * to a Gemini {@link Schema} object, correctly handling the abstract nature
     * of the Schema class and primitive types. This method is guaranteed to never return null.
     *
     * @param clazz The class to generate the schema for.
     * @return The Gemini Schema object. Never null.
     * @throws Exception if schema generation or conversion fails.
     */
    public static Schema getGeminiSchema(Class<?> clazz) throws Exception {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            return VOID_SCHEMA;
        }

        // FIX: Handle primitive/simple types directly, as SchemaProvider doesn't generate schemas for them.
        if (PRIMITIVE_MAP.containsKey(clazz)) {
            return Schema.builder()
                    .type(PRIMITIVE_MAP.get(clazz))
                    .description("Schema for " + clazz.getSimpleName())
                    .build();
        }

        String inlinedSchema = SchemaProvider.generateInlinedSchemaString(clazz);
        if (inlinedSchema == null) {
            // Return the empty schema as a fallback instead of null
            return VOID_SCHEMA;
        }
        
        // 1. Parse the generic JSON string into a Map
        Map<String, Object> schemaMap = GSON.fromJson(inlinedSchema, new TypeToken<Map<String, Object>>() {}.getType());
        
        // 2. Manually build the Schema object from the map
        return buildSchemaFromMap(schemaMap);
    }

    /**
     * Recursively builds a Gemini {@link Schema} object from a map representation of its JSON structure.
     * This method correctly uses the Schema.Builder, avoiding issues with deserializing into abstract classes.
     *
     * @param map The map parsed from the JSON schema.
     * @return A fully constructed Schema object.
     */
    private static Schema buildSchemaFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Schema.Builder builder = Schema.builder();

        if (map.containsKey("type")) {
            String typeStr = (String) map.get("type");
            // Simple mapping from JSON schema types to Gemini's Type.Known enum
            switch (typeStr.toUpperCase()) {
                case "STRING":  builder.type(Type.Known.STRING);  break;
                case "NUMBER":  builder.type(Type.Known.NUMBER);  break;
                case "INTEGER": builder.type(Type.Known.INTEGER); break;
                case "BOOLEAN": builder.type(Type.Known.BOOLEAN); break;
                case "ARRAY":   builder.type(Type.Known.ARRAY);   break;
                case "OBJECT":  builder.type(Type.Known.OBJECT);  break;
            }
        }

        if (map.containsKey("description")) {
            builder.description((String) map.get("description"));
        }

        if (map.containsKey("enum")) {
            builder.enum_((List<String>) map.get("enum"));
        }

        if (map.containsKey("properties")) {
            Map<String, Map<String, Object>> propertiesMap = (Map<String, Map<String, Object>>) map.get("properties");
            Map<String, Schema> schemaProperties = propertiesMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> buildSchemaFromMap(e.getValue())));
            builder.properties(schemaProperties);
        }

        if (map.containsKey("required")) {
            builder.required((List<String>) map.get("required"));
        }

        if (map.containsKey("items")) {
            Schema itemsSchema = buildSchemaFromMap((Map<String, Object>) map.get("items"));
            builder.items(itemsSchema);
        }

        return builder.build();
    }
}