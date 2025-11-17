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
 * Manages the status of a Chat session.
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

    // Usage metadata field
    private GenerateContentResponseUsageMetadata lastUsage;
    
    // Currently executing tool
    @Setter
    private volatile String executingToolName;

    public StatusManager(Chat chat) {
        this.chat = chat;
        resetTimers();
    }
    
    private void resetTimers() {
        this.statusChangeTime = System.currentTimeMillis();
        this.lastUserInputTime = System.currentTimeMillis();
    }

    public void addListener(StatusListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StatusListener listener) {
        listeners.remove(listener);
    }

    public void recordUserInputTime() {
        this.lastUserInputTime = System.currentTimeMillis();
        this.lastOperationDuration = -1;
    }

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

    public void setLastUsage(GenerateContentResponseUsageMetadata lastUsage) {
        this.lastUsage = lastUsage;
    }

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

    public void recordApiError(String modelId, String apiKey, int retryAttempt, long backoffAmount, Throwable throwable) {
        ApiExceptionRecord record = new ApiExceptionRecord(modelId, apiKey, new Date(), retryAttempt, backoffAmount, throwable);
        apiErrors.add(record);
        setStatus(ChatStatus.WAITING_WITH_BACKOFF);
    }

    public List<ApiExceptionRecord> getApiErrors() {
        return Collections.unmodifiableList(apiErrors);
    }

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
