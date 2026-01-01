/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A data class representing the state and result of an asynchronous background job.
 * <p>
 * When a tool is executed asynchronously, a {@code JobInfo} object is initially
 * returned to the model to indicate that the task has started. Once the task
 * completes, another {@code JobInfo} with the final result is added to the
 * context.
 * </p>
 * 
 * @author Anahata
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobInfo {
    /**
     * Defines the possible states of a background job.
     */
    public enum JobStatus {
        /** The job has been submitted and is starting. */
        STARTED,
        /** The job is currently executing. */
        RUNNING,
        /** The job has finished successfully. */
        COMPLETED,
        /** The job failed with an error. */
        FAILED
    }

    /**
     * A unique identifier for the job.
     */
    private String jobId;
    
    /**
     * The current status of the job.
     */
    private JobStatus status;
    
    /**
     * A human-readable description of the task being performed.
     */
    private String description;
    
    /**
     * The final result of the job (if successful) or an error message/stack trace (if failed).
     */
    private Object result;
}