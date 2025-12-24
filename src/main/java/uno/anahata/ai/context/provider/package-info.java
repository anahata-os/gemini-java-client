/* Licensed under the Apache License, Version 2.0 */
/**
 * Defines the framework for dynamically injecting contextual information into the AI model's prompt.
 *
 * This package provides a flexible and extensible plugin architecture centered around the {@link uno.anahata.ai.context.provider.ContextProvider} interface.
 * Implementations of this interface can provide specific pieces of information, such as system properties,
 * environment variables, or details about stateful resources currently in the chat.
 *
 * Key components:
 * <ul>
 *     <li>{@link uno.anahata.ai.context.provider.ContextProvider}: The core abstract class that defines the contract for all context-providing modules.</li>
 *     <li>{@link uno.anahata.ai.context.provider.ContextPosition}: An enum that specifies *where* in the prompt the context should be injected,
 *     allowing a distinction between permanent system instructions and temporary, just-in-time augmented context.</li>
 *     <li>{@link uno.anahata.ai.context.provider.ContentFactory}: The orchestrator that discovers, invokes, and assembles the content from all
 *     enabled providers at the appropriate time.</li>
 * </ul>
 */
package uno.anahata.ai.context.provider;
