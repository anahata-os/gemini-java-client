/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A special type of object that the tool execution framework recognizes to add blob items to the 'user' message that follows the 'tool' message (and not as byte[] in a tool execution response)")
public class MultiPartResponse {

    /**
     * A list of absolute file paths to the content that should be sent as
     * separate parts in the next user message.
     */
    @Schema(description = "The full file paths of the files to attach to the next user message")
    private List<String> filePaths;
}
