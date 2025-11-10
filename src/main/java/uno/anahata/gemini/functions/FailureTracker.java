package uno.anahata.gemini.functions;

import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import uno.anahata.gemini.config.ChatConfig;

public class FailureTracker {

    private final int maxFailures;
    private final long timeWindowMs;
    private final Map<String, List<FailureRecord>> failureLog = new ConcurrentHashMap<>();

    public FailureTracker(ChatConfig config) {
        this.maxFailures = config.getFailureTrackerMaxFailures();
        this.timeWindowMs = config.getFailureTrackerTimeWindowMs();
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
        failures.removeIf(record -> (currentTime - record.timestamp) > timeWindowMs);

        return failures.size() >= maxFailures;
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
