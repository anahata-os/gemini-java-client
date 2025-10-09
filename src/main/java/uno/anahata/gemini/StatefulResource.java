package uno.anahata.gemini;

/**
 * An interface for objects that represent a stateful resource in the context,
 * allowing the ContextManager to identify and manage them without reflection.
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
}
