package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part; // Import Part
import java.util.List;
import java.util.Map; // Import Map
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A rich, stateful representation of a single message in the chat history.
 * This is the core data model for the new architecture.
 * @author Anahata
 */
@Getter
public class ChatMessage {

    private final String id;
    private final String modelId;
    private final Content content;
    private final String parentId;
    private final GenerateContentResponseUsageMetadata usageMetadata;
    private final GroundingMetadata groundingMetadata;

    // NEW FIELD: Stores links from a Part in this message to a Part in another.
    // For a "tool" message, this maps a FunctionResponse Part to its FunctionCall Part.
    private final Map<Part, Part> partLinks;

    @Setter
    private List<FunctionResponse> functionResponses;

    public ChatMessage(String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this(UUID.randomUUID().toString(), modelId, content, parentId, usageMetadata, groundingMetadata, null);
    }
    
    // New constructor to preserve ID during modification
    public ChatMessage(String id, String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this(id, modelId, content, parentId, usageMetadata, groundingMetadata, null);
    }

    // NEW CONSTRUCTOR to accept the links
    public ChatMessage(String id, String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata, Map<Part, Part> partLinks) {
        this.id = id;
        this.modelId = modelId;
        this.content = content;
        this.parentId = parentId;
        this.usageMetadata = usageMetadata;
        this.groundingMetadata = groundingMetadata;
        this.partLinks = partLinks;
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
