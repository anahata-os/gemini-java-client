/**
 * Provides the core configuration framework for chat sessions.
 * <p>
 * This package contains {@link uno.anahata.ai.config.ChatConfig}, an abstract
 * base class that defines the essential settings for a chat, including tool
 * availability and context providers. The {@link uno.anahata.ai.config.ConfigManager}
 * class then uses this configuration to dynamically construct the
 * {@link com.google.genai.types.GenerateContentConfig} for each API call,
 * assembling system instructions and selecting the appropriate tools based on
 a* the application's state.
 */
package uno.anahata.ai.config;
