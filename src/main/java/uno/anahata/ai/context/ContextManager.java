package uno.anahata.ai.context;

import com.google.genai.types.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.ai.ChatMessage;
import uno.anahata.ai.Chat;
import uno.anahata.ai.config.ChatConfig;
import uno.anahata.ai.context.pruning.ContextPruner;
import uno.anahata.ai.context.session.SessionManager;
import uno.anahata.ai.context.stateful.ResourceTracker;
import uno.anahata.ai.tools.ToolManager;

@Slf4j
@Getter
public class ContextManager {

    private List<ChatMessage> context = new ArrayList<>();
    private final Chat chat;
    private final ChatConfig config;
    private final List<ContextListener> listeners = new CopyOnWriteArrayList<>();
    private final ToolManager toolManager;
    private int totalTokenCount = 0;
    @Getter
    @Setter
    private int tokenThreshold = 250_000;
    private Instant lastMessageTimestamp = Instant.now();

    // Delegated classes
    private final SessionManager sessionManager;
    private final ResourceTracker resourceTracker;
    private final ContextPruner contextPruner;

    public ContextManager(Chat chat) {
        this.chat = chat;
        this.config = chat.getConfig();
        this.toolManager = chat.getFunctionManager();

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
        Chat chat = Chat.getCallingInstance();
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
        // Set elapsed time before adding
        Instant now = Instant.now();
        long elapsed = now.toEpochMilli() - lastMessageTimestamp.toEpochMilli();
        message.setElapsedTimeMillis(elapsed);
        lastMessageTimestamp = now;

        resourceTracker.handleStatefulReplace(message, toolManager);
        context.add(message);

        if (message.getUsageMetadata() != null) {
            this.totalTokenCount = message.getUsageMetadata().promptTokenCount().orElse(this.totalTokenCount);
        }

        if (isUserMessage(message)) {
            contextPruner.pruneEphemeralToolCalls(toolManager);
        }

        notifyHistoryChange();
    }

    public synchronized int getTotalTokenCount() {
        return totalTokenCount;
    }

    public synchronized void clear() {
        context.clear();
        totalTokenCount = 0;
        lastMessageTimestamp = Instant.now(); // Reset timestamp on clear
        listeners.forEach(l -> l.contextCleared(chat));
        log.info("Chat history cleared.");
    }

    public synchronized List<ChatMessage> getContext() {
        return new ArrayList<>(context);
    }

    public synchronized void setContext(List<ChatMessage> newContext) {
        this.context = new ArrayList<>(newContext);
        
        // Recalculate elapsed time for the entire restored context
        if (!context.isEmpty()) {
            lastMessageTimestamp = context.get(0).getCreatedOn();
            context.get(0).setElapsedTimeMillis(0);
            for (int i = 1; i < context.size(); i++) {
                ChatMessage current = context.get(i);
                ChatMessage previous = context.get(i - 1);
                long elapsed = current.getCreatedOn().toEpochMilli() - previous.getCreatedOn().toEpochMilli();
                current.setElapsedTimeMillis(elapsed);
            }
            lastMessageTimestamp = context.get(context.size() - 1).getCreatedOn();
        } else {
            lastMessageTimestamp = Instant.now();
        }


        // Correctly set the token count and usage metadata from the *last* available record.
        GenerateContentResponseUsageMetadata lastUsage = null;
        for (int i = newContext.size() - 1; i >= 0; i--) {
            ChatMessage msg = newContext.get(i);
            if (msg.getUsageMetadata() != null) {
                lastUsage = msg.getUsageMetadata();
                break;
            }
        }

        if (lastUsage != null) {
            this.totalTokenCount = lastUsage.totalTokenCount().orElse(0);
            chat.getStatusManager().setLastUsage(lastUsage);
            log.info("Context restored. Last usage metadata found. Token count set to: {}", this.totalTokenCount);
        } else {
            this.totalTokenCount = 0;
            chat.getStatusManager().setLastUsage(null);
            log.info("Context restored. No usage metadata found. Token count reset to 0.");
        }

        notifyHistoryChange();
    }

    public synchronized void pruneMessages(List<Long> sequentialIds, String reason) {
        contextPruner.pruneMessages(sequentialIds, reason);
    }

    public synchronized void pruneParts(long messageSequentialId, List<Number> partIndices, String reason) {
        contextPruner.pruneParts(messageSequentialId, partIndices, reason);
    }

    public synchronized void prunePartsByReference(List<Part> partsToPrune, String reason) {
        contextPruner.prunePartsByReference(partsToPrune, reason);
    }
    
    public synchronized void pruneToolCall(String toolCallId, String reason) {
        contextPruner.pruneToolCall(toolCallId, reason);
    }

    public void notifyHistoryChange() {
        listeners.forEach(l -> l.contextChanged(chat));
        sessionManager.triggerAutobackup();
    }

    private boolean isUserMessage(ChatMessage message) {
        return message.getContent() != null && "user".equals(message.getContent().role().orElse(null));
    }
    
    /**
     * Finds the ChatMessage that contains a specific Part instance.
     *
     * @param part The Part to find.
     * @return An Optional containing the ChatMessage if found, otherwise empty.
     */
    public Optional<ChatMessage> getChatMessageForPart(Part part) {
        for (ChatMessage message : context) {
            if (message.getContent().parts().isPresent() && message.getContent().parts().get().contains(part)) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }
}