/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * Defines how the output of an @AIToolMethod should be treated within the chat context.
 * This allows for declarative, intelligent context management.
 * @author Anahata
 */
public enum ContextBehavior {
    /**
     * The result is ephemeral and does not represent a lasting state.
     * It is useful for one-off actions or queries. The system may automatically
     * prune these results after a short time to keep the context clean.
     * (Default behavior).
     */
    EPHEMERAL,

    /**
     * The result represents a stateful resource (like a file's content) that
     * should replace any previous version of the same resource in the context.
     * The system will use this to maintain an "Active Workspace" view, ensuring
     * only the latest version of a resource is considered.
     */
    STATEFUL_REPLACE
}