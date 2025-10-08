package uno.anahata.gemini;

public interface ContextListener {

    void contentAdded(ChatMessage message);

    void contextCleared();

    void contextModified();
}
