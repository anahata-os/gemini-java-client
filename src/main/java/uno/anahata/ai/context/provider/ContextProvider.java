/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context.provider;

import com.google.genai.types.Part;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import uno.anahata.ai.Chat;
import uno.anahata.ai.context.provider.ContextPosition;

/**
 * An abstract base class for modules that contribute dynamic context or
 * instructions to the AI model's prompt.
 * <p>
 * Context providers are executed before each request to the model. They can
 * provide system instructions, workspace information, or any other relevant
 * data that should be included in the context.
 * </p>
 * <p>
 * This class manages the enabled/disabled state and the target position
 * within the prompt.
 * </p>
 */
@AllArgsConstructor
@NoArgsConstructor
@ToString
public abstract class ContextProvider {

    /**
     * Constructs a ContextProvider with a specific target position.
     *
     * @param pos The position in the prompt where this provider's content should be placed.
     */
    public ContextProvider(ContextPosition pos) {
        this.position = pos;
    }
    
    /**
     * Determines where the context from this provider should be placed in the prompt.
     * Defaults to {@link ContextPosition#SYSTEM_INSTRUCTIONS}.
     */
    @Getter
    @Setter
    private ContextPosition position = ContextPosition.SYSTEM_INSTRUCTIONS;

    /**
     * Flag to enable or disable this provider.
     */
    @Getter
    @Setter
    private boolean enabled = true;

    /**
     * Gets the unique identifier for this provider.
     * <p>
     * This ID is used for internal tracking, configuration, and persistence.
     * </p>
     *
     * @return A non-null, unique string ID (e.g., "core-context-summary").
     */
    public abstract String getId();

    /**
     * Gets the human-readable display name for this provider.
     *
     * @return A non-null, descriptive name.
     */
    public abstract String getDisplayName();

    /**
     * Gets a brief, human-readable description of what this provider does.
     *
     * @return The description.
     */
    public String getDescription() {
        return "";
    }

    /**
     * Generates a list of {@link Part} objects to be included in the model's prompt.
     * <p>
     * This method is called by the {@link ContentFactory} before each request.
     * Implementations should be efficient and avoid long-running operations.
     * </p>
     *
     * @param chat The current Chat instance, providing access to the session state.
     * @return A list of Parts, or an empty list if the provider has no content to add.
     */
    public abstract List<Part> getParts(Chat chat);
}