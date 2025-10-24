package uno.anahata.gemini.systeminstructions;

import com.google.genai.types.Part;
import java.util.List;
import uno.anahata.gemini.GeminiChat;

/**
 * An abstract base class for modules that contribute specific instructions or
 * context to the AI model's system prompt.
 * <p>
 * This class manages the enabled/disabled state, and concrete implementations
 * only need to provide their identity and the logic for generating instruction parts.
 */
public abstract class SystemInstructionProvider {

    private boolean enabled = true;

    /**
     * Gets the unique identifier for this provider. This ID is used for
     * internal tracking and potentially for persistence.
     *
     * @return A non-null, unique string ID (e.g., "netbeans-project-overview").
     */
    public abstract String getId();

    /**
     * Gets the display name for this provider. This is a human-readable
     * name used in the UI (e.g., "Project Overview").
     *
     * @return A non-null, human-readable name.
     */
    public abstract String getDisplayName();

    /**
     * Checks if this provider is currently active and should contribute its
     * instruction parts.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state of this provider. This allows the user to
     * dynamically toggle instruction providers on and off via the UI.
     *
     * @param enabled The new state.
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Provides a list of {@link Part} objects to be included in the system
     * instructions.
     * <p>
     * This method will be called before each request to the model if the
     * provider is enabled. Implementations should be efficient and avoid
     * long-running operations.
     *
     * @param chat The current GeminiChat instance, providing access to the
     *             complete, real-time context of the application.
     * @return A list of Parts. The list can be empty if the provider has
     *         no instructions to add at the moment.
     */
    public abstract List<Part> getInstructionParts(GeminiChat chat);
}
