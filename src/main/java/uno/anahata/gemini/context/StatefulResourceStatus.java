package uno.anahata.gemini.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data class that holds the complete status of a stateful resource,
 * comparing its state in the chat context versus its state on the physical disk.
 *
 * @author Anahata
 */
@Getter
@AllArgsConstructor
public class StatefulResourceStatus {

    /** The unique identifier of the resource (e.g., absolute file path). */
    public final String resourceId;

    /** The last modified timestamp of the resource as recorded in the context. */
    public final long contextLastModified;

    /** The size of the resource as recorded in the context. */
    public final long contextSize;

    /** The current last modified timestamp of the resource on disk. */
    public final long diskLastModified;

    /** The current size of the resource on disk. */
    public final long diskSize;

    /** The calculated status comparing the context and disk states. */
    public final ResourceStatus status;
    
    /** A transient reference to the actual resource object from the context. */
    public final transient StatefulResource resource;
}
