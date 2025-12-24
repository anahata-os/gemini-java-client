/* Licensed under the Apache License, Version 2.0 */
/**
 * This package contains a set of concrete implementations of the {@link uno.anahata.ai.context.provider.ContextProvider}
 * interface, each designed to inject a specific type of information into the system prompt, creating a rich and
 * dynamic context for the AI.
 *
 * <ul>
 *     <li>{@link uno.anahata.ai.context.provider.spi.CoreSystemInstructionsMdFileProvider}: Loads the foundational
 *     system instructions from an external Markdown file, defining the AI's core identity, principles, and operational
 *     procedures.</li>
 *
 *     <li>{@link uno.anahata.ai.context.provider.spi.ChatStatusProvider}: Injects real-time, high-level status
 *     information about the current chat session, including configuration details, latency, and recent API errors,
 *     which is crucial for the model's self-awareness and debugging capabilities.</li>
 *
 *     <li>{@link uno.anahata.ai.context.provider.spi.ContextSummaryProvider}: Provides a detailed, structured
 *     overview of the entire conversation history, including token counts and a table of all messages and their parts.
 *     It also includes instructions for the model on how to perform context compression, making it a key component for
 *     managing long conversations.</li>
 *
 *     <li>{@link uno.anahata.ai.context.provider.spi.StatefulResourcesProvider}: A vital component for maintaining
 *     data integrity, it actively monitors all files and other stateful resources loaded into the context, providing a
 *     real-time report on whether the in-memory version is still synchronized with the version on disk.</li>
 *
 *     <li>{@link uno.anahata.ai.context.provider.spi.EnvironmentVariablesProvider}: Supplies the model with a
 *     complete list of the host system's environment variables, offering critical context about the execution
 *     environment.</li>
 *
 *     <li>{@link uno.anahata.ai.context.provider.spi.SystemPropertiesProvider}: Injects a comprehensive list of
 *     Java Virtual Machine (JVM) system properties, giving the model deep insight into the runtime environment,
 *     including classpath configurations and operating system details.</li>
 * </ul>
 *
 * Collectively, these providers form a sophisticated system for dynamically constructing the AI's context, ensuring it
 * has all the necessary information to perform its tasks effectively.
 */
package uno.anahata.ai.context.provider.spi;