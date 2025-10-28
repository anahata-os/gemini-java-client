
package uno.anahata.gemini.functions.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.Schema;
import com.google.genai.types.Type.Known;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.internal.GsonUtils;

public class GeminiSchemaGenerator2 {
    
    private static final Map<Class<?>, Known> primitiveMap = new HashMap<>();
    static {
        primitiveMap.put(String.class, Known.STRING);
        primitiveMap.put(Integer.class, Known.INTEGER);
        primitiveMap.put(int.class, Known.INTEGER);
        primitiveMap.put(Long.class, Known.INTEGER);
        primitiveMap.put(long.class, Known.INTEGER);
        primitiveMap.put(Float.class, Known.NUMBER);
        primitiveMap.put(float.class, Known.NUMBER);
        primitiveMap.put(Double.class, Known.NUMBER);
        primitiveMap.put(double.class, Known.NUMBER);
        primitiveMap.put(Boolean.class, Known.BOOLEAN);
        primitiveMap.put(boolean.class, Known.BOOLEAN);
    }

    public static Schema generateSchema(Class<?> clazz, String description) {
        return generateSchemaInternal(clazz, new HashSet<>(), description);
    }
    
    public static String generateSchemaAsString(Class<?> clazz) {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            return null;
        }
        
        if (primitiveMap.containsKey(clazz)) {
            return "Returns a " + clazz.getSimpleName();
        }
        
        if (clazz == Class.class) {
            return "Returns an object of type java.lang.Class";
        }
        
        Schema schema = generateSchema(clazz, "Schema for " + clazz.getSimpleName());
        return GsonUtils.prettyPrint(schema);
    }

    private static Schema generateSchemaInternal(Type type, Set<Type> seen, String description) {
        if (seen.contains(type)) {
            return Schema.builder().type(Known.OBJECT).build(); // break cycles
        }
        seen.add(type);

        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            if (primitiveMap.containsKey(clazz)) {
                return Schema.builder().type(primitiveMap.get(clazz)).description(description).build();
            }

            if (clazz.isEnum()) {
                List<String> values = new ArrayList<>();
                for (Object c : clazz.getEnumConstants()) {
                    values.add(c.toString());
                }
                return Schema.builder()
                        .type(Known.STRING)
                        .enum_(values)
                        .description(description)
                        .build();
            }

            if (clazz.isArray()) {
                Schema itemSchema = generateSchemaInternal(clazz.getComponentType(), seen, description);
                return Schema.builder()
                        .type(Known.ARRAY)
                        .items(itemSchema)                        
                        .description(description)
                        .build();
            }

            if (Collection.class.isAssignableFrom(clazz)) {
                Schema itemSchema = Schema.builder().type(Known.OBJECT).build();
                return Schema.builder()
                        .type(Known.ARRAY)
                        .items(itemSchema)
                        .description(description)
                        .build();
            }

            if (Map.class.isAssignableFrom(clazz)) {
                return Schema.builder()
                        .type(Known.OBJECT)
                        .properties(Collections.emptyMap())
                        .description(description)
                        .build();
            }

            return buildObjectSchema(clazz, seen, description);
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type raw = pt.getRawType();
            if (raw instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) raw;
                if (Collection.class.isAssignableFrom(rawClass)) {
                    Schema items = generateSchemaInternal(pt.getActualTypeArguments()[0], seen, description);
                    return Schema.builder().type(Known.ARRAY).items(items).build();
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    return Schema.builder()
                            .type(Known.OBJECT)
                            .properties(Collections.emptyMap())
                            .build();
                }
            }
        }

        return Schema.builder().type(Known.OBJECT).build();
    }

    private static Schema buildObjectSchema(Class<?> clazz, Set<Type> seen, String description) {
        Map<String, Schema> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        String objectDescription = description;
        if (clazz.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema.class)) {
            io.swagger.v3.oas.annotations.media.Schema classSchema = clazz.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
            if (classSchema.description() != null && !classSchema.description().isEmpty()) {
                objectDescription = classSchema.description();
            }
        }

        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            
            String fieldName = f.getName();
            String fieldDescription = f.getName();
            boolean isRequired = false;

            if (f.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema.class)) {
                io.swagger.v3.oas.annotations.media.Schema fieldSchemaAnnotation = f.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
                if (fieldSchemaAnnotation.description() != null && !fieldSchemaAnnotation.description().isEmpty()) {
                    fieldDescription = fieldSchemaAnnotation.description();
                }
                if (fieldSchemaAnnotation.required()) {
                    isRequired = true;
                }
            }

            Schema fieldSchema = generateSchemaInternal(f.getGenericType(), seen, fieldDescription);
            Schema.Builder fieldBuilder = fieldSchema.toBuilder();

            JsonProperty jsonProp = f.getAnnotation(JsonProperty.class);
            if (jsonProp != null) {
                if (!jsonProp.value().isEmpty()) {
                    fieldName = jsonProp.value();
                }
                if (jsonProp.required()) {
                    isRequired = true;
                }
            }
            
            if (isRequired && !required.contains(fieldName)) {
                required.add(fieldName);
            }

            for (Annotation ann : f.getAnnotations()) {
                applyAnnotation(fieldBuilder, ann, required, fieldName);
            }

            props.put(fieldName, fieldBuilder.build());
        }

        Schema.Builder builder = Schema.builder()
                .type(Known.OBJECT)
                .description(objectDescription)
                .properties(props);
        if (!required.isEmpty()) {
            builder.required(required);
        }
        return builder.build();
    }

    private static void applyAnnotation(Schema.Builder builder, Annotation ann, List<String> required, String fieldName) {
        if (ann instanceof NotNull) {
            if (!required.contains(fieldName)) required.add(fieldName);
        } else if (ann instanceof Size) {
            Size s = (Size) ann;
            if (s.min() > 0) builder.minLength((long)s.min());
            if (s.max() < Integer.MAX_VALUE) builder.maxLength((long)s.max());
        } else if (ann instanceof Min) {
            builder.minimum((double)((Min) ann).value());
        } else if (ann instanceof Max) {
            builder.maximum((double)((Max) ann).value());
        } else if (ann instanceof Pattern) {
            builder.pattern(((Pattern) ann).regexp());
        }
    }
}
