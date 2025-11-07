/**
 * Provides a Service Provider Interface (SPI) with a collection of default system instruction providers.
 * <p>
 * This package contains a set of concrete implementations of the
 * {@link uno.anahata.gemini.systeminstructions.SystemInstructionProvider} interface. Each provider
 * is responsible for supplying a specific piece of contextual information to the AI model's system
 * prompt. These providers are automatically discovered and can be enabled or disabled by the user.
 *
 * <h2>Core Providers:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.ChatStatusProvider}: Provides real-time
 *       status information about the current chat session, including model ID, latency, and error states.</li>
 *
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.ContextSummaryProvider}: Summarizes the
 *       current state of the conversation context, including token counts and a list of messages.</li>
 *
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.CoreSystemInstructionsMdFileProvider}: Loads
 *       the main system instructions from an embedded Markdown file.</li>
 *
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.StatefulResourcesProvider}: Lists all
 *       stateful resources currently tracked in the context and their on-disk validation status.</li>
 *
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.SystemPropertiesProvider}: Supplies the
 *       model with the Java system properties of the host JVM.</li>
 *
 *   <li>{@link uno.anahata.gemini.systeminstructions.spi.EnvironmentVariablesProvider}: Supplies the
 *       model with the environment variables of the host system.</li>
 * </ul>
 */
package uno.anahata.gemini.systeminstructions.spi;
