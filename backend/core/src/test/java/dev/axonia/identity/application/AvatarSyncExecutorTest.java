package dev.axonia.identity.application;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarSyncExecutorTest {

    @Test
    void trySubmit_runsTheTask() throws Exception {

        // given
        var sut = new AvatarSyncExecutor();
        var ran = new CountDownLatch(1);

        // when
        var accepted = sut.trySubmit(ran::countDown);

        // then
        assertThat(accepted).isTrue();
        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        sut.shutdown();
    }

    @Test
    void trySubmit_returnsFalseWhenSaturated() throws Exception {

        // given — queue capacity 1; both threads blocked, the queue slot taken
        var sut = new AvatarSyncExecutor(1);
        var release = new CountDownLatch(1);
        var blocked = new CountDownLatch(2);
        Runnable blocker = () -> {
            blocked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        assertThat(sut.trySubmit(blocker)).isTrue();
        assertThat(sut.trySubmit(blocker)).isTrue();
        assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sut.trySubmit(() -> {
        })).isTrue();

        // when — threads and queue are all occupied
        var accepted = sut.trySubmit(() -> {
        });

        // then
        assertThat(accepted).isFalse();
        release.countDown();
        sut.shutdown();
    }
}
