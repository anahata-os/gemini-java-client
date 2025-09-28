/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 * @author pablo
 */
public class Executors {
    private static volatile int idx = 0;
    public static ExecutorService cachedThreadPool = java.util.concurrent.Executors.newCachedThreadPool((Runnable r) -> {
        Thread t = new Thread (r, "GeminiClient-" + nextId());
        t.setDaemon(false);
        return t;
    });
    
    private static synchronized int nextId() {
        return idx++;
    }
}
