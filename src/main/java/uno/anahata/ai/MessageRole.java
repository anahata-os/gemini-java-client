/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A type-safe enum representing the role of the author of a ChatMessage.
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
     * Defaults to USER for any unknown values.
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
