package com.ragserver;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Test to verify if Executors.newSingleThreadExecutor() creates daemon threads
 */
public class ThreadPoolTest {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("Test: Verify thread pool behavior");
        System.out.println("========================================\n");

        System.out.println("Creating ExecutorService with Executors.newSingleThreadExecutor()...");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        System.out.println("Submitting a task that waits...\n");
        executor.submit(() -> {
            Thread currentThread = Thread.currentThread();
            System.out.println("Task running in thread: " + currentThread.getName());
            System.out.println("  -> isDaemon: " + currentThread.isDaemon());
            System.out.println("  -> ThreadGroup: " + currentThread.getThreadGroup().getName());

            try {
                System.out.println("\nTask will wait for 5 seconds...");
                Thread.sleep(5000);
                System.out.println("Task completed");
            } catch (InterruptedException e) {
                System.out.println("Task interrupted");
            }
        });

        Thread.sleep(1000);

        System.out.println("\n========== All Active Threads ==========");
        Thread.getAllStackTraces().keySet().forEach(t -> {
            System.out.printf("  %-40s | Daemon: %-5s\n", t.getName(), t.isDaemon());
        });

        System.out.println("\n========================================");
        System.out.println("RESULT:");
        System.out.println("  If pool-X-thread-Y is NON-DAEMON -> MCP threads are NON-DAEMON");
        System.out.println("  If pool-X-thread-Y is DAEMON -> MCP threads are DAEMON");
        System.out.println("========================================");

        System.out.println("\nMain thread will now exit WITHOUT shutdown()...");
        System.out.println("If executor threads are NON-DAEMON, JVM will wait");
        System.out.println("If executor threads are DAEMON, JVM will exit immediately\n");

        // DO NOT shutdown - let's see if JVM waits or exits
        // executor.shutdown();
    }
}
