package io.github.luckybearbear.forge.starter.audit;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 访问日志异步发布器（spec §6.6，P3 Task 2）：{@link ApiAccessLogFilter} 的 finally 段
 * 仅入队（微秒级，请求线程不再付每请求事务税），单一虚拟线程消费循环以 TransactionTemplate
 * 包裹 publishEvent——事务语义与旧同步路径等价（Modulith @ApplicationModuleListener 为
 * AFTER_COMMIT 投递，无事务发布直接丢弃；filter 出口本无自然事务，TransactionTemplate 自开
 * REQUIRED 空事务，挪出请求线程不改变语义）。
 *
 * <p><b>背压=丢弃</b>：有界队列（forge.audit.queue-capacity，缺省 1024）满即丢——
 * 访问日志缺失可容忍、请求不可阻塞（审计不反噬）；丢弃计数 {@link #droppedCount()}，
 * log.warn 节流（首条+每 1000 条）。
 *
 * <p><b>停机窗口</b>：@PreDestroy 停止接收→shutdown→awaitTermination(3s)（在途事件在
 * grace 窗口内完成提交），队内余量计数丢弃并告警——停机可能丢尾部若干条访问日志（语义
 * 取舍，README）。停机后 publish 不抛（计 dropped）。
 *
 * <p><b>单事件失败不杀循环</b>：消费侧异常 warn 后继续（发布器存活优先）；错误侧
 * （ErrorLogRecorded）发布点在 GlobalExceptionHandler 保持请求线程同步——低频路径，
 * 不值得异步基建（取舍见其 javadoc）。
 */
public class AsyncAccessLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(AsyncAccessLogPublisher.class);

    /** 停机 grace：在途事件事务提交窗口（超时则 shutdownNow 收割）。 */
    static final long SHUTDOWN_GRACE_SECONDS = 3;

    /** 消费循环 poll 超时：停机标志至多此后一拍生效。 */
    private static final long POLL_TIMEOUT_MS = 500;

    /** 丢弃告警节流间隔：首条与每该条数一条 warn。 */
    private static final long DROP_WARN_INTERVAL = 1_000;

    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate publishTransaction;
    private final BlockingQueue<AccessLogRecorded> queue;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public AsyncAccessLogPublisher(ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager, int queueCapacity) {
        this.eventPublisher = eventPublisher;
        this.publishTransaction = new TransactionTemplate(transactionManager);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.executor = Executors.newVirtualThreadPerTaskExecutor(); // JDK 25 虚拟线程
        this.executor.execute(this::consumeLoop);
    }

    /**
     * 入队发布（请求线程调用）：满/停机即丢弃计数，绝不阻塞、绝不抛（审计不反噬请求）。
     */
    public void publish(AccessLogRecorded event) {
        if (!running.get()) {
            countDrop("publisher stopped");
            return;
        }
        if (!queue.offer(event)) {
            countDrop("queue full (capacity=" + queue.size() + ")");
        }
    }

    /** 经异步通道完成事务发布的事件数（测试异步判别用）。 */
    public long processedCount() {
        return processed.get();
    }

    /** 累计丢弃数（队列满+停机窗口，测试判别用）。 */
    public long droppedCount() {
        return dropped.get();
    }

    /** 是否仍在接收（停机后 false——publish 将计丢弃；测试停机时序判别用）。 */
    public boolean isRunning() {
        return running.get();
    }

    private void countDrop(String reason) {
        long total = dropped.incrementAndGet();
        if (total == 1 || total % DROP_WARN_INTERVAL == 0) {
            log.warn("access log event dropped: {} (total dropped={})", reason, total);
        }
    }

    private void consumeLoop() {
        while (running.get()) {
            AccessLogRecorded event;
            try {
                event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // 停机收割：退出即余量走丢弃计数
                return;
            }
            if (event != null) {
                publishInTransaction(event);
            }
        }
    }

    private void publishInTransaction(AccessLogRecorded event) {
        try {
            publishTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
            processed.incrementAndGet();
        } catch (Exception ex) {
            // 审计不反噬 + 循环存活：单事件登记失败 warn 后继续消费下一事件
            log.warn("access log event publish failed: {}", ex.getMessage(), ex);
        }
    }

    /** 优雅停机（@PreDestroy）：停止接收→在途事件 grace(3s) 内完成→余量计数丢弃。 */
    @PreDestroy
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return; // 幂等（重复停机/已停）
        }
        executor.shutdown(); // 不先 shutdownNow：在途事件事务在 grace 窗口内自然提交
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        int remainder = queue.size();
        if (remainder > 0) {
            queue.clear();
            log.warn("access log publisher stopped: dropped {} queued events (total dropped={})",
                    remainder, dropped.addAndGet(remainder));
        } else {
            log.info("access log publisher stopped: processed={}, dropped={}",
                    processed.get(), dropped.get());
        }
    }
}
