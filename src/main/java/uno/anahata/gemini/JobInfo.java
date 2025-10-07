package uno.anahata.gemini;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A simple POJO representing the state and result of an asynchronous background job.
 * @author Anahata
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobInfo {
    public enum JobStatus {
        STARTED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private String jobId;
    private JobStatus status;
    private String description;
    private Object result; // Can hold the final output or an error message
}
