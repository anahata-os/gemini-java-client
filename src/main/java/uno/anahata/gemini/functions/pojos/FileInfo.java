package uno.anahata.gemini.functions.pojos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uno.anahata.gemini.context.StatefulResource;

/**
 * A POJO to hold file information, including content and metadata.
 * This object is used for context-aware file operations.
 * @author AI
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A POJO holding file metadata and content, used for context-aware file operations.")
public class FileInfo implements StatefulResource {
    @Schema(description = "The absolute path to the file.")
    public String path;

    @Schema(description = "The text content of the file.")
    public String content;

    @Schema(description = "The last modified timestamp of the file, in milliseconds since the epoch.")
    public long lastModified;

    @Schema(description = "The size of the file in bytes.")
    public long size;

    @Override
    public String toString() {
        return "FileInfo{" +
               "path='" + path + '\'' +
               ", lastModified=" + lastModified +
               ", size=" + size +
               ", content.length=" + (content != null ? content.length() : 0) +
               '}';
    }

    @Override
    public String getResourceId() {
        return path;
    }

}
