package uno.anahata.gemini.context.stateful;

/**
 * An interface for objects that represent a stateful resource in the context,
 * allowing the ContextManager to identify and manage them.
 * This provides a compile-time safe contract for resource identification.
 *
 * @author Anahata
 */
public interface StatefulResource {

    /**
     * Gets the unique identifier for this resource.
     * For files, this would be the absolute path. For other resources, it could be a URL or a database ID.
     *
     * @return The unique, non-null identifier for the resource.
     */
    String getResourceId();
    
    /**
     * Gets the last modified timestamp of the resource.
     *
     * @return The last modified time in milliseconds since the epoch.
     */
    long getLastModified();

    /**
     * Gets the size of the resource.
     *
     * @return The size in bytes.
     */
    long getSize();
}
