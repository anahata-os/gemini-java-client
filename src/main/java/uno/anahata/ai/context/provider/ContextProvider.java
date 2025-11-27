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
 * An abstract base class for modules that contribute specific instructions or
 * context to the AI model's system prompt.
 * <p>
 * This class manages the enabled/disabled state, and concrete implementations
 * only need to provide their identity and the logic for generating instruction
 * parts.
 */
@AllArgsConstructor
@NoArgsConstructor
@ToString
public abstract class ContextProvider {

    public ContextProvider(ContextPosition pos) {
        this.position = pos;
    }
    
    /**
     * Determines where the context from this provider should be placed.
     * Defaults to {@code ContextPosition.SYSTEM_INSTRUCTIONS}.
     */
    @Getter
    @Setter
    private ContextPosition position = ContextPosition.SYSTEM_INSTRUCTIONS;

    @Getter
    @Setter
    private boolean enabled = true;

    /**
     * Gets the unique identifier for this provider. This ID is used for
     * internal tracking and potentially for persistence.
     *
     * @return A non-null, unique string ID (e.g., "netbeans-project-overview").
     */
    public abstract String getId();

    /**
     * Gets the display name for this provider. This is a human-readable name
     * used in the UI (e.g., "Project Overview").
     *
     * @return A non-null, human-readable name.
     */
    public abstract String getDisplayName();

    /**
     * Gets a brief, human-readable description of what this provider does. This
     * is used for tooltips in the UI.
     *
     * @return The description.
     */
    public String getDescription() {
        return "";
    }

    /**
     * Provides a list of {@link Part} objects to be included in the system
     * instructions.
     * <p>
     * This method will be called before each request to the model if the
     * provider is enabled. Implementations should be efficient and avoid
     * long-running operations.
     *
     * @param chat The current Chat instance, providing access to the complete,
     * real-time context of the application.
     * @return A list of Parts. The list can be empty if the provider has no
     * instructions to add at the moment.
     */
    public abstract List<Part> getParts(Chat chat);
}
