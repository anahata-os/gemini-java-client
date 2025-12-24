/* Licensed under the Apache License, Version 2.0 */
/**
 * This package serves as the primary bridge between the application and the Google Gemini API.
 * It encapsulates all direct interactions with the {@code com.google.genai} client library,
 * providing a clean abstraction layer that separates API-specific concerns from the core
 * application logic.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *     <li><b>{@link uno.anahata.ai.gemini.GeminiAPI}</b>: Manages the lifecycle of the Gemini
 *         client, including API key loading, key rotation, and client instantiation. It ensures
 *         that the application can securely and reliably connect to the Gemini service.</li>
 *
 *     <li><b>{@link uno.anahata.ai.gemini.GeminiAdapter}</b>: A sophisticated utility class
 *         responsible for translating data structures between the application's domain and the
 *         Gemini API's required format. Its most critical function is the dynamic generation of
 *         {@link com.google.genai.types.Schema} objects from Java types, which is the cornerstone
 *         of the automatic tool-to-function mapping. It also includes vital sanitization logic
 *         to enforce the presence of unique IDs on all tool calls returned by the model.</li>
 * </ul>
 *
 * Together, these classes provide a robust and maintainable integration point with the
 * underlying AI service.
 */
package uno.anahata.ai.gemini;
