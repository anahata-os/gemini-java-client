package uno.anahata.gemini.functions.pojos;

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
    private Status status;

    /** A descriptive message for the user. */
    private String message;

    /** The updated file information, only present if the status is ACCEPTED. */
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
