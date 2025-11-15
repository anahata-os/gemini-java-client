package uno.anahata.gemini.functions.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SchemaProvider {

    private static final ObjectMapper OBJECT_MAPPER = createConfiguredObjectMapper();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ObjectMapper createConfiguredObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        return mapper;
    }

    public static String generateStandardSchema(Type type) throws JsonProcessingException {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new ModelResolver(OBJECT_MAPPER));

        Map<String, Schema> swaggerSchemas = converters.readAll(new AnnotatedType(type));
        if (swaggerSchemas.isEmpty()) return null;

        // This is the main enrichment step.
        postProcessSchemas(type, swaggerSchemas);

        String rootSchemaName = findRootSchemaName(swaggerSchemas, type);
        Schema rootSchema = swaggerSchemas.get(rootSchemaName);
        
        // Handle the case where the root is a List
        if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(List.class)) {
            Schema listSchema = new Schema().type("array");
            listSchema.setTitle(getTypeName(type));
            String refName = findRootSchemaName(swaggerSchemas, ((ParameterizedType) type).getActualTypeArguments()[0]);
            listSchema.setItems(new Schema().$ref("#/components/schemas/" + refName));
            rootSchema = listSchema;
        } else {
            // Ensure the root schema also has a title if it's a direct object
            if (rootSchema != null && rootSchema.getTitle() == null) {
                 rootSchema.setTitle(getTypeName(type));
            }
        }

        Map<String, Object> finalSchemaMap = new LinkedHashMap<>(OBJECT_MAPPER.convertValue(rootSchema, Map.class));
        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> componentSchemas = new LinkedHashMap<>();
        swaggerSchemas.forEach((key, schema) -> componentSchemas.put(key, OBJECT_MAPPER.convertValue(schema, Map.class)));
        
        components.put("schemas", componentSchemas);
        finalSchemaMap.put("components", components);

        return GSON.toJson(finalSchemaMap);
    }

    private static void postProcessSchemas(Type rootType, Map<String, Schema> allSchemas) {
        // Step 1: Discover all types and build a map from simple name to full Type.
        Map<String, Type> discoveredTypes = new HashMap<>();
        findAllTypesRecursive(rootType, discoveredTypes, new HashSet<>());

        // Step 2: Iterate through the generated schemas and enrich them.
        for (Map.Entry<String, Schema> entry : allSchemas.entrySet()) {
            String schemaName = entry.getKey();
            Schema schema = entry.getValue();
            Type type = discoveredTypes.get(schemaName);

            if (type != null) {
                addTitleToSchemaRecursive(schema, type, allSchemas, new HashSet<>());
            }
        }
    }

    private static void findAllTypesRecursive(Type type, Map<String, Type> foundTypes, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }

        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (!isJdkClass(clazz)) {
                foundTypes.put(clazz.getSimpleName(), clazz);
                for (Field field : getAllFields(clazz)) {
                    findAllTypesRecursive(field.getGenericType(), foundTypes, visited);
                }
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            findAllTypesRecursive(pt.getRawType(), foundTypes, visited);
            for (Type arg : pt.getActualTypeArguments()) {
                findAllTypesRecursive(arg, foundTypes, visited);
            }
        } else if (type instanceof WildcardType) {
            WildcardType wt = (WildcardType) type;
            for (Type bound : wt.getUpperBounds()) {
                findAllTypesRecursive(bound, foundTypes, visited);
            }
            for (Type bound : wt.getLowerBounds()) {
                findAllTypesRecursive(bound, foundTypes, visited);
            }
        } else if (type instanceof TypeVariable) {
            // In this context, we can't resolve type variables further without more info.
        }
    }

    private static void addTitleToSchemaRecursive(Schema schema, Type type, Map<String, Schema> allSchemas, Set<Type> visited) {
        if (schema == null || type == null || !visited.add(type)) {
            return; // Base case for recursion or cycle detected
        }

        // Ensure the schema itself has a title
        if (schema.getTitle() == null) {
            schema.setTitle(getTypeName(type));
        }

        Class<?> rawClass = getRawClass(type);
        if (rawClass == null || isJdkClass(rawClass)) {
            visited.remove(type);
            return;
        }

        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> propEntry : (Set<Map.Entry<String, Schema>>) schema.getProperties().entrySet()) {
                try {
                    Field field = findField(rawClass, propEntry.getKey());
                    if (field != null) {
                        Schema propSchema = propEntry.getValue();
                        Type fieldType = field.getGenericType();
                        
                        if (propSchema.get$ref() != null) {
                            String refName = propSchema.get$ref().substring(propSchema.get$ref().lastIndexOf('/') + 1);
                            Schema refSchema = allSchemas.get(refName);
                            addTitleToSchemaRecursive(refSchema, fieldType, allSchemas, visited);
                        } else if ("array".equals(propSchema.getType()) && propSchema.getItems() != null) {
                             propSchema.setTitle(getTypeName(fieldType));
                             if (fieldType instanceof ParameterizedType) {
                                 Type itemType = ((ParameterizedType) fieldType).getActualTypeArguments()[0];
                                 addTitleToSchemaRecursive(propSchema.getItems(), itemType, allSchemas, visited);
                             }
                        } else {
                            propSchema.setTitle(getTypeName(fieldType));
                        }
                    }
                } catch (Exception e) {
                    // Ignore and continue
                }
            }
        }
        visited.remove(type);
    }

    public static String generateInlinedSchemaString(Type type) throws JsonProcessingException {
        if (type == null || type.equals(void.class) || type.equals(Void.class)) return null;
        
        String standardSchemaJson = generateStandardSchema(type);
        if (standardSchemaJson == null) return null;

        JsonNode rootNode = OBJECT_MAPPER.readTree(standardSchemaJson);
        JsonNode definitions = rootNode.path("components").path("schemas");

        JsonNode inlinedNode = inlineDefinitionsRecursive(rootNode, definitions, new HashSet<>());
        
        if (inlinedNode instanceof ObjectNode) {
            ((ObjectNode) inlinedNode).remove("components");
        }
        
        return GSON.toJson(OBJECT_MAPPER.treeToValue(inlinedNode, Map.class));
    }

    private static JsonNode inlineDefinitionsRecursive(JsonNode currentNode, JsonNode definitions, Set<String> visitedRefs) {
        if (currentNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) currentNode.deepCopy();
            if (objectNode.has("$ref")) {
                String refPath = objectNode.get("$ref").asText();
                
                if (visitedRefs.contains(refPath)) {
                    String refName = refPath.substring(refPath.lastIndexOf('/') + 1);
                    JsonNode definition = definitions.path(refName);

                    String fqn = definition.has("title") ? definition.get("title").asText() : "N/A";
                    String originalDescription = definition.has("description") ? definition.get("description").asText() : "";

                    String newDescription = "Recursive reference to " + fqn;
                    if (!originalDescription.isEmpty()) {
                        newDescription += ". " + originalDescription;
                    }
                    
                    ObjectNode recursiveNode = OBJECT_MAPPER.createObjectNode();
                    recursiveNode.put("type", "object");
                    recursiveNode.put("title", fqn);
                    recursiveNode.put("description", newDescription);
                    return recursiveNode;
                }
                
                visitedRefs.add(refPath);
                String refName = refPath.substring(refPath.lastIndexOf('/') + 1);
                JsonNode definition = definitions.path(refName);
                // Merge properties from the definition into the current node
                ObjectNode mergedNode = objectNode.deepCopy();
                mergedNode.remove("$ref");
                if (definition.isObject()) {
                    ((ObjectNode)definition).fields().forEachRemaining(entry -> {
                        if (!mergedNode.has(entry.getKey())) {
                            mergedNode.set(entry.getKey(), entry.getValue());
                        }
                    });
                }
                JsonNode result = inlineDefinitionsRecursive(mergedNode, definitions, visitedRefs);
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

    private static String findRootSchemaName(Map<String, Schema> schemas, Type type) {
        String preferredName = getRawClass(type) != null ? getRawClass(type).getSimpleName() : type.getTypeName();
        if (schemas.size() == 1) return schemas.keySet().iterator().next();
        if (schemas.containsKey(preferredName) && schemas.get(preferredName).get$ref() == null) return preferredName;
        
        // Fallback: find the first schema that is not just a reference
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            if (entry.getValue().get$ref() == null) return entry.getKey();
        }
        return preferredName; // Should not be reached if there's at least one concrete schema
    }
    
    private static String getTypeName(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).getCanonicalName();
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            String rawTypeName = getTypeName(pt.getRawType());
            String args = Arrays.stream(pt.getActualTypeArguments())
                                .map(SchemaProvider::getTypeName)
                                .collect(Collectors.joining(", "));
            return rawTypeName + "<" + args + ">";
        }
        return type.getTypeName();
    }
    
    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // Not in this class, try superclass
            }
            current = current.getSuperclass();
        }
        return null;
    }
    
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
    
    private static Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return null;
    }
    
    private static boolean isJdkClass(Class<?> clazz) {
        return clazz.isPrimitive() || clazz.getPackage() == null || clazz.getPackage().getName().startsWith("java.");
    }
}