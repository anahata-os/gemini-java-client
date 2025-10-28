package uno.anahata.gemini.functions.pojos;

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
public class FileInfo implements StatefulResource {
    /** The absolute path to the file. */
    public String path;

    /** The text content of the file. */
    public String content;

    /** The last modified timestamp of the file, in milliseconds since the epoch. */
    public long lastModified;

    /** The size of the file in bytes. */
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
