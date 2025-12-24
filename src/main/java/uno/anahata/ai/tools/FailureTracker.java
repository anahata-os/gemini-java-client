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

public class FailureTracker {

    private final Map<String, List<FailureRecord>> failureLog = new ConcurrentHashMap<>();
    private final Chat chat;

    public FailureTracker(Chat chat) {
        this.chat = chat;
    }

    public void recordFailure(FunctionCall functionCall, Exception e) {
        String key = generateKey(functionCall);
        failureLog.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                  .add(new FailureRecord(System.currentTimeMillis(), e.getMessage()));
    }

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
