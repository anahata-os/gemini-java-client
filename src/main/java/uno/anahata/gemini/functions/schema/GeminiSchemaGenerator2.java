package uno.anahata.gemini.functions.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.Type.Known;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import uno.anahata.gemini.internal.GsonUtils;

/**
 * Generates a JSON Schema for a Java class, including nested object definitions
 * and using $ref for recursive or repeated types to produce a well-formed schema.
 */
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

    /**
     * Public entrypoint for generating a standard JSON Schema with definitions and $ref.
     * Used for system instructions where a complete schema is desired.
     */
    public static String generateSchemaAsString(Class<?> clazz, String description) {
        CustomSchema finalSchema = generateSchemaWithDefinitions(clazz, description);
        return GsonUtils.prettyPrint(finalSchema);
    }
    
    /**
     * Public entrypoint for generating a flat, inlined schema compatible with the Gemini API's
     * strict tool declaration requirements.
     */
    public static com.google.genai.types.Schema generateInlinedSchema(Class<?> clazz, String description) {
        CustomSchema schemaWithDefs = generateSchemaWithDefinitions(clazz, description);
        
        // Recursively inline all definitions into the main schema
        CustomSchema inlinedSchema = inlineDefinitionsRecursive(schemaWithDefs, new HashSet<>());
        
        // Convert the final CustomSchema back to the official Gemini Schema DTO
        return convertToOfficialSchema(inlinedSchema);
    }
    
    private static CustomSchema generateSchemaWithDefinitions(Class<?> clazz, String description) {
        Map<String, CustomSchema> definitions = new LinkedHashMap<>();
        CustomSchema mainSchema = generateSchemaInternal(clazz, definitions, new HashSet<>(), description);
        
        if (mainSchema.getRef() != null) {
            String refName = mainSchema.getRef().substring("#/definitions/".length());
            mainSchema = definitions.get(refName);
            definitions.remove(refName);
        }
        
        CustomSchema.CustomSchemaBuilder finalBuilder = mainSchema.toBuilder();
        if (!definitions.isEmpty()) {
            finalBuilder.definitions(definitions);
        }
        
        return finalBuilder.build();
    }
    
    private static CustomSchema inlineDefinitionsRecursive(CustomSchema schema, Set<String> inliningSeen) {
        if (schema == null) return null;

        // 1. Handle $ref: Replace the reference with the actual inlined schema
        if (schema.getRef() != null) {
            String refName = schema.getRef().substring("#/definitions/".length());
            
            // Cycle detection for inlining: If we are already processing this definition, break the cycle
            if (inliningSeen.contains(refName)) {
                // Return a simple object schema to break the recursion in the final inlined output
                return CustomSchema.builder().type(Known.OBJECT).build();
            }
            
            CustomSchema definition = schema.getDefinitions().get(refName);
            if (definition != null) {
                inliningSeen.add(refName);
                // Recursively inline the definition itself
                CustomSchema inlinedDef = inlineDefinitionsRecursive(definition, inliningSeen);
                inliningSeen.remove(refName);
                return inlinedDef;
            }
        }
        
        // 2. Recursively process properties
        if (schema.getProperties() != null) {
            Map<String, CustomSchema> inlinedProps = new LinkedHashMap<>();
            for (Map.Entry<String, CustomSchema> entry : schema.getProperties().entrySet()) {
                inlinedProps.put(entry.getKey(), inlineDefinitionsRecursive(entry.getValue(), inliningSeen));
            }
            schema = schema.toBuilder().properties(inlinedProps).build();
        }
        
        // 3. Recursively process array items
        if (schema.getItems() != null) {
            schema = schema.toBuilder().items(inlineDefinitionsRecursive(schema.getItems(), inliningSeen)).build();
        }
        
        // 4. Remove definitions and ref fields from the final inlined schema
        return schema.toBuilder().definitions(null).ref(null).build();
    }
    
    private static com.google.genai.types.Schema convertToOfficialSchema(CustomSchema customSchema) {
        if (customSchema == null) return null;
        
        Map<String, com.google.genai.types.Schema> officialProps = null;
        if (customSchema.getProperties() != null) {
            officialProps = new LinkedHashMap<>();
            for (Map.Entry<String, CustomSchema> entry : customSchema.getProperties().entrySet()) {
                officialProps.put(entry.getKey(), convertToOfficialSchema(entry.getValue()));
            }
        }
        
        com.google.genai.types.Schema officialItems = convertToOfficialSchema(customSchema.getItems());
        
        return com.google.genai.types.Schema.builder()
                .type(customSchema.getType())
                .description(customSchema.getDescription())
                .properties(officialProps)
                .required(customSchema.getRequired())
                .items(officialItems)
                .enum_(customSchema.getEnum_())
                .minLength(customSchema.getMinLength())
                .maxLength(customSchema.getMaxLength())
                .minimum(customSchema.getMinimum())
                .maximum(customSchema.getMaximum()) // FIX: Use getMaximum()
                .pattern(customSchema.getPattern())
                .build();
    }

    /**
     * Generates a schema for a given type. If the type is a complex object,
     * it adds the full schema to the definitions map and returns a $ref schema.
     * @return The schema for the type, which is a $ref for complex objects.
     */
    private static CustomSchema generateSchemaInternal(Type type, Map<String, CustomSchema> definitions, Set<Type> seen, String description) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            // Primitive / boxed / string
            if (primitiveMap.containsKey(clazz)) {
                return CustomSchema.builder().type(primitiveMap.get(clazz)).description(description).build();
            }

            // Enum
            if (clazz.isEnum()) {
                List<String> values = new ArrayList<>();
                for (Object c : clazz.getEnumConstants()) {
                    values.add(c.toString());
                }
                return CustomSchema.builder()
                        .type(Known.STRING)
                        .enum_(values)
                        .description(description)
                        .build();
            }

            // Array
            if (clazz.isArray()) {
                CustomSchema itemSchema = generateSchemaInternal(clazz.getComponentType(), definitions, seen, description);
                return CustomSchema.builder()
                        .type(Known.ARRAY)
                        .items(itemSchema)                        
                        .description(description)
                        .build();
            }

            // Collection (List, Set, etc.)
            if (Collection.class.isAssignableFrom(clazz)) {
                // If it's a raw collection (e.g., List), default to Object items
                CustomSchema itemSchema = CustomSchema.builder().type(Known.OBJECT).build();
                return CustomSchema.builder()
                        .type(Known.ARRAY)
                        .items(itemSchema)
                        .description(description)
                        .build();
            }

            // Map<K,V>  Free-form object
            if (Map.class.isAssignableFrom(clazz)) {
                return CustomSchema.builder()
                        .type(Known.OBJECT)
                        .properties(Collections.emptyMap())
                        .description(description)
                        .build();
            }

            // Custom object - Handle with definitions and $ref
            String simpleName = clazz.getSimpleName();
            if (definitions.containsKey(simpleName)) {
                // Cycle detected or already defined, return $ref
                return CustomSchema.builder().ref("#/definitions/" + simpleName).build();
            }
            
            // Add a placeholder to the seen set to break cycles during generation
            // We use the simple name as a key in the definitions map to track progress
            definitions.put(simpleName, null); 
            
            CustomSchema objectSchema = buildObjectSchema(clazz, definitions, seen, description);
            definitions.put(simpleName, objectSchema);
            
            return CustomSchema.builder().ref("#/definitions/" + simpleName).build();
        }

        // ParameterizedType (List<T>, Map<K,V>)
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type raw = pt.getRawType();
            if (raw instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) raw;
                if (Collection.class.isAssignableFrom(rawClass)) {
                    CustomSchema items = generateSchemaInternal(pt.getActualTypeArguments()[0], definitions, seen, description);
                    return CustomSchema.builder().type(Known.ARRAY).items(items).build();
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    // free-form object
                    return CustomSchema.builder()
                            .type(Known.OBJECT)
                            .properties(Collections.emptyMap())
                            .build();
                }
            }
        }

        return CustomSchema.builder().type(Known.OBJECT).build();
    }

    private static CustomSchema buildObjectSchema(Class<?> clazz, Map<String, CustomSchema> definitions, Set<Type> seen, String description) {
        Map<String, CustomSchema> props = new LinkedHashMap<>();
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

            // Recursive call to generate schema for the field's type
            CustomSchema fieldSchema = generateSchemaInternal(f.getGenericType(), definitions, seen, fieldDescription);
            CustomSchema.CustomSchemaBuilder fieldBuilder = fieldSchema.toBuilder();
            
            // CRITICAL FIX: Ensure the description is applied to the final schema builder for the field.
            fieldBuilder.description(fieldDescription);

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

        CustomSchema.CustomSchemaBuilder builder = CustomSchema.builder()
                .type(Known.OBJECT)
                .description(objectDescription)
                .properties(props);
        if (!required.isEmpty()) {
            builder.required(required);
        }
        return builder.build();
    }

    private static void applyAnnotation(CustomSchema.CustomSchemaBuilder builder, Annotation ann, List<String> required, String fieldName) {
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