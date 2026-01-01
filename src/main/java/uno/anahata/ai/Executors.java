/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Provides a shared, cached thread pool for general-purpose asynchronous tasks.
 * <p>
 * Threads created by this executor are named with an "Anahata-" prefix and a
 * sequential ID. Unlike {@link AnahataExecutors}, these threads are NOT
 * configured as daemons by default.
 * </p>
 * 
 * @author anahata
 */
public class Executors {
    private static volatile int idx = 0;
    
    /**
     * A shared {@link ExecutorService} that creates new threads as needed and
     * reuses them when available.
     */
    public static ExecutorService cachedThreadPool = java.util.concurrent.Executors.newCachedThreadPool((Runnable r) -> {
        Thread t = new Thread (r, "Anahata-" + nextId());
        t.setDaemon(false);
        return t;
    });
    
    private static synchronized int nextId() {
        return idx++;
    }
}