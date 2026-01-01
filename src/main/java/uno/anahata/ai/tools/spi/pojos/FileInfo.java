/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi.pojos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import uno.anahata.ai.context.stateful.StatefulResource;

/**
 * A POJO that encapsulates file content and metadata for context-aware operations.
 * <p>
 * This class implements {@link StatefulResource}, allowing it to be tracked
 * by the {@link uno.anahata.ai.context.stateful.ResourceTracker} to maintain
 * an accurate view of the local file system within the conversation context.
 * </p>
 * 
 * @author AI
 */
@Getter
@AllArgsConstructor
@Schema(description = "A POJO holding file metadata and content, used for context-aware file operations.")
public class FileInfo implements StatefulResource {
    
    /** The absolute path to the file on the local file system. */
    @Schema(description = "The absolute path to the file.")
    String path;

    /** The full text content of the file. */
    @Schema(description = "The text content of the file.")
    String content;
    
    /** The total number of lines in the file. */
    @Schema(description = "The total number of lines of this file.")
    long contentLines;

    /** The last modified timestamp of the file in milliseconds since the epoch. */
    @Schema(description = "The last modified timestamp of the file, in milliseconds since the epoch.")
    long lastModified;

    /** The size of the file in bytes. */
    @Schema(description = "The size of the file in bytes.")
    long size;

    /**
     * Constructs a FileInfo object by reading the file at the specified path.
     *
     * @param path The absolute path to the file.
     * @throws IOException if the file cannot be read.
     */
    public FileInfo(String path) throws IOException {
        Path filePath = Paths.get(path);
        this.path = path;
        this.content = Files.readString(filePath);
        this.lastModified = Files.getLastModifiedTime(filePath).toMillis();
        this.size = Files.size(filePath);
        this.contentLines = this.content.lines().count();
    }

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