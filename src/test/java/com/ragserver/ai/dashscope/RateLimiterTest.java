package com.ragserver.ai.dashscope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiter单元测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("RateLimiter测试")
class RateLimiterTest {

    @Test
    @DisplayName("应能创建限流器")
    void testCreate() {
        RateLimiter limiter = new RateLimiter(10);
        assertEquals(10, limiter.getPermitsPerSecond());
        assertEquals(20, limiter.getMaxPermits()); // 桶容量 = QPS * 2
    }

    @Test
    @DisplayName("创建限流器时QPS必须大于0")
    void testCreateWithInvalidQps() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(-1);
        });
    }

    @Test
    @DisplayName("应能获取单个令牌")
    void testAcquireSingle() {
        RateLimiter limiter = new RateLimiter(10);

        // 第一次获取应该立即返回
        long waitTime = limiter.acquire();
        assertTrue(waitTime >= 0, "等待时间应该非负");
    }

    @Test
    @DisplayName("应能在限流时阻塞等待")
    void testAcquireBlocking() {
        RateLimiter limiter = new RateLimiter(10); // 10 QPS = 每100ms一个令牌

        long start = System.currentTimeMillis();

        // 快速获取2个令牌
        limiter.acquire();
        limiter.acquire();

        long duration = System.currentTimeMillis() - start;

        // 第二次获取应该等待约100ms
        assertTrue(duration >= 80, "应该等待约100ms，实际：" + duration + "ms");
    }

    @Test
    @DisplayName("应能尝试获取令牌（不阻塞）")
    void testTryAcquire() {
        RateLimiter limiter = new RateLimiter(10);

        // 第一次应该成功
        assertTrue(limiter.tryAcquire(), "第一次tryAcquire应该成功");

        // 立即再次尝试应该失败（没有足够时间生成新令牌）
        boolean result = limiter.tryAcquire(10, TimeUnit.MILLISECONDS);
        assertFalse(result, "立即tryAcquire应该失败");
    }

    @Test
    @DisplayName("应能在超时时间内获取令牌")
    void testTryAcquireWithTimeout() {
        RateLimiter limiter = new RateLimiter(10); // 10 QPS = 每100ms一个令牌

        // 第一次获取
        assertTrue(limiter.tryAcquire());

        // 等待足够长时间应该成功
        boolean result = limiter.tryAcquire(150, TimeUnit.MILLISECONDS);
        assertTrue(result, "在150ms内应该能获取到令牌");
    }

    @Test
    @DisplayName("应能限制并发请求速率")
    void testConcurrentRateLimiting() throws InterruptedException {
        int qps = 10;
        RateLimiter limiter = new RateLimiter(qps);

        int concurrency = 50; // 50个并发线程
        int requestsPerThread = 2; // 每个线程2次请求
        int totalRequests = concurrency * requestsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger completedRequests = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // 提交并发任务
        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    limiter.acquire();
                    completedRequests.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成
        latch.await(20, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        // 验证所有请求都完成
        assertEquals(totalRequests, completedRequests.get(), "所有请求应该完成");

        // 验证平均速率不超过QPS
        // 100个请求，10 QPS，理论上需要10秒
        // 由于桶容量和启动延迟，实际时间会少一些
        double actualQps = totalRequests * 1000.0 / duration;
        assertTrue(actualQps <= qps * 1.5,
            String.format("实际QPS(%.2f)不应该显著超过限制(%d)", actualQps, qps));

        System.out.printf("并发测试：%d个请求，耗时%dms，实际QPS=%.2f%n",
            totalRequests, duration, actualQps);
    }

    @Test
    @DisplayName("应能正确处理多个令牌的获取")
    void testAcquireMultiple() {
        RateLimiter limiter = new RateLimiter(10);

        long start = System.currentTimeMillis();

        // 获取5个令牌
        limiter.acquire(5);

        long duration = System.currentTimeMillis() - start;

        // 5个令牌 = 500ms（在10 QPS下）
        // 由于桶初始有20个令牌，第一次应该立即返回
        assertTrue(duration < 100, "第一次获取应该很快");

        // 再次获取5个令牌
        start = System.currentTimeMillis();
        limiter.acquire(5);
        duration = System.currentTimeMillis() - start;

        // 这次应该等待约500ms
        assertTrue(duration >= 400, "第二次获取应该等待约500ms，实际：" + duration + "ms");
    }

    @Test
    @DisplayName("获取令牌数必须大于0")
    void testAcquireInvalidPermits() {
        RateLimiter limiter = new RateLimiter(10);

        assertThrows(IllegalArgumentException.class, () -> {
            limiter.acquire(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            limiter.acquire(-1);
        });
    }

    @Test
    @DisplayName("应能支持突发流量（桶容量）")
    void testBurstTraffic() {
        RateLimiter limiter = new RateLimiter(10); // 10 QPS，桶容量20

        long start = System.currentTimeMillis();

        // 连续获取15个令牌（在桶容量内）
        for (int i = 0; i < 15; i++) {
            limiter.acquire();
        }

        long duration = System.currentTimeMillis() - start;

        // 由于桶初始满（20个令牌），前20个令牌应该很快获取
        // 但我们只取15个，所以应该很快（但可能需要等待一些令牌生成）
        // 15个令牌，如果桶初始有20个，理论上立即返回
        // 实际上由于同步开销和令牌生成机制，可能需要一些时间
        assertTrue(duration < 2000, "突发流量应该快速处理（<2秒），实际：" + duration + "ms");
    }

    @Test
    @DisplayName("压力测试：100并发请求")
    void testStressTest() throws InterruptedException, ExecutionException {
        int qps = 10;
        RateLimiter limiter = new RateLimiter(qps);

        int concurrency = 100;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        long startTime = System.currentTimeMillis();
        List<Future<?>> futures = new ArrayList<>();

        // 提交100个并发请求
        for (int i = 0; i < concurrency; i++) {
            Future<?> future = executor.submit(() -> {
                limiter.acquire();
            });
            futures.add(future);
        }

        // 等待所有完成
        for (Future<?> future : futures) {
            future.get();
        }

        long duration = System.currentTimeMillis() - startTime;
        double actualQps = concurrency * 1000.0 / duration;

        executor.shutdown();

        System.out.printf("压力测试：%d个并发请求，耗时%dms，实际QPS=%.2f%n",
            concurrency, duration, actualQps);

        // 验证速率控制生效
        assertTrue(actualQps <= qps * 1.5,
            String.format("实际QPS(%.2f)不应该显著超过限制(%d)", actualQps, qps));

        // 100个请求，10 QPS，应该需要至少8秒（考虑桶容量）
        assertTrue(duration >= 8000, "100个请求在10 QPS下应该需要至少8秒");
    }
}
