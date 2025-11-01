package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rich, stateful representation of a single message in the chat history.
 * This is the core data model for the new architecture.
 * @author Anahata
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String id;
    private String modelId;
    private Content content;
    private String parentId;
    private GenerateContentResponseUsageMetadata usageMetadata;
    private GroundingMetadata groundingMetadata;
    private Map<Part, Part> partLinks;

    @Setter
    private List<FunctionResponse> functionResponses;

    public ChatMessage(String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this(UUID.randomUUID().toString(), modelId, content, parentId, usageMetadata, groundingMetadata, null, null);
    }
    
    public ChatMessage(String id, String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this(id, modelId, content, parentId, usageMetadata, groundingMetadata, null, null);
    }
    
    /**
     * Convenience method to find the originating FunctionCall Part for a given FunctionResponse Part.
     * @param responsePart The Part containing the FunctionResponse.
     * @return The Part containing the original FunctionCall, or null if not found.
     */
    public Part getFunctionCallForResponse(Part responsePart) {
        if (partLinks == null || !responsePart.functionResponse().isPresent()) {
            return null;
        }
        return partLinks.get(responsePart);
    }
}
