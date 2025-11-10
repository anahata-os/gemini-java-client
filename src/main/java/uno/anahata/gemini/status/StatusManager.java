package uno.anahata.gemini.status;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import uno.anahata.gemini.GeminiChat;

/**
 * Manages the status of a GeminiChat session, including the current state,
 * a history of API errors, and listener notification.
 *
 * @author AI
 */
@Getter
public class StatusManager {
    private final GeminiChat chat;
    private final List<StatusListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ApiExceptionRecord> apiErrors = new ArrayList<>();
    private volatile ChatStatus currentStatus = ChatStatus.IDLE_WAITING_FOR_USER;

    // Time tracking fields
    private long lastUserInputTime;
    private long statusChangeTime;
    private long lastOperationDuration = -1; // Duration of the last send/process loop

    // Usage metadata field
    private GenerateContentResponseUsageMetadata lastUsage;

    public StatusManager(GeminiChat chat) {
        this.chat = chat;
        this.statusChangeTime = System.currentTimeMillis();
        this.lastUserInputTime = System.currentTimeMillis();
    }

    public void addListener(StatusListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Records the timestamp of the last user input action.
     */
    public void recordUserInputTime() {
        this.lastUserInputTime = System.currentTimeMillis();
        this.lastOperationDuration = -1; // Reset duration on new input
    }

    /**
     * Sets the new status and notifies all registered listeners.
     * The status change time is only updated if the status actually changes.
     *
     * @param newStatus The new status to set.
     */
    public void setStatus(ChatStatus newStatus) {
        if (this.currentStatus != newStatus) {
            // If transitioning TO idle, calculate the total operation duration.
            if (newStatus == ChatStatus.IDLE_WAITING_FOR_USER) {
                this.lastOperationDuration = System.currentTimeMillis() - this.lastUserInputTime;
            }
            
            this.currentStatus = newStatus;
            this.statusChangeTime = System.currentTimeMillis();
            fireStatusChanged(newStatus);
        }
    }
    
    /**
     * Caches the latest usage metadata from a successful API response.
     * @param lastUsage The usage metadata to cache.
     */
    public void setLastUsage(GenerateContentResponseUsageMetadata lastUsage) {
        this.lastUsage = lastUsage;
    }
    
    /**
     * Clears the history of API errors. This should be called after a successful API call.
     */
    public void clearApiErrors() {
        if (!apiErrors.isEmpty()) {
            apiErrors.clear();
        }
        this.lastUsage = null;
        this.lastOperationDuration = -1;
    }

    /**
     * Records a new API error with detailed context.
     *
     * @param modelId The ID of the model being called.
     * @param apiKey The last 5 digits of the API key used.
     * @param retryAttempt The attempt number (0-based).
     * @param backoffAmount The backoff delay waited before this attempt.
     * @param throwable The exception that occurred.
     */
    public void recordApiError(String modelId, String apiKey, int retryAttempt, long backoffAmount, Throwable throwable) {
        ApiExceptionRecord record = new ApiExceptionRecord(modelId, apiKey, new Date(), retryAttempt, backoffAmount, throwable);
        apiErrors.add(record);
        setStatus(ChatStatus.WAITING_WITH_BACKOFF);
    }

    /**
     * Gets an unmodifiable view of the API error history.
     * @return A list of API exception records.
     */
    public List<ApiExceptionRecord> getApiErrors() {
        return Collections.unmodifiableList(apiErrors);
    }
    
    /**
     * Gets the most recent API error, or null if there are none.
     * @return The last recorded ApiExceptionRecord.
     */
    public ApiExceptionRecord getLastApiError() {
        return apiErrors.isEmpty() ? null : apiErrors.get(apiErrors.size() - 1);
    }

    private void fireStatusChanged(ChatStatus newStatus) {
        for (StatusListener listener : listeners) {
            try {
                listener.statusChanged(newStatus, getLastApiError() != null ? getLastApiError().getException().toString() : null);
            } catch (Exception e) {
                // Don't let a faulty listener stop the notification chain
                System.err.println("StatusListener " + listener.getClass().getName() + " threw an exception: " + e.getMessage());
            }
        }
    }
}
