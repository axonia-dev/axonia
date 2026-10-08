package dev.axonia.config;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SchedulerConfigTest {

    private static final ClusteredTask.Every HOURLY = new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO);

    @Test
    void tasksAreKeyedByName() {

        var sweep = new ClusteredTask("billing-sweep", HOURLY, () -> {
        });

        assertThat(SchedulerConfig.byName(List.of(sweep))).containsEntry("billing-sweep", sweep);
    }

    @Test
    void twoTasksWithOneNameRefuseTheStart() {

        var tasks = List.of(new ClusteredTask("billing-sweep", HOURLY, () -> {
        }), new ClusteredTask("billing-sweep", HOURLY, () -> {
        }));

        assertThatIllegalStateException().isThrownBy(() -> SchedulerConfig.byName(tasks))
                .withMessageContaining("billing-sweep");
    }
}
