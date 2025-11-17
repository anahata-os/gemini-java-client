/**
 * Provides the logic for context pruning.
 * <p>
 * This package contains the {@link uno.anahata.gemini.context.pruning.ContextPruner},
 * a key component responsible for intelligently managing the size of the conversation
 * history to stay within the model's token limits. It implements strategies for
 * removing specific parts of messages, entire messages, and automatically cleaning up
 * old or ephemeral tool call artifacts based on a set of predefined rules.
 */
package uno.anahata.ai.context.pruning;
