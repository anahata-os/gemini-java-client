/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

/**
 * Defines how the output of an {@link AIToolMethod} should be treated within
 * the conversation context.
 * <p>
 * This allows for declarative, intelligent context management, ensuring that
 * the model always has the most relevant and up-to-date information without
 * unnecessary bloat.
 * </p>
 * 
 * @author Anahata
 */
public enum ContextBehavior {
    /**
     * The result is ephemeral and does not represent a lasting state.
     * <p>
     * It is useful for one-off actions or queries (e.g., running a shell command).
     * The system automatically prunes these results after 4 user turns to keep
     * the context clean.
     * </p>
     */
    EPHEMERAL,

    /**
     * The result represents a stateful resource (like a file's content) that
     * should replace any previous version of the same resource in the context.
     * <p>
     * The system uses this to maintain an "Active Workspace" view, ensuring
     * only the latest version of a resource is considered and automatically
     * pruning older versions.
     * </p>
     */
    STATEFUL_REPLACE
}
