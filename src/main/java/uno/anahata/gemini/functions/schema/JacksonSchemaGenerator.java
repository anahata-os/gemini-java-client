package uno.anahata.gemini.functions.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.functions.spi.pojos.FileInfo;

/**
 * Generates a standard JSON Schema string from a given Java class using the Jackson library.
 * This provides a more robust and annotation-driven way to create rich schemas for the AI model.
 */
@Slf4j
public class JacksonSchemaGenerator {

    /**
     * Generates a pretty-printed JSON Schema string for a given class.
     *
     * @param clazz The class to generate the schema for.
     * @return A string containing the JSON Schema.
     * @throws Exception if schema generation fails.
     */
    public static String generateSchema(Class<?> clazz) throws Exception {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            return null;
        }
        
        ObjectMapper mapper = new ObjectMapper();
        // configure the schema generator
        com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        // generate the schema
        JsonSchema schema = schemaGen.generateSchema(clazz);
        
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
    }
    
    /**
     * A simple main method for testing the schema generation.
     */
    public static void main(String[] args) {
        try {
            String schema = generateSchema(FileInfo.class);
            System.out.println("Generated Schema for FileInfo:");
            System.out.println(schema);
        } catch (Exception e) {
            log.error("Error generating schema for testing", e);
        }
    }
}
