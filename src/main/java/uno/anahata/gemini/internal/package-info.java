/**
 * Provides internal utility classes for the Gemini client.
 * <p>
 * This package contains helper classes and utilities that are used throughout the application
 * but are not intended for public use. These include custom serializers, content converters,
 * and wrappers for third-party libraries.
 *
 * <h2>Key Utilities:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.internal.GsonUtils}: Manages the Gson instance for JSON
 *       serialization and deserialization, particularly for function call arguments and responses.</li>
 *
 *   <li>{@link uno.anahata.gemini.internal.KryoUtils}: Manages the Kryo instance and custom
 *       serializers for efficient session persistence.</li>
 *
 *   <li>{@link uno.anahata.gemini.internal.PartUtils}: Provides helper methods for converting
 *       between different data types and the GenAI {@link com.google.genai.types.Part} object.</li>
 *
 *   <li>{@link uno.anahata.gemini.internal.TikaUtils}: A wrapper for the Apache Tika library
 *       to detect MIME types of files.</li>
 *
 *   <li>{@link uno.anahata.gemini.internal.ContentUtils}: Provides utilities for manipulating
 *       {@link com.google.genai.types.Content} objects.</li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.gemini.internal.kryo}: Contains custom serializers for the Kryo
 *       framework to handle types not supported by default, such as {@link java.util.Optional}.</li>
 * </ul>
 */
package uno.anahata.gemini.internal;
