/**
 * This package provides a robust framework for managing "stateful resources"  local
 * resources, primarily files, whose state can change independently of the chat
 * conversation. It ensures that the AI's view of these resources remains
 * consistent with their real-world state on the disk.
 *
 * <h2>Core Concepts:</h2>
 * <p>
 * The central challenge with using local files in a long-running conversation is
 * that they can be modified or deleted by external processes. This package
 * addresses that by providing a system to track, validate, and automatically
 * manage the lifecycle of these resources within the chat context.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.context.stateful.StatefulResource}: A foundational
 *       interface that acts as a contract for any tool response that represents a
 *       trackable resource. It standardizes access to a unique ID, last modified
 *       timestamp, and size.</li>
 *
 *   <li>{@link uno.anahata.ai.context.stateful.ResourceTracker}: The proactive engine
 *       of this package. It scans the chat history for instances of
 *       {@code StatefulResource}, compares their recorded state against the live
 *       state on the file system, and is responsible for the automatic pruning of
 *       stale resources when a newer version is loaded into the context.</li>
 *
 *   <li>{@link uno.anahata.ai.context.stateful.ResourceStatus}: A simple yet crucial
 *       enum that provides a clear, machine-readable status for each tracked
 *       resource (e.g., {@code VALID}, {@code STALE}, {@code DELETED}).</li>
 *
 *   <li>{@link uno.anahata.ai.context.stateful.StatefulResourceStatus}: A data
 *       transfer object that aggregates all relevant information about a resource's
 *       state, providing a complete snapshot for diagnostics and UI representation.</li>
 * </ul>
 */
package uno.anahata.ai.context.stateful;
