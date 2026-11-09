package com.ragserver.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;

/**
 * Test to verify MCP Server background thread behavior
 */
public class McpServerThreadTest {

    private static final Logger log = LoggerFactory.getLogger(McpServerThreadTest.class);

    @Test
    public void testBackgroundThreadBehavior() throws Exception {
        log.info("========================================");
        log.info("Test Start: Verify MCP Server background thread behavior");
        log.info("========================================");

        Set<Thread> threadsBefore = Thread.getAllStackTraces().keySet();
        log.info("Active threads before test: {}", threadsBefore.size());

        ByteArrayInputStream mockStdin = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream mockStdout = new ByteArrayOutputStream();

        log.info("Creating StdioServerTransportProvider...");
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                mockStdin,
                mockStdout
        );

        log.info("Building McpSyncServer...");
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();

        log.info("MCP Server built successfully");

        Thread.sleep(500);

        Set<Thread> threadsAfter = Thread.getAllStackTraces().keySet();
        log.info("Active threads after test: {}", threadsAfter.size());
        log.info("New threads created: {}", threadsAfter.size() - threadsBefore.size());

        log.info("\n========== New Thread Information ==========");
        for (Thread thread : threadsAfter) {
            if (!threadsBefore.contains(thread)) {
                log.info("Thread name: {}, isDaemon: {}, isAlive: {}, State: {}",
                        thread.getName(),
                        thread.isDaemon(),
                        thread.isAlive(),
                        thread.getState());
            }
        }

        boolean hasNonDaemonThreads = threadsAfter.stream()
                .filter(t -> !threadsBefore.contains(t))
                .anyMatch(t -> !t.isDaemon() && t.isAlive());

        log.info("\n========== Verification Result ==========");
        log.info("Has non-daemon threads: {}", hasNonDaemonThreads);

        if (hasNonDaemonThreads) {
            log.info("CONCLUSION: Background threads are NON-DAEMON, will prevent JVM exit");
            log.info("RESULT: Current understanding is CORRECT - no need for latch.await()");
        } else {
            log.info("CONCLUSION: Background threads are DAEMON, JVM will exit immediately");
            log.info("RESULT: Current understanding is WRONG - must keep latch.await()");
        }

        log.info("========================================");
    }
}
