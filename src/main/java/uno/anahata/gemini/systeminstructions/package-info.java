/**
 * Provides the framework for supplying the AI model with system-level instructions and context.
 * <p>
 * This package contains the {@link uno.anahata.gemini.systeminstructions.SystemInstructionProvider}
 * abstract class, which defines a contract for modules that contribute contextual information
 * to the system prompt. This mechanism allows the application to dynamically inject relevant
 * data, such as the current application state, environment variables, or core operational
 * guidelines, before sending a request to the model.
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi}: Contains a set of default provider
 *       implementations for core contextual information, such as chat status, system properties,
 *       and stateful resource summaries.</li>
 * </ul>
 */
package uno.anahata.gemini.systeminstructions;
