/**
 * Provides a robust, event-driven framework for monitoring and broadcasting the
 * real-time operational status of the chat application.
 * <p>
 * <h2>Architecture</h2>
 * This package implements a classic observer pattern to decouple status reporting
 * from the application's core logic, which is essential for building a responsive
 * user interface.
 * <ul>
 *     <li>
 *         <b>{@link uno.anahata.ai.status.StatusManager}</b>: The central
 *         coordinator and state machine. It tracks the current
 *         {@link uno.anahata.ai.status.ChatStatus}, manages a list of listeners,
 *         and records detailed diagnostic information, including a history of API
 *         errors ({@link uno.anahata.ai.status.ApiExceptionRecord}) and
 *         performance timings.
 *     </li>
 *     <li>
 *         <b>{@link uno.anahata.ai.status.ChatStatus}</b>: A core {@code enum}
 *         that defines the finite set of possible application states (e.g.,
 *         {@code IDLE_WAITING_FOR_USER}, {@code API_CALL_IN_PROGRESS}).
 *     </li>
 *     <li>
 *         <b>{@link uno.anahata.ai.status.StatusListener}</b>: A simple
 *         {@code FunctionalInterface} that allows other components (like the UI)
 *         to subscribe to status changes.
 *     </li>
 *     <li>
 *         <b>{@link uno.anahata.ai.status.ChatStatusEvent}</b>: A rich event
 *         object that provides listeners with detailed context about each status
 *         transition, including the old and new states and any associated
 *         exceptions.
 *     </li>
 * </ul>
 */
package uno.anahata.ai.status;
