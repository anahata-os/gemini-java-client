package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * A rich, stateful representation of a single message in the chat history.
 * This is the core data model for the new architecture.
 * @author Anahata
 */
@Data
@Builder(toBuilder = true)
public class ChatMessage {

    @Builder.Default
    private final String id = UUID.randomUUID().toString();
    private final String modelId;
    private final Content content;
    private final String parentId;
    private final GenerateContentResponseUsageMetadata usageMetadata;
    private final GroundingMetadata groundingMetadata;
    private final Map<Part, Part> partLinks;
    
    @Builder.Default
    private final Instant createdOn = Instant.now();

    // This field is mutable and is set after the model's response is received.
    private List<FunctionResponse> functionResponses;

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
