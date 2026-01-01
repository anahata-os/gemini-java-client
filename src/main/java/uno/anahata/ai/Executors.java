/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 * @author anahata
 */
public class Executors {
    private static volatile int idx = 0;
    public static ExecutorService cachedThreadPool = java.util.concurrent.Executors.newCachedThreadPool((Runnable r) -> {
        Thread t = new Thread (r, "Anahata-" + nextId());
        t.setDaemon(false);
        return t;
    });
    
    private static synchronized int nextId() {
        return idx++;
    }
}