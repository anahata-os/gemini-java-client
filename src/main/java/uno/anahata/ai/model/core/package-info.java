/**
 * Defines the core domain model for the AI assistant, providing a model-agnostic
 * representation of the conversation and its components.
 * <p>
 * This package serves as the central abstraction layer, decoupling the main application
 * logic from the specific implementation details of any particular AI provider's API
 * (e.g., Google Gemini, OpenAI). By defining our own clear and concise data
 * structures like {@link uno.anahata.ai.model.core.MessagePart}, we ensure that the core framework remains
 * flexible and can be adapted to support different models in the future without
 * requiring a major rewrite of the business logic.
 *
 * @author anahata
 */
package uno.anahata.ai.model.core;