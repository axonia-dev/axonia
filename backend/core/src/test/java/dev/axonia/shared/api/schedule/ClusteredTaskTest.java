package dev.axonia.shared.api.schedule;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ClusteredTaskTest {

    @Test
    void aNameMustBeASweepTag() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusteredTask("Billing Sweep", new ClusteredTask.Cron("0 0 * * * ?"), () -> {
                }));
    }

    @Test
    void aSweepTagIsAName() {

        assertThatNoException()
                .isThrownBy(() -> new ClusteredTask("billing-sweep", new ClusteredTask.Cron("0 0 * * * ?"), () -> {
                }));
    }

    @Test
    void anIntervalMustBePositive() {

        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteredTask.Every(Duration.ZERO, Duration.ZERO));
    }

    @Test
    void anInitialDelayMayNotBeNegative() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusteredTask.Every(Duration.ofMinutes(5), Duration.ofSeconds(-1)));
    }

    @Test
    void aCronMustParse() {

        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteredTask.Cron("every day"));
    }

    @Test
    void aSpringStyleCronIsNotAQuartzCron() {

        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteredTask.Cron("0 20 3 * * *"));
    }
}
