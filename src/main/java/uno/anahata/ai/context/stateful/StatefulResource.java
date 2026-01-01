/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.stateful;

/**
 * An interface for objects that represent a stateful resource in the conversation context.
 * <p>
 * Implementing this interface allows the {@link ResourceTracker} to identify,
 * monitor, and manage the lifecycle of the resource (e.g., a local file).
 * </p>
 * <p>
 * This provides a compile-time safe contract for resource identification and
 * staleness detection.
 * </p>
 *
 * @author Anahata
 */
public interface StatefulResource {

    /**
     * Gets the unique identifier for this resource.
     * <p>
     * For files, this is typically the absolute path. For other resources,
     * it could be a URL, a database ID, or any other unique string.
     * </p>
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
     * Gets the size of the resource in bytes.
     *
     * @return The size in bytes.
     */
    long getSize();
}