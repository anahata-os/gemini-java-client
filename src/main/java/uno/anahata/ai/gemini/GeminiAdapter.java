/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.internal.JacksonUtils;
import uno.anahata.ai.tools.schema.SchemaProvider;

/**
 * A utility class for adapting Java types and objects to the Gemini API's data structures.
 * <p>
 * This class provides methods to:
 * <ul>
 *   <li>Generate {@link Schema} objects from Java {@link java.lang.reflect.Type}s.</li>
 *   <li>Extract tool call IDs from {@link Part} objects.</li>
 *   <li>Prepare content for the API (purifying POJOs).</li>
 * </ul>
 * </p>
 */
@Slf4j
public class GeminiAdapter {

    private static final Gson GSON = new Gson();
    private static final Map<Class<?>, Type.Known> PRIMITIVE_MAP = new HashMap<>();
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
     * Generates a Gemini {@link Schema} for a given Java type.
     *
     * @param type The Java type to convert.
     * @return The corresponding Gemini Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(java.lang.reflect.Type type) throws Exception {
        return getGeminiSchema(type, false);
    }
    
    /**
     * Generates a Gemini {@link Schema} for a given Java type, with an option to include a JSON schema ID.
     *
     * @param type               The Java type to convert.
     * @param includeJsonSchemaId Whether to include a unique ID in the schema.
     * @return The corresponding Gemini Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(java.lang.reflect.Type type, boolean includeJsonSchemaId) throws Exception {
        if (type == null || type.equals(void.class) || type.equals(Void.class)) {
            return VOID_SCHEMA;
        }

        String inlinedSchema = SchemaProvider.generateInlinedSchemaString(type);
        if (inlinedSchema == null) return VOID_SCHEMA;
        
        Map<String, Object> schemaMap = GSON.fromJson(inlinedSchema, new TypeToken<Map<String, Object>>() {}.getType());
        return buildSchemaFromMap(schemaMap);
    }
    
    /**
     * Generates a Gemini {@link Schema} for a given Java class.
     *
     * @param clazz The Java class to convert.
     * @return The corresponding Gemini Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(Class<?> clazz) throws Exception {
        return getGeminiSchema((java.lang.reflect.Type) clazz, false);
    }

    private static Schema buildSchemaFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;

        Schema.Builder builder = Schema.builder();

        String fqn = (String) map.get("title");
        if (fqn == null) {
            fqn = "N/A";
        }
        
        // For now, just use the FQN as the title as requested.
        // The logic can be expanded later to include the JSON ID if needed.
        builder.title(fqn);

        if (map.containsKey("description")) {
            builder.description((String) map.get("description"));
        }

        if (map.containsKey("type")) {
            String typeStr = (String) map.get("type");
            switch (typeStr.toUpperCase()) {
                case "STRING":  builder.type(Type.Known.STRING);  break;
                case "NUMBER":  builder.type(Type.Known.NUMBER);  break;
                case "INTEGER": builder.type(Type.Known.INTEGER); break;
                case "BOOLEAN": builder.type(Type.Known.BOOLEAN); break;
                case "ARRAY":   builder.type(Type.Known.ARRAY);   break;
                case "OBJECT":  builder.type(Type.Known.OBJECT);  break;
            }
        }

        if (map.containsKey("enum")) {
            List<?> rawList = (List<?>) map.get("enum");
            List<String> enumValues = rawList.stream().map(Object::toString).collect(Collectors.toList());
            builder.enum_(enumValues);
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
    
    /**
     * Extracts the tool call ID from a Part, checking both FunctionCall and FunctionResponse.
     *
     * @param part The part to inspect.
     * @return An Optional containing the ID if found, otherwise empty.
     */
    public static Optional<String> getToolCallId(Part part) {
        if (part.functionCall().isPresent()) {
            return part.functionCall().get().id();
        }
        if (part.functionResponse().isPresent()) {
            return part.functionResponse().get().id();
        }
        return Optional.empty();
    }

    /**
     * Prepares a Content object for the Gemini API by ensuring all FunctionCall arguments
     * and FunctionResponse results are converted to pure JSON primitives (Maps, Lists, Primitives).
     * This prevents "greedy" serialization crashes in external mappers.
     *
     * @param content The content to prepare.
     * @return A new Content object with purified parts, or the original if no changes were needed.
     */
    @SuppressWarnings("unchecked")
    public static Content prepareForApi(Content content) {
        if (content == null || !content.parts().isPresent()) {
            return content;
        }

        List<Part> originalParts = content.parts().get();
        List<Part> newParts = new ArrayList<>(originalParts.size());
        boolean wasModified = false;

        for (Part part : originalParts) {
            Part newPart = part;
            if (part.functionCall().isPresent()) {
                FunctionCall fc = part.functionCall().get();
                if (fc.args().isPresent()) {
                    Map<String, Object> safeArgs = (Map<String, Object>) JacksonUtils.toJsonPrimitives(fc.args().get());
                    newPart = part.toBuilder().functionCall(fc.toBuilder().args(safeArgs).build()).build();
                    wasModified = true;
                }
            } else if (part.functionResponse().isPresent()) {
                FunctionResponse fr = part.functionResponse().get();
                if (fr.response().isPresent()) {
                    Map<String, Object> safeResponse = (Map<String, Object>) JacksonUtils.toJsonPrimitives(fr.response().get());
                    newPart = part.toBuilder().functionResponse(fr.toBuilder().response(safeResponse).build()).build();
                    wasModified = true;
                }
            }
            newParts.add(newPart);
        }

        return wasModified ? content.toBuilder().parts(newParts).build() : content;
    }
}