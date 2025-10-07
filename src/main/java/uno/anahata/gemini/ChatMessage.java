package uno.anahata.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingMetadata;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

/**
 * A rich data model representing a single, complete turn or transaction in the chat.
 * This replaces the direct use of {@link Content} as the fundamental unit of context,
 * allowing for stable IDs, explicit linking of calls and responses, and rich metadata.
 * @author Anahata
 */
@Getter
public class ChatMessage {
    /**
     * A stable, unique identifier for this message, generated upon creation.
     */
    private final String id;

    /**
     * The ID of the model that generated this message (e.g., "gemini-2.5-pro").
     */
    private final String modelId;

    /**
     * The core content of the turn (e.g., the user's text or the model's response part).
     */
    private final Content content;

    /**
     * A map linking a FunctionCall ID to its corresponding FunctionResponse.
     * This makes the relationship between a tool call and its result explicit.
     */
    private final Map<String, FunctionResponse> functionResponses;

    /**
     * Usage metadata from the API, including token counts. Essential for the context heatmap.
     */
    private final GenerateContentResponseUsageMetadata usageMetadata;

    /**
     * Grounding metadata from the API, containing citations for search-grounded responses.
     */
    private final GroundingMetadata groundingMetadata;

    public ChatMessage(String modelId, Content content, Map<String, FunctionResponse> functionResponses,
                       GenerateContentResponseUsageMetadata usageMetadata, GroundingMetadata groundingMetadata) {
        this.id = UUID.randomUUID().toString();
        this.modelId = modelId;
        this.content = content;
        this.functionResponses = functionResponses;
        this.usageMetadata = usageMetadata;
        this.groundingMetadata = groundingMetadata;
    }
}
