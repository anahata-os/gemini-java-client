/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import uno.anahata.ai.Chat;

/**
 * Tracks consecutive failures of tool calls and implements a temporary
 * blocking mechanism.
 * <p>
 * If a specific tool (identified by its name and arguments) fails repeatedly
 * within a short time window, this tracker will block subsequent calls to
 * that tool to prevent infinite loops or excessive resource consumption.
 * </p>
 */
public class FailureTracker {

    private final Map<String, List<FailureRecord>> failureLog = new ConcurrentHashMap<>();
    private final Chat chat;

    /**
     * Constructs a new FailureTracker for the given Chat instance.
     *
     * @param chat The Chat instance.
     */
    public FailureTracker(Chat chat) {
        this.chat = chat;
    }

    /**
     * Records a failure for a specific function call.
     *
     * @param functionCall The function call that failed.
     * @param e            The exception that occurred.
     */
    public void recordFailure(FunctionCall functionCall, Exception e) {
        String key = generateKey(functionCall);
        failureLog.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                  .add(new FailureRecord(System.currentTimeMillis(), e.getMessage()));
    }

    /**
     * Checks if a specific function call is currently blocked due to repeated failures.
     * <p>
     * This method also performs cleanup of expired failure records based on the
     * configured time window.
     * </p>
     *
     * @param functionCall The function call to check.
     * @return {@code true} if the call is blocked, {@code false} otherwise.
     */
    public boolean isBlocked(FunctionCall functionCall) {
        String key = generateKey(functionCall);
        List<FailureRecord> failures = failureLog.get(key);

        if (failures == null || failures.isEmpty()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        failures.removeIf(record -> (currentTime - record.timestamp) > chat.getConfig().getFailureTrackerTimeWindowMs());

        return failures.size() >= chat.getConfig().getFailureTrackerMaxFailures();
    }

    private String generateKey(FunctionCall functionCall) {
        // Use a TreeMap to ensure stable argument ordering for the key
        Map<String, Object> sortedArgs = new TreeMap<>(functionCall.args().get());
        return functionCall.name().get() + ":" + sortedArgs.toString();
    }

    private static class FailureRecord {
        final long timestamp;
        final String errorMessage;

        FailureRecord(long timestamp, String errorMessage) {
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
        }
    }
}