/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.stateful;

/**
 * Represents the status of a stateful resource in the context, indicating
 * whether the version in memory is valid, stale, or has been deleted from disk.
 *
 * @author Anahata
 */
public enum ResourceStatus {
    /** The resource exists on disk but has not been loaded into the context. */
    NOT_IN_CONTEXT,
    /** The resource in the context is identical to the one on disk. */
    VALID,
    /** The resource on disk has been modified more recently than the one in the context. */
    STALE,
    /** The resource has been deleted from the disk. */
    DELETED,
    /** The resource on disk is older than the one in the context (should be rare). */
    OLDER,
    /** An error occurred while checking the status of the resource on disk. */
    ERROR
}