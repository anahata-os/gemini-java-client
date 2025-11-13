package uno.anahata.gemini.functions.schema;

import com.google.genai.types.Type.Known;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * A custom Schema DTO that extends the functionality of the Google GenAI Schema
 * to include support for $ref and definitions, which are necessary for generating
 * well-formed JSON Schemas for recursive and nested types.
 * 
 * This class is used internally by GeminiSchemaGenerator2.
 */
@Data
@Builder(toBuilder = true)
public class CustomSchema {
    
    private final Known type;
    private final String description;
    
    // Fields for complex objects
    @Singular
    private final Map<String, CustomSchema> properties;
    private final List<String> required;
    
    // Fields for arrays
    private final CustomSchema items;
    
    // Fields for enums
    private final List<String> enum_;
    
    // Fields for constraints
    private final Long minLength;
    private final Long maxLength;
    private final Double minimum;
    private final Double maximum;
    private final String pattern;
    
    // Custom fields for $ref and definitions
    private final String ref;
    private final Map<String, CustomSchema> definitions;
}
