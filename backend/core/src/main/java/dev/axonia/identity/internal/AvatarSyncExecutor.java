package dev.axonia.identity.internal;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

/**
 * Dedicated bounded executor for avatar photo sync — two platform threads and a small queue of task descriptors. Losing
 * queued work is safe by design: {@code avatar_synced_at} is only stamped on success, so a task dropped here (full
 * queue, redeploy, crash) is simply re-derived by the next provision after the staleness window.
 */
@Component
class AvatarSyncExecutor {

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 200;

    private final ThreadPoolExecutor delegate;

    AvatarSyncExecutor() {
        this(QUEUE_CAPACITY);
    }

    AvatarSyncExecutor(int queueCapacity) {
        delegate = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), Thread.ofPlatform().name("avatar-sync-", 0).factory());
    }

    /** Queues the task; {@code false} when the bounded queue is full — the caller drops, a later window retries. */
    boolean trySubmit(Runnable task) {

        try {
            delegate.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    @PreDestroy
    void shutdown() {

        delegate.shutdown();
    }
}
