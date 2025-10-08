package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import java.util.List;
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

    @Setter
    private List<FunctionResponse> functionResponses;

    public ChatMessage(String modelId, Content content, String parentId, GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this.id = UUID.randomUUID().toString();
        this.modelId = modelId;
        this.content = content;
        this.parentId = parentId;
        this.usageMetadata = usageMetadata;
        this.groundingMetadata = groundingMetadata;
    }
}
