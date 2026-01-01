/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.context;

import uno.anahata.ai.Chat;

/**
 * A listener interface for receiving notifications about changes in the conversation context.
 * <p>
 * Implementations can be registered with a {@link Chat} instance to react to new messages,
 * pruning operations, or context resets.
 * </p>
 *
 * @author anahata
 */
public interface ContextListener {

    /**
     * Fired whenever the context has changed in any way (messages added, removed, or modified).
     * <p>
     * The listener is responsible for completely redrawing its view or updating its state
     * based on the new context state.
     * </p>
     * @param source The Chat instance where the change occurred.
     */
    void contextChanged(Chat source);

    /**
     * Fired when the entire context is cleared, usually on a chat restart.
     * @param source The Chat instance that was cleared.
     */
    void contextCleared(Chat source);

}