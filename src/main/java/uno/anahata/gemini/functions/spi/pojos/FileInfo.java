package uno.anahata.gemini.functions.spi.pojos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import uno.anahata.gemini.context.stateful.StatefulResource;

/**
 * A POJO to hold file information, including content and metadata.
 * This object is used for context-aware file operations.
 * @author AI
 */
//@Value
@Getter
@AllArgsConstructor
@Schema(description = "A POJO holding file metadata and content, used for context-aware file operations.")
public class FileInfo implements StatefulResource {
    @Schema(description = "The absolute path to the file.")
    String path;

    @Schema(description = "The text content of the file.")
    String content;
    
    @Schema(description = "The total number of lines of this file.")
    long contentLines;

    @Schema(description = "The last modified timestamp of the file, in milliseconds since the epoch.")
    long lastModified;

    @Schema(description = "The size of the file in bytes.")
    long size;

    @Override
    public String toString() {
        return "FileInfo{" +
               "path='" + path + '\'' +
               ", lastModified=" + lastModified +
               ", size=" + size +
               ", content.length=" + (content != null ? content.length() : 0) +
                ", contentLines=" + contentLines +
               '}';
    }

    @Override
    public String getResourceId() {
        return path;
    }

}
