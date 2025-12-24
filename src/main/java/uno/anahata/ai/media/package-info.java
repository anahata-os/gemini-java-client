/* Licensed under the Apache License, Version 2.0 */
/**
 * Provides a comprehensive framework for handling audio and other media,
 * neatly separating high-level, AI-callable tools from their low-level
 * implementations.
 *
 * <h2>Architecture</h2>
 * The package is divided into two main sub-packages:
 * <ul>
 *     <li><b>{@code functions.spi}</b>: Contains the high-level, AI-callable
 *         tools, such as {@link uno.anahata.ai.media.functions.spi.AudioTool}
 *         and {@link uno.anahata.ai.media.functions.spi.RadioTool}. These
 *         classes are designed to be simple and declarative, exposing media
 *         capabilities in a way that is easy for the AI to understand and use.</li>
 *     <li><b>{@code util}</b>: Contains the low-level implementation details
 *         for media handling, such as the {@link uno.anahata.ai.media.util.Microphone}
 *         class, which manages the raw audio capture, and the
 *         {@link uno.anahata.ai.media.util.AudioPlayer}, which handles
 *         non-blocking sound playback.</li>
 * </ul>
 *
 * This separation of concerns makes the system both robust and extensible. The
 * low-level utilities in the {@code util} package can be tested and maintained
 * independently, while the AI tools in the {@code functions} package provide a
 * stable and consistent interface for the AI.
 */
package uno.anahata.ai.media;
