/**
 * Provides a comprehensive suite of internal utility classes that support the core functionalities of the Gemini client.
 * <p>
 * This package contains essential helper classes for a wide range of operations, but they are designed for internal
 * use and are not part of the public API. These utilities encapsulate critical logic for serialization, data handling,
 * and text processing.
 *
 * <h2>Key Responsibilities:</h2>
 * <ul>
 *   <li><b>Serialization:</b>
 *     <ul>
 *       <li>{@link uno.anahata.ai.internal.KryoUtils}: Manages a pre-configured, thread-safe Kryo instance
 *           for high-performance serialization and deserialization of chat sessions.</li>
 *       <li>{@link uno.anahata.ai.internal.GsonUtils}: Provides a customized Gson instance for robust JSON
 *           handling, including a smart pretty-printer.</li>
 *     </ul>
 *   </li>
 *   <li><b>Content and Data Handling:</b>
 *     <ul>
 *       <li>{@link uno.anahata.ai.internal.ContentUtils}: Offers methods for the immutable manipulation of
 *           {@link com.google.genai.types.Content} objects.</li>
 *       <li>{@link uno.anahata.ai.internal.PartUtils}: A versatile helper for creating and analyzing
 *           {@link com.google.genai.types.Part} objects, including MIME type detection and token count estimation.</li>
 *       <li>{@link uno.anahata.ai.internal.TikaUtils}: A clean wrapper around Apache Tika for file type
 *           detection and content extraction.</li>
 *     </ul>
 *   </li>
 *   <li><b>Text Processing:</b>
 *     <ul>
 *       <li>{@link uno.anahata.ai.internal.TextUtils}: A powerful utility for processing and formatting text,
 *           featuring pagination, regex filtering (grep), and intelligent line truncation.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Subpackages:</h2>
 * <ul>
 *   <li>{@link uno.anahata.ai.internal.kryo}: Contains custom serializers for the Kryo framework,
 *       such as the one for {@link java.util.Optional}, to ensure complete and correct session state persistence.</li>
 * </ul>
 */
package uno.anahata.ai.internal;
