package uno.anahata.gemini.context;

import com.google.genai.types.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.GeminiChat;
import uno.anahata.gemini.config.ChatConfig;
import uno.anahata.gemini.context.pruning.ContextPruner;
import uno.anahata.gemini.context.session.SessionManager;
import uno.anahata.gemini.context.stateful.ResourceTracker;
import uno.anahata.gemini.context.stateful.StatefulResourceStatus;
import uno.anahata.gemini.functions.FunctionManager;
import uno.anahata.gemini.internal.PartUtils;

@Slf4j
@Getter
public class ContextManager {

    private List<ChatMessage> context = new ArrayList<>();
    private final GeminiChat chat;
    private final ChatConfig config;
    private final List<ContextListener> listeners = new CopyOnWriteArrayList<>();
    private final FunctionManager functionManager;
    private int totalTokenCount = 0;
    @Getter
    @Setter
    private int tokenThreshold = 125_000;

    // Delegated classes
    private final SessionManager sessionManager;
    private final ResourceTracker resourceTracker;
    private final ContextPruner contextPruner;

    public ContextManager(GeminiChat chat) {
        this.chat = chat;
        this.config = chat.getConfig();
        this.functionManager = chat.getFunctionManager();
        
        this.sessionManager = new SessionManager(this);
        this.resourceTracker = new ResourceTracker(this);
        this.contextPruner = new ContextPruner(this);
    }

    /**
     * Gets the ContextManager for the currently executing tool. 
     *
     * @return The active ContextManager.
     * @throws IllegalStateException if not called from a tool execution thread.
     */
    public static ContextManager getCallingInstance() {
        GeminiChat chat = GeminiChat.getCallingInstance();
        if (chat == null) {
            throw new IllegalStateException("ContextManager.getCurrentInstance() can only be called from a thread where a tool is being executed by the model.");
        }
        return chat.getContextManager();
    }

    public void addListener(ContextListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ContextListener listener) {
        listeners.remove(listener);
    }

    public synchronized void add(ChatMessage message) {
        resourceTracker.handleStatefulReplace(message, functionManager);
        context.add(message);

        if (message.getUsageMetadata() != null) {
            this.totalTokenCount = message.getUsageMetadata().totalTokenCount().orElse(this.totalTokenCount);
        }

        if (isUserMessage(message)) {
            contextPruner.pruneEphemeralToolCalls(functionManager);
        }
        
        notifyHistoryChange();
    }

    public synchronized int getTotalTokenCount() {
        return totalTokenCount;
    }

    public synchronized void clear() {
        context.clear();
        totalTokenCount = 0;
        listeners.forEach(l -> l.contextCleared(chat));
        log.info("Chat history cleared.");
    }

    public synchronized List<ChatMessage> getContext() {
        return new ArrayList<>(context);
    }

    public synchronized void setContext(List<ChatMessage> newContext) {
        this.context = new ArrayList<>(newContext);
        // Recalculate token count based on the new context
        this.totalTokenCount = newContext.stream()
            .map(ChatMessage::getUsageMetadata)
            .filter(Objects::nonNull)
            .map(usage -> usage.totalTokenCount().orElse(0))
            .reduce(0, Integer::sum);
        log.info("Context set. New token count: {}", this.totalTokenCount);
        notifyHistoryChange();
    }

    public synchronized void pruneMessages(List<String> uids, String reason) {
        contextPruner.pruneMessages(uids, reason);
    }

    public synchronized void pruneParts(String messageUID, List<Number> partIndices, String reason) {
        contextPruner.pruneParts(messageUID, partIndices, reason);
    }

    public synchronized void prunePartsByReference(List<Part> partsToPrune, String reason) {
        contextPruner.prunePartsByReference(partsToPrune, reason);
    }

    public void notifyHistoryChange() {
        listeners.forEach(l -> l.contextChanged(chat));
        sessionManager.triggerAutobackup();
    }
    
    public String getContextId() {
        return config.getSessionId() + "-" + System.identityHashCode(this);
    }
    
    private boolean isUserMessage(ChatMessage message) {
        return message.getContent() != null && "user".equals(message.getContent().role().orElse(null));
    }

}