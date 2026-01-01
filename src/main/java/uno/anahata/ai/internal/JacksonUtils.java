/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uno.anahata.ai.tools.schema.SchemaProvider2;

/**
 * A generic utility class for Jackson-based JSON operations.
 * <p>
 * This class uses the centrally configured {@link ObjectMapper} from
 * {@link SchemaProvider2} to ensure consistency in how Java objects are
 * mapped to JSON across the application, especially for tool parameters
 * and responses.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JacksonUtils {

    private static final ObjectMapper MAPPER = SchemaProvider2.OBJECT_MAPPER;

    /**
     * Converts an object to a {@code Map<String, Object>}, replicating the logic required by the
     * Gemini {@code FunctionResponse} type.
     * <ul>
     *   <li>If the object naturally serializes to a JSON Object (e.g., a POJO or a Map), it is
     *       converted into a {@code Map<String, Object>}.</li>
     *   <li>If the object serializes to any other JSON type (e.g., an array, a string, a number),
     *       it is wrapped in a Map under the given field name.</li>
     * </ul>
     *
     * @param primitiveFieldName The key to use when wrapping a non-POJO type.
     * @param o The object to convert.
     * @return A Map representation of the object, ready for use in a FunctionResponse.
     */
    public static Map<String, Object> convertObjectToMap(String primitiveFieldName, Object o) {
        if (o == null) {
            return Collections.emptyMap();
        }

        // Use Jackson's tree model to inspect the JSON structure without full serialization.
        JsonNode node = MAPPER.valueToTree(o);

        if (node.isObject()) {
            // It's a POJO or a Map, convert it to the required Map type.
            return MAPPER.convertValue(o, new TypeReference<Map<String, Object>>() {});
        } else {
            // It's a primitive, String, array, or collection. Wrap the original object.
            // The final serialization of the FunctionResponse will correctly handle this structure.
            return Collections.singletonMap(primitiveFieldName, o);
        }
    }
    
    /**
     * Deserializes a {@code Map<String, Object>} back into a specific POJO type.
     *
     * @param <T>   The target type.
     * @param map   The map to convert.
     * @param clazz The class of the target type.
     * @return An instance of the target type, or {@code null} if the input map is null or empty.
     */
    public static <T> T convertMapToObject(Map<String, Object> map, Class<T> clazz) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return MAPPER.convertValue(map, clazz);
    }
    
    /**
     * Deserializes a generic object (typically a Map or List of Maps) back into a specific type,
     * including complex generic types.
     *
     * @param <T>    The target type.
     * @param object The object to convert (typically a Map or List of Maps).
     * @param type   The target type, which can be a {@link Class} or a {@link java.lang.reflect.ParameterizedType}.
     * @return An instance of the target type, or {@code null} if the input object is null.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertValue(Object object, Type type) {
        if (object == null) {
            return null;
        }
        // We use MAPPER.constructType(type) to correctly handle generic types (like List<Note>)
        // and then cast the result. This is safe as Jackson is performing the conversion to the
        // type represented by 'type'.
        return (T) MAPPER.convertValue(object, MAPPER.constructType(type));
    }
}