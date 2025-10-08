package uno.anahata.gemini.functions;

import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A service to track and temporarily block repeatedly failing function calls
 * to prevent the model from getting stuck in an error loop.
 * @author Anahata
 */
public class FailureTracker {

    // A function call is blocked if it fails more than MAX_FAILURES times within TIME_WINDOW_MS.
    private static final int MAX_FAILURES = 3;
    private static final long TIME_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, List<FailureRecord>> failureLog = new ConcurrentHashMap<>();

    /**
     * Records a failure for a specific function call.
     * @param functionCall The FunctionCall that failed.
     * @param e The exception that was thrown.
     */
    public void recordFailure(FunctionCall functionCall, Exception e) {
        String key = generateKey(functionCall);
        failureLog.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                  .add(new FailureRecord(System.currentTimeMillis(), e.getMessage()));
    }

    /**
     * Checks if a specific function call is currently blocked due to repeated failures.
     * This method also prunes old failure records that are outside the time window.
     * @param functionCall The FunctionCall to check.
     * @return True if the call should be blocked, false otherwise.
     */
    public boolean isBlocked(FunctionCall functionCall) {
        String key = generateKey(functionCall);
        List<FailureRecord> failures = failureLog.get(key);

        if (failures == null || failures.isEmpty()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        // Prune old failures while checking
        failures.removeIf(record -> (currentTime - record.timestamp) > TIME_WINDOW_MS);

        return failures.size() >= MAX_FAILURES;
    }

    /**
     * Generates a stable, unique key for a function call based on its name and arguments.
     * @param functionCall The FunctionCall.
     * @return A unique string key.
     */
    private String generateKey(FunctionCall functionCall) {
        // Using a sorted map ensures arguments are in a consistent order for the key.
        Map<String, Object> sortedArgs = new TreeMap<>(functionCall.args().get());
        return functionCall.name().get() + ":" + sortedArgs.toString();
    }

    /**
     * A simple record to hold the timestamp and message of a failure.
     */
    private static class FailureRecord {
        final long timestamp;
        final String errorMessage;

        FailureRecord(long timestamp, String errorMessage) {
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
        }
    }
}
