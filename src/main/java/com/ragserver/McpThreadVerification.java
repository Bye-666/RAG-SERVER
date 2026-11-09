package com.ragserver;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Set;

/**
 * Standalone program to verify MCP Server background thread behavior
 */
public class McpThreadVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("Test: Verify MCP Server background thread behavior");
        System.out.println("========================================");

        Set<Thread> threadsBefore = Thread.getAllStackTraces().keySet();
        System.out.println("Active threads BEFORE: " + threadsBefore.size());

        // IMPORTANT: Use System.in and System.out to simulate real scenario
        // ByteArrayInputStream with empty data causes immediate EOF, threads exit immediately
        System.out.println("\nCreating StdioServerTransportProvider with System.in/out...");
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper()
        );

        System.out.println("Building McpSyncServer...");
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();

        System.out.println("MCP Server built successfully\n");
        System.out.println("Waiting for threads to start...");

        // Wait for threads to fully initialize
        Thread.sleep(2000);

        // List all threads to find stdio threads
        System.out.println("\n========== All Active Threads ==========");
        Thread.getAllStackTraces().keySet().forEach(t -> {
            String name = t.getName();
            // Highlight stdio-related threads
            if (name.contains("stdio")) {
                System.out.printf(">>> %-40s | Daemon: %-5s | State: %s <<<\n",
                    name, t.isDaemon(), t.getState());
            } else {
                System.out.printf("    %-40s | Daemon: %-5s\n", name, t.isDaemon());
            }
        });
        System.out.println();

        Set<Thread> threadsAfter = Thread.getAllStackTraces().keySet();
        System.out.println("Active threads AFTER: " + threadsAfter.size());
        System.out.println("New threads created: " + (threadsAfter.size() - threadsBefore.size()));

        System.out.println("\n========== New Thread Details ==========");
        int nonDaemonCount = 0;
        for (Thread thread : threadsAfter) {
            if (!threadsBefore.contains(thread)) {
                boolean isDaemon = thread.isDaemon();
                System.out.printf("  Thread: %-30s | Daemon: %-5s | Alive: %-5s | State: %s%n",
                        thread.getName(),
                        isDaemon,
                        thread.isAlive(),
                        thread.getState());
                if (!isDaemon && thread.isAlive()) {
                    nonDaemonCount++;
                }
            }
        }

        System.out.println("\n========== VERIFICATION RESULT ==========");
        System.out.println("Non-daemon threads: " + nonDaemonCount);

        if (nonDaemonCount > 0) {
            System.out.println("\n[CASE A] Background threads are NON-DAEMON");
            System.out.println("  -> These threads will prevent JVM from exiting");
            System.out.println("  -> Current understanding is CORRECT");
            System.out.println("  -> Can REMOVE latch.await() blocking");
            System.out.println("  -> Spring Boot can start normally");
        } else {
            System.out.println("\n[CASE B] Background threads are DAEMON");
            System.out.println("  -> These threads will NOT prevent JVM from exiting");
            System.out.println("  -> Current understanding is WRONG");
            System.out.println("  -> MUST KEEP latch.await() blocking");
            System.out.println("  -> Need alternative approach for Spring Boot");
        }

        System.out.println("\n========================================");
        System.out.println("Test will now exit in 2 seconds...");
        System.out.println("If non-daemon threads exist, you'll see delay before exit");
        System.out.println("========================================");

        Thread.sleep(2000);
        System.out.println("Exiting...");
    }
}
