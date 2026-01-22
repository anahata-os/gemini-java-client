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
import uno.anahata.ai.tools.schema.SchemaProvider;

/**
 * A generic utility class for Jackson-based JSON operations.
 * <p>
 * This class uses the centrally configured {@link ObjectMapper} from
 * {@link SchemaProvider} to ensure consistency in how Java objects are
 * mapped to JSON across the application, especially for tool parameters
 * and responses.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JacksonUtils {

    private static final ObjectMapper MAPPER = SchemaProvider.OBJECT_MAPPER;

    /**
     * Converts any object into its JSON-safe equivalent (Map, List, or Primitive)
     * using the centrally configured ObjectMapper. This ensures that all POJOs
     * are flattened and custom serializers (like ElementHandleModule) are applied.
     *
     * @param o The object to convert.
     * @return The JSON-safe representation of the object.
     */
    public static Object toJsonPrimitives(Object o) {
        if (o == null) {
            return null;
        }
        JsonNode node = MAPPER.valueToTree(o);
        return MAPPER.convertValue(node, Object.class);
    }

    /**
     * Converts a JSON-safe object (Map, List, or Primitive) back into a rich Java object
     * of the specified type.
     *
     * @param <T>    The target type.
     * @param object The JSON-safe object to convert.
     * @param type   The target type.
     * @return An instance of the target type.
     */
    public static <T> T toPojo(Object object, Type type) {
        return convertValue(object, type);
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