package com.ragserver.ai.dashscope;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API限流器（令牌桶算法）
 *
 * <p>防止超过DashScope API的QPS限制，基于令牌桶算法实现。</p>
 *
 * <h3>令牌桶算法原理</h3>
 * <ul>
 *   <li>桶中有固定容量的令牌</li>
 *   <li>令牌以恒定速率生成（例如10 QPS = 每100ms生成1个令牌）</li>
 *   <li>每次请求消耗1个令牌</li>
 *   <li>没有令牌时请求阻塞等待</li>
 * </ul>
 *
 * <h3>特点</h3>
 * <ul>
 *   <li>允许短时间的突发流量（桶容量 > 1）</li>
 *   <li>平滑限流，避免请求堆积</li>
 *   <li>线程安全</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建限流器：10 QPS
 * RateLimiter limiter = new RateLimiter(10);
 *
 * // 在API调用前获取令牌（阻塞等待）
 * limiter.acquire();
 * callDashScopeAPI();
 *
 * // 或者尝试获取令牌（不阻塞）
 * if (limiter.tryAcquire(100, TimeUnit.MILLISECONDS)) {
 *     callDashScopeAPI();
 * } else {
 *     System.out.println("限流中，请稍后重试");
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
public class RateLimiter {

    /**
     * 每秒生成的令牌数（QPS）
     */
    private final int permitsPerSecond;

    /**
     * 桶容量（最大令牌数）
     *
     * <p>设置为QPS的2倍，允许短时间突发流量。</p>
     */
    private final int maxPermits;

    /**
     * 当前令牌数（原子操作）
     *
     * <p>使用纳秒精度存储，避免浮点数精度问题。</p>
     */
    private final AtomicLong storedPermitsNanos;

    /**
     * 上次更新时间（纳秒）
     */
    private volatile long nextFreeTicketNanos;

    /**
     * 生成1个令牌的时间间隔（纳秒）
     */
    private final long intervalNanos;

    /**
     * 创建限流器
     *
     * @param permitsPerSecond 每秒允许的请求数（QPS）
     */
    public RateLimiter(int permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("QPS必须大于0，当前：" + permitsPerSecond);
        }

        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = permitsPerSecond * 2; // 桶容量 = QPS * 2
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / permitsPerSecond;
        this.storedPermitsNanos = new AtomicLong(maxPermits * intervalNanos);
        this.nextFreeTicketNanos = System.nanoTime();

        log.info("RateLimiter初始化：QPS={}, 桶容量={}, 令牌间隔={}ns",
            permitsPerSecond, maxPermits, intervalNanos);
    }

    /**
     * 获取1个令牌（阻塞等待）
     *
     * <p>如果没有可用令牌，线程会阻塞直到获得令牌。</p>
     *
     * @return 等待时间（毫秒）
     */
    public long acquire() {
        return acquire(1);
    }

    /**
     * 获取指定数量的令牌（阻塞等待）
     *
     * @param permits 需要的令牌数
     * @return 等待时间（毫秒）
     */
    public long acquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("令牌数必须大于0");
        }

        long waitNanos = reserve(permits);

        if (waitNanos > 0) {
            long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitNanos);
            log.debug("限流中，等待{}ms后继续", waitMillis);

            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待令牌被中断", e);
            }

            return waitMillis;
        }

        return 0;
    }

    /**
     * 尝试获取1个令牌（不阻塞）
     *
     * <p>如果立即有可用令牌则返回true，否则返回false。</p>
     *
     * @return 是否成功获取令牌
     */
    public boolean tryAcquire() {
        return tryAcquire(1, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试在指定时间内获取1个令牌
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否成功获取令牌
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 尝试在指定时间内获取指定数量的令牌
     *
     * @param permits 需要的令牌数
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否成功获取令牌
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        if (permits <= 0) {
            throw new IllegalArgumentException("令牌数必须大于0");
        }

        long timeoutNanos = unit.toNanos(timeout);
        long waitNanos = reserve(permits);

        if (waitNanos > timeoutNanos) {
            log.debug("无法在{}ms内获取{}个令牌，需要等待{}ms",
                TimeUnit.NANOSECONDS.toMillis(timeoutNanos),
                permits,
                TimeUnit.NANOSECONDS.toMillis(waitNanos));
            return false;
        }

        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return true;
    }

    /**
     * 预定令牌（核心算法）
     *
     * <p>返回需要等待的时间（纳秒）。</p>
     *
     * @param permits 需要的令牌数
     * @return 等待时间（纳秒）
     */
    private synchronized long reserve(int permits) {
        long nowNanos = System.nanoTime();

        // 补充令牌
        resync(nowNanos);

        // 计算需要等待的时间
        long momentAvailable = nextFreeTicketNanos;
        long permitsNanos = permits * intervalNanos;

        // 更新下次可用时间
        nextFreeTicketNanos = Math.max(momentAvailable, nowNanos) + permitsNanos;

        return Math.max(momentAvailable - nowNanos, 0);
    }

    /**
     * 重新同步令牌数（补充令牌）
     *
     * @param nowNanos 当前时间（纳秒）
     */
    private void resync(long nowNanos) {
        // 如果当前时间 > 下次可用时间，说明可以补充令牌
        if (nowNanos > nextFreeTicketNanos) {
            long newPermitsNanos = nowNanos - nextFreeTicketNanos;
            long maxPermitsNanos = maxPermits * intervalNanos;

            // 更新令牌数（不超过最大值）
            long currentPermits = storedPermitsNanos.get();
            long newStoredPermits = Math.min(currentPermits + newPermitsNanos, maxPermitsNanos);
            storedPermitsNanos.set(newStoredPermits);

            nextFreeTicketNanos = nowNanos;
        }
    }

    /**
     * 获取当前QPS配置
     *
     * @return 每秒允许的请求数
     */
    public int getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 获取桶容量
     *
     * @return 最大令牌数
     */
    public int getMaxPermits() {
        return maxPermits;
    }

    /**
     * 获取当前可用令牌数（估算）
     *
     * @return 当前令牌数
     */
    public int getAvailablePermits() {
        long nowNanos = System.nanoTime();
        long availableNanos = Math.max(0, nextFreeTicketNanos - nowNanos);
        return (int) (storedPermitsNanos.get() / intervalNanos) - (int) (availableNanos / intervalNanos);
    }
}
