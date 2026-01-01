/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.status;

import java.util.Date;
import lombok.Value;

/**
 * A record to hold structured information about a single API error event,
 * including retry context.
 * <p>
 * This is used by the {@link StatusManager} to track the history of errors
 * and provide diagnostic information to the user.
 * </p>
 *
 * @author AI
 */
@Value
public class ApiExceptionRecord {
    /** The ID of the model that was being called when the error occurred. */
    String modelId;

    /** The last 5 digits of the API key used for the failed call. */
    String apiKey;

    /** The timestamp of when the error occurred. */
    Date timestamp;

    /** The retry attempt number (e.g., 0 for the first attempt, 1 for the second). */
    int retryAttempt;

    /** The backoff delay in milliseconds that was waited *before* making the call that resulted in this exception. Will be 0 for the first attempt (retryAttempt 0). */
    long backoffAmount;

    /** The actual exception that was thrown. */
    Throwable exception;
}