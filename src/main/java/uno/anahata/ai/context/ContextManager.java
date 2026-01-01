/* Licensed under the Apache License, Version 2.0 */
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

/**
 * Manages the conversation context (history) for a {@link Chat} session.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Maintaining the list of {@link ChatMessage} objects.</li>
 *   <li>Tracking the total token count of the conversation.</li>
 *   <li>Managing context listeners for history changes.</li>
 *   <li>Delegating specialized tasks to {@link SessionManager} (persistence),
 *       {@link ResourceTracker} (stateful files), and {@link ContextPruner} (pruning).</li>
 * </ul>
 * </p>
 */
@Slf4j
@Getter
public class ContextManager {

    /**
     * The underlying list of messages in the conversation.
     */
    private List<ChatMessage> context = new ArrayList<>();
    
    /**
     * The Chat instance this manager belongs to.
     */
    private final Chat chat;
    
    /**
     * The configuration for the associated chat session.
     */
    private final ChatConfig config;
    
    private final List<ContextListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * The tool manager used for function calling operations.
     */
    private final ToolManager toolManager;
    
    /**
     * The total number of tokens currently in the context, as reported by the last API response.
     */
    private int totalTokenCount = 0;
    
    /**
     * The maximum number of tokens allowed in the context before pruning or warnings are triggered.
     */
    @Getter
    @Setter
    private int tokenThreshold = 250_000;
    
    /**
     * The timestamp of the last message added to the context.
     */
    private Instant lastMessageTimestamp = Instant.now();

    // Delegated classes
    private final SessionManager sessionManager;
    private final ResourceTracker resourceTracker;
    private final ContextPruner contextPruner;

    /**
     * Constructs a new ContextManager for the given Chat instance.
     *
     * @param chat The Chat instance to manage context for.
     */
    public ContextManager(Chat chat) {
        this.chat = chat;
        this.config = chat.getConfig();
        this.toolManager = chat.getToolManager();

        this.sessionManager = new SessionManager(this);
        this.resourceTracker = new ResourceTracker(this);
        this.contextPruner = new ContextPruner(this);
    }

    /**
     * Gets the ContextManager for the currently executing tool.
     * <p>
     * This method uses a {@code ThreadLocal} to retrieve the active instance,
     * allowing tools to interact with the context without needing an explicit reference.
     * </p>
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

    /**
     * Adds a listener to be notified of changes to the conversation history.
     *
     * @param listener The listener to add.
     */
    public void addListener(ContextListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously added context listener.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(ContextListener listener) {
        listeners.remove(listener);
    }

    /**
     * Adds a new message to the conversation context.
     * <p>
     * This method performs several side effects:
     * <ul>
     *   <li>Calculates and sets the elapsed time since the last message.</li>
     *   <li>Handles stateful resource replacement via {@link ResourceTracker}.</li>
     *   <li>Updates the total token count if usage metadata is present.</li>
     *   <li>Triggers ephemeral tool call pruning if the message is from the user.</li>
     *   <li>Notifies listeners of the history change.</li>
     * </ul>
     * </p>
     *
     * @param message The message to add.
     */
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

    /**
     * Gets the total token count of the conversation.
     *
     * @return The total token count.
     */
    public synchronized int getTotalTokenCount() {
        return totalTokenCount;
    }

    /**
     * Clears all messages from the context and resets the token count.
     */
    public synchronized void clear() {
        context.clear();
        totalTokenCount = 0;
        lastMessageTimestamp = Instant.now(); // Reset timestamp on clear
        listeners.forEach(l -> l.contextCleared(chat));
        log.info("Chat history cleared.");
    }

    /**
     * Returns a copy of the current conversation history.
     *
     * @return A list of {@link ChatMessage} objects.
     */
    public synchronized List<ChatMessage> getContext() {
        return new ArrayList<>(context);
    }

    /**
     * Replaces the entire conversation history with a new list of messages.
     * <p>
     * This is typically used when restoring a session. It recalculates elapsed times
     * for all messages and updates the token count from the most recent usage metadata.
     * </p>
     *
     * @param newContext The new list of messages.
     */
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

    /**
     * Prunes a set of messages from the context by their sequential IDs.
     *
     * @param sequentialIds The list of sequential IDs to prune.
     * @param reason        The reason for pruning.
     */
    public synchronized void pruneMessages(List<Long> sequentialIds, String reason) {
        contextPruner.pruneMessages(sequentialIds, reason);
    }

    /**
     * Prunes specific parts from a message.
     *
     * @param messageSequentialId The sequential ID of the message.
     * @param partIndices         The indices of the parts to prune.
     * @param reason              The reason for pruning.
     */
    public synchronized void pruneParts(long messageSequentialId, List<Number> partIndices, String reason) {
        contextPruner.pruneParts(messageSequentialId, partIndices, reason);
    }

    /**
     * Prunes specific parts from the context by their object references.
     *
     * @param partsToPrune The list of Part objects to prune.
     * @param reason       The reason for pruning.
     */
    public synchronized void prunePartsByReference(List<Part> partsToPrune, String reason) {
        contextPruner.prunePartsByReference(partsToPrune, reason);
    }
    
    /**
     * Prunes a specific tool call and its associated response from the context.
     *
     * @param toolCallId The unique ID of the tool call.
     * @param reason     The reason for pruning.
     */
    public synchronized void pruneToolCall(String toolCallId, String reason) {
        contextPruner.pruneToolCall(toolCallId, reason);
    }

    /**
     * Notifies listeners of a change in the conversation history and triggers an automatic backup.
     */
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