/**
 * Provides classes and interfaces for managing stateful resources within the chat context.
 * <p>
 * A "stateful resource" is a resource, like a local file, whose state exists
 * independently of the chat history. This package provides the tools to track such
 * resources, check their status (e.g., whether a file in the context is stale
 * compared to the one on disk), and manage their lifecycle within the conversation.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.context.stateful.StatefulResource}: An interface that
 *       must be implemented by any object returned from a tool that represents a
 *       stateful resource. It provides a contract for a unique resource ID.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.stateful.ResourceTracker}: The main class
 *       that scans the context for stateful resources, checks their on-disk status,
 *       and handles automatic replacement of stale resources.</li>
 *
 *   <li>{@link uno.anahata.gemini.context.stateful.ResourceStatus}: An enum representing
 *       the different states a resource can be in (e.g., VALID, STALE, DELETED).</li>
 * </ul>
 */
package uno.anahata.ai.context.stateful;
