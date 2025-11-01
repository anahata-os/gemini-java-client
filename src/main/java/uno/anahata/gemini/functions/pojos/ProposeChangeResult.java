package uno.anahata.gemini.functions.pojos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uno.anahata.gemini.context.StatefulResource;

/**
 * Represents the result of a proposeChange operation, indicating whether the user
 * accepted or cancelled the change, and including the updated file information if accepted.
 *
 * @author Anahata
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Represents the result of a proposeChange operation, indicating the user's action and providing the updated file details if accepted.")
public class ProposeChangeResult implements StatefulResource {

    public enum Status {
        /** The user accepted the proposed file change. */
        ACCEPTED,
        /** The user cancelled the operation. */
        CANCELLED,
        /** An error occurred during the operation. */
        ERROR
    }

    /** The outcome of the operation. */
    @Schema(description = "The final outcome of the operation, indicating whether the user accepted or cancelled the change.", required = true)
    private Status status;

    /** A descriptive message for the user. */
    @Schema(description = "A descriptive message detailing the result, including any user comments or error details.", required = true)
    private String message;

    /** The updated file information, only present if the status is ACCEPTED. */
    @Schema(description = "The updated file information, which is present only if the status is 'ACCEPTED'.")
    private FileInfo fileInfo;

    /**
     * Gets the resource ID from the nested FileInfo object.
     * This allows the ContextManager to treat this result as a stateful resource
     * only when a file was actually modified.
     *
     * @return The file path if the change was accepted, otherwise null.
     */
    @Override
    public String getResourceId() {
        return (fileInfo != null) ? fileInfo.getResourceId() : null;
    }

    @Override
    public long getLastModified() {
        return (fileInfo != null) ? fileInfo.getLastModified() : 0;
    }

    @Override
    public long getSize() {
        return (fileInfo != null) ? fileInfo.getSize() : 0;
    }

    @Override
    public String toString() {
        return "ProposeChangeResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", fileInfo=" + (fileInfo != null ? fileInfo.toString() : "null") +
                '}';
    }
}
