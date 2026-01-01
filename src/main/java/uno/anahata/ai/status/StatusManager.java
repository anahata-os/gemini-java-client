/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.status;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import uno.anahata.ai.Chat;

/**
 * Manages and broadcasts the operational status of a {@link Chat} session.
 * <p>
 * This class tracks the current {@link ChatStatus}, monitors API errors for retry logic,
 * records token usage metadata, and notifies registered {@link StatusListener}s of state changes.
 * </p>
 */
@Getter
public class StatusManager {
    private final Chat chat;
    private final List<StatusListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ApiExceptionRecord> apiErrors = new ArrayList<>();
    private volatile ChatStatus currentStatus = ChatStatus.IDLE_WAITING_FOR_USER;

    // Time tracking fields
    private long lastUserInputTime;
    private long statusChangeTime;
    private long lastOperationDuration = -1;

    /**
     * The token usage metadata from the most recent successful API response.
     */
    private GenerateContentResponseUsageMetadata lastUsage;
    
    /**
     * The name of the tool currently being executed, if any.
     */
    @Setter
    private volatile String executingToolName;

    /**
     * Constructs a new StatusManager for the given Chat instance.
     *
     * @param chat The Chat instance to manage status for.
     */
    public StatusManager(Chat chat) {
        this.chat = chat;
        resetTimers();
    }
    
    private void resetTimers() {
        this.statusChangeTime = System.currentTimeMillis();
        this.lastUserInputTime = System.currentTimeMillis();
    }

    /**
     * Adds a listener to be notified of status changes.
     *
     * @param listener The listener to add.
     */
    public void addListener(StatusListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously added status listener.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(StatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Records the timestamp of the most recent user input.
     */
    public void recordUserInputTime() {
        this.lastUserInputTime = System.currentTimeMillis();
        this.lastOperationDuration = -1;
    }

    /**
     * Updates the current operational status and notifies listeners.
     *
     * @param newStatus The new status to set.
     */
    public void setStatus(ChatStatus newStatus) {
        if (this.currentStatus != newStatus) {
            if (newStatus == ChatStatus.IDLE_WAITING_FOR_USER && this.currentStatus != ChatStatus.IDLE_WAITING_FOR_USER) {
                this.lastOperationDuration = System.currentTimeMillis() - this.lastUserInputTime;
            }
            this.currentStatus = newStatus;
            this.statusChangeTime = System.currentTimeMillis();
            
            // Clear the executing tool name if we are no longer executing tools
            if (newStatus != ChatStatus.TOOL_EXECUTION_IN_PROGRESS) {
                this.executingToolName = null;
            }
            
            fireStatusChanged(newStatus);
        }
    }

    /**
     * Sets the usage metadata from the last API response.
     *
     * @param lastUsage The usage metadata.
     */
    public void setLastUsage(GenerateContentResponseUsageMetadata lastUsage) {
        this.lastUsage = lastUsage;
    }

    /**
     * Clears the history of API errors.
     */
    public void clearApiErrors() {
        if (!apiErrors.isEmpty()) {
            apiErrors.clear();
        }
    }
    
    /**
     * Resets the status manager to its initial state.
     */
    public void reset() {
        clearApiErrors();
        this.lastUsage = null;
        this.lastOperationDuration = -1;
        this.executingToolName = null;
        resetTimers();
        setStatus(ChatStatus.IDLE_WAITING_FOR_USER);
    }

    /**
     * Records an API error and updates the status to {@code WAITING_WITH_BACKOFF}.
     *
     * @param modelId        The ID of the model being called.
     * @param apiKey         The last 5 characters of the API key used.
     * @param retryAttempt   The current retry attempt number.
     * @param backoffAmount  The amount of time to wait before the next retry.
     * @param throwable      The exception that occurred.
     */
    public void recordApiError(String modelId, String apiKey, int retryAttempt, long backoffAmount, Throwable throwable) {
        ApiExceptionRecord record = new ApiExceptionRecord(modelId, apiKey, new Date(), retryAttempt, backoffAmount, throwable);
        apiErrors.add(record);
        setStatus(ChatStatus.WAITING_WITH_BACKOFF);
    }

    /**
     * Gets an unmodifiable list of all recorded API errors.
     *
     * @return The list of API error records.
     */
    public List<ApiExceptionRecord> getApiErrors() {
        return Collections.unmodifiableList(apiErrors);
    }

    /**
     * Gets the most recent API error record.
     *
     * @return The last error record, or {@code null} if no errors have occurred.
     */
    public ApiExceptionRecord getLastApiError() {
        return apiErrors.isEmpty() ? null : apiErrors.get(apiErrors.size() - 1);
    }

    private void fireStatusChanged(ChatStatus newStatus) {
        String exceptionString = getLastApiError() != null ? getLastApiError().getException().toString() : null;
        for (StatusListener listener : listeners) {
            try {
                listener.statusChanged(newStatus, exceptionString);
            } catch (Exception e) {
                System.err.println("StatusListener " + listener.getClass().getName() + " threw an exception: " + e.getMessage());
            }
        }
    }
}