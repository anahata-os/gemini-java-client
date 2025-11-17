package uno.anahata.ai.context;

import uno.anahata.ai.Chat;

/**
 * A listener for changes in the chat context.
 *
 * @author pablo
 */
public interface ContextListener {

    /**
     * Fired whenever the context has changed in any way (messages added, removed, or modified).
     * The listener is responsible for completely redrawing its view from the new context state.
     * @param source The Chat instance where the change occurred.
     */
    void contextChanged(Chat source);

    /**
     * Fired when the entire context is cleared, usually on a chat restart.
     * @param source The Chat instance that was cleared.
     */
    void contextCleared(Chat source);

}
