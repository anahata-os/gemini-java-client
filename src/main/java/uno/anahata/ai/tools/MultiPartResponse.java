package uno.anahata.ai.tools;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A special return type for tools that need to send multiple, distinct parts
 * back to the model in a subsequent user message.
 * <p>
 * This is primarily used for tools that generate files (like screenshots or
 * other media) and need to return the file paths to the client application,
 * which will then construct and send the actual file content as new Parts.
 *
 * @author Anahata
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MultiPartResponse {

    /**
     * A list of absolute file paths to the content that should be sent as
     * separate parts in the next user message.
     */
    private List<String> filePaths;
}
