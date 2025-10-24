package uno.anahata.gemini.spi;

import com.google.genai.types.Part;
import java.util.List;

/**
 * A Service Provider Interface (SPI) for modules that need to contribute
 * specific instructions or context to the AI model's system prompt.
 * <p>
 * Implementations of this interface can be discovered at runtime using
 * {@link java.util.ServiceLoader}, allowing for a modular and extensible
 * way to build the system instructions.
 */
public interface SystemInstructionProvider {

    /**
     * Gets the unique identifier for this provider. This ID is used
     * for enabling/disabling the provider and for display purposes in the UI.
     *
     * @return A non-null, unique string ID (e.g., "netbeans-project-overview").
     */
    String getId();

    /**
     * Gets the display name for this provider. This is a human-readable
     * name used in the UI (e.g., "Project Overview").
     *
     * @return A non-null, human-readable name.
     */
    String getDisplayName();

    /**
     * Checks if this provider is currently active and should contribute its
     * instruction parts.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    boolean isEnabled();

    /**
     * Sets the enabled state of this provider. This allows the user to
     * dynamically toggle instruction providers on and off via the UI.
     *
     * @param enabled The new state.
     */
    void setEnabled(boolean enabled);

    /**
     * Provides a list of {@link Part} objects to be included in the system
     * instructions.
     * <p>
     * This method will be called before each request to the model if the
     * provider is enabled. Implementations should be efficient and avoid
     * long-running operations.
     *
     * @return A list of Parts. The list can be empty if the provider has
     *         no instructions to add at the moment.
     */
    List<Part> getInstructionParts();
}
