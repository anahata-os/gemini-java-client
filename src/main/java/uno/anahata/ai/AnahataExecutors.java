/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * A utility class for creating and managing named {@link ExecutorService} instances
 * for various asynchronous tasks within the Anahata AI framework. This ensures
 * that all threads are properly named, configured as daemons, and managed
 * consistently.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnahataExecutors {

    /**
     * A shared, fixed-size thread pool optimized for CPU-intensive, parallelizable tasks.
     * The size is set to the number of available processor cores to maximize throughput
     * without performance degradation from excessive context switching.
     */
    public static final ExecutorService SHARED_CPU_EXECUTOR = newFixedThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), "shared-cpu");

    /**
     * Creates a new cached thread pool. Threads in this pool are created as needed
     * and will be reused if available. This type of pool is suitable for executing
     * many short-lived asynchronous tasks. Threads are created as daemon threads
     * to prevent them from blocking application shutdown.
     *
     * @param threadPrefix The prefix for the thread name, used for identification in logs and debuggers.
     * @return A new cached thread pool ExecutorService.
     */
    public static ExecutorService newCachedThreadPoolExecutor(String threadPrefix) {
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("anahata-ai-cached-" + threadPrefix + "-thread-%d")
                .daemon(true)
                .priority(Thread.NORM_PRIORITY)
                .build();
        return Executors.newCachedThreadPool(factory);
    }

    /**
     * Creates a new thread pool with a fixed number of threads. This is ideal for
     * CPU-intensive tasks where the number of concurrent operations should be
     * limited to the number of available cores to avoid performance degradation
     * from excessive context switching. Threads are created as daemon threads.
     *
     * @param size         The fixed number of threads in the pool.
     * @param threadPrefix The prefix for the thread name, used for identification.
     * @return A new fixed-size thread pool ExecutorService.
     */
    public static ExecutorService newFixedThreadPoolExecutor(int size, String threadPrefix) {
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("anahata-ai-fixed-" + threadPrefix + "-thread-%d")
                .daemon(true)
                .priority(Thread.NORM_PRIORITY)
                .build();
        return Executors.newFixedThreadPool(size, factory);
    }
}
