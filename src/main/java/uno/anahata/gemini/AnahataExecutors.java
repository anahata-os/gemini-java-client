package uno.anahata.gemini;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnahataExecutors {

    /**
     * Creates a new single-threaded executor specifically for managing the lifecycle of a single chat session.
     * Threads created by this executor are daemon threads to prevent them from blocking application shutdown.
     *
     * @param sessionId The unique identifier for the chat session, used in the thread name.
     * @return A new single-thread ExecutorService.
     */
    public static ExecutorService newChatExecutor(String sessionId) {
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("anahata-chat-" + sessionId + "-thread-%d")
                .daemon(true)
                .priority(Thread.NORM_PRIORITY)
                .build();
        return Executors.newSingleThreadExecutor(factory);
    }
}
