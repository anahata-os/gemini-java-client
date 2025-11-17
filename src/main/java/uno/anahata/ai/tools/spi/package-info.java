/**
 * Provides a Service Provider Interface (SPI) with a collection of pre-built, general-purpose local tools.
 * <p>
 * This package contains a set of concrete tool implementations that are essential for most AI assistant
 * applications. These tools grant the model the ability to interact with the user's local environment
 * in a powerful and controlled way. Developers can extend the system by providing their own tool classes
 * following the patterns established here.
 *
 * <h2>Core Tools:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.functions.spi.LocalFiles}: A comprehensive tool for performing context-aware
 *       file system operations, such as reading, writing, creating, and deleting files.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi.LocalShell}: Enables the execution of arbitrary shell commands,
 *       providing a powerful way for the model to interact with the underlying operating system.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi.RunningJVM}: A sophisticated tool that allows the model to
 *       compile and execute Java code within the host application's running JVM, enabling dynamic extension
 *       and complex, on-the-fly computations.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi.ContextWindow}: Provides meta-tools for managing the conversation
 *       context itself, such as pruning messages or parts to control token usage.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi.Session}: A tool for managing the lifecycle of the chat session,
 *       including saving and loading the conversation history.</li>
 *
 *   <li>{@link uno.anahata.gemini.functions.spi.Images}: A tool for generating images from text prompts using
 *       an image generation model.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.functions.spi.pojos}: Contains Plain Old Java Objects (POJOs) used as
 *       data transfer objects for the tools, such as {@link uno.anahata.gemini.functions.spi.pojos.FileInfo}.</li>
 * </ul>
 */
package uno.anahata.ai.tools.spi;
