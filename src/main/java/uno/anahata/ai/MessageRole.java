/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A type-safe enum representing the role of the author of a {@link ChatMessage}.
 * <p>
 * This enum maps to the roles recognized by the Gemini API ("user", "model")
 * and includes a "tool" role for function responses.
 * </p>
 */
@Getter
@AllArgsConstructor
public enum MessageRole {
    /** The user who is interacting with the model. */
    USER("user"),

    /** The model that is generating responses. */
    MODEL("model"),

    /** The tool that is providing function responses. */
    TOOL("tool");

    private final String value;

    /**
     * Parses a string role value into the corresponding enum constant.
     * <p>
     * This method is case-insensitive. If the provided value does not match any
     * known role, it defaults to {@link #USER}.
     * </p>
     *
     * @param value The string value (e.g., "user", "model").
     * @return The matching MessageRole enum.
     */
    public static MessageRole fromString(String value) {
        for (MessageRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        // Default to USER if the role is unknown or null
        return USER;
    }
}