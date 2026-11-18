package io.github.luckybearbear.forge.audit.publisher;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import io.github.luckybearbear.forge.starter.audit.AsyncAccessLogPublisher;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AsyncAccessLogPublisher 单测（无容器，stub tx manager/事件发布器）：
 * 事务包裹发布（begin→publish→commit）、队列满丢弃不抛（容量 1+阻塞消费）、
 * 停机后 publish 计数不抛、优雅停机（在途事件于 grace 窗口完成，余量丢弃计数）、
 * 单事件失败不杀消费循环。容器全链见 AuditStarterIntegrationTest。
 */
class AsyncAccessLogPublisherTest {

    private static AccessLogRecorded event(String path) {
        return new AccessLogRecorded(null, null, "GET", path, 200, 1L, null, Instant.now());
    }

    /** 事务边界记录 stub：getTransaction/commit/rollback 各落一笔 trace。 */
    private static final class RecordingTxManager implements PlatformTransactionManager {
        final List<String> trace = new CopyOnWriteArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            trace.add("begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            trace.add("commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            trace.add("rollback");
        }
    }

    @Test
    void publishesEventInsideTransactionAndCountsProcessed() {
        RecordingTxManager txManager = new RecordingTxManager();
        ApplicationEventPublisher publisher =
                event -> txManager.trace.add(((AccessLogRecorded) event).path());
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(publisher, txManager, 8);

        async.publish(event("/probe"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> async.processedCount() == 1);
        assertThat(txManager.trace).containsExactly("begin", "/probe", "commit");
        assertThat(async.droppedCount()).isZero();
        async.shutdown();
    }

    @Test
    void queueFullDropsExcessWithoutThrowing() throws InterruptedException {
        CountDownLatch consuming = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Object> firstEvent = new AtomicReference<>();
        // 阻塞消费 stub：首事件进入发布回调后挂起（占住消费线程）
        ApplicationEventPublisher blocking = event -> {
            firstEvent.compareAndSet(null, event);
            consuming.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(blocking,
                new RecordingTxManager(), 1);

        assertThatCode(() -> {
            async.publish(event("/first"));  // 被消费线程取走，消费中（阻塞）
            consuming.await(5, TimeUnit.SECONDS);
            async.publish(event("/queued"));  // 占满容量 1
            async.publish(event("/drop-1"));  // 满 → dropped=1
            async.publish(event("/drop-2"));  // 满 → dropped=2
        }).doesNotThrowAnyException();

        assertThat(async.droppedCount()).isEqualTo(2);
        release.countDown();
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> async.processedCount() == 2); // first + queued 均经事务发布
        async.shutdown();
    }

    @Test
    void publishAfterShutdownIsCountedAsDroppedAndNeverThrows() {
        RecordingTxManager txManager = new RecordingTxManager();
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(
                event -> { }, txManager, 8);
        async.shutdown();

        assertThatCode(() -> async.publish(event("/late"))).doesNotThrowAnyException();

        assertThat(async.droppedCount()).isEqualTo(1);
        assertThat(async.processedCount()).isZero();
    }

    @Test
    void gracefulShutdownCompletesInFlightEventAndDropsQueueRemainder() throws Exception {
        CountDownLatch consuming = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ApplicationEventPublisher blocking = event -> {
            consuming.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(blocking,
                new RecordingTxManager(), 4);

        async.publish(event("/in-flight"));
        consuming.await(5, TimeUnit.SECONDS); // 在途：消费线程阻塞于发布回调内
        async.publish(event("/remainder"));   // 队内余量（不再被消费）

        Thread stopper = new Thread(async::shutdown);
        stopper.start();
        // 停机标志落地后才放行：否则消费线程可能回环取走余量事件（processed=2）——竞态排除
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !async.isRunning());
        release.countDown(); // 停机 grace 窗口内放行在途事件
        stopper.join(10_000);

        assertThat(stopper.isAlive()).isFalse(); // 停机在 grace 窗口内收敛
        assertThat(async.processedCount()).isEqualTo(1); // 在途事件完成（未因停机中断丢弃）
        assertThat(async.droppedCount()).isEqualTo(1);   // 余量事件计数丢弃
    }

    @Test
    void consumeFailureDoesNotKillLoop() {
        RecordingTxManager txManager = new RecordingTxManager();
        ApplicationEventPublisher flaky = event -> {
            if (((AccessLogRecorded) event).path().equals("/bad")) {
                throw new IllegalStateException("publish boom");
            }
            txManager.trace.add(((AccessLogRecorded) event).path());
        };
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(flaky, txManager, 8);

        assertThatCode(() -> async.publish(event("/bad"))).doesNotThrowAnyException();
        async.publish(event("/good"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(async.processedCount()).isEqualTo(1)); // 循环存活，好事件照常处理
        // 失败事务 begin→rollback 收敛；好事件 begin→发布→commit（循环未被前者杀死）
        assertThat(txManager.trace).containsExactly("begin", "rollback", "begin", "/good", "commit");
        async.shutdown();
    }

    /**
     * 消费中 Error（review Important #1）：publishEvent 抛 OutOfMemoryError——只捕 Exception 时
     * Error 穿透静默击杀消费循环（running 仍 true、队列堆满持续丢弃直到重启）；加固后循环存活，
     * 后续事件照常经事务发布。
     */
    @Test
    void errorInConsumeDoesNotKillLoop() {
        RecordingTxManager txManager = new RecordingTxManager();
        ApplicationEventPublisher erroring = event -> {
            if (((AccessLogRecorded) event).path().equals("/oom")) {
                throw new OutOfMemoryError("simulated oom"); // Error：穿透 Exception 捕获的回归载体
            }
            txManager.trace.add(((AccessLogRecorded) event).path());
        };
        AsyncAccessLogPublisher async = new AsyncAccessLogPublisher(erroring, txManager, 8);

        assertThatCode(() -> async.publish(event("/oom"))).doesNotThrowAnyException();
        async.publish(event("/after-oom"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(async.processedCount())
                        .isEqualTo(1)); // 循环未被 Error 击杀：后续事件照常处理
        assertThat(txManager.trace).containsExactly("begin", "rollback", "begin", "/after-oom", "commit");
        async.shutdown();
    }
}
