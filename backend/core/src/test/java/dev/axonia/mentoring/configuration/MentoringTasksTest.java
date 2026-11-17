package dev.axonia.mentoring.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.mentoring.adapter.in.schedule.MeetingReminderSweep;
import dev.axonia.shared.api.schedule.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MentoringTasksTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MeetingReminderSweep.class, () -> mock(MeetingReminderSweep.class))
            .withUserConfiguration(MentoringTasks.class);

    @Test
    void remindersDefaultToHourlyAfterSevenMinutes() {

        runner.run(context -> assertThat(schedule(context))
                .isEqualTo(new ClusteredTask.Every(Duration.ofHours(1), Duration.ofMinutes(7))));
    }

    @Test
    void eachDelayReadsItsOwnKey() {

        runner.withPropertyValues("axonia.mentoring.meeting-reminder.interval=30m",
                "axonia.mentoring.meeting-reminder.initial-delay=1m")
                .run(context -> assertThat(schedule(context))
                        .isEqualTo(new ClusteredTask.Every(Duration.ofMinutes(30), Duration.ofMinutes(1))));
    }

    @Test
    void theTaskIsNamedAfterItsSweepAndRunsItsTick() {

        runner.run(context -> {
            var task = context.getBean("mentoringMeetingReminderTask", ClusteredTask.class);

            task.run().run();

            assertThat(task.name()).isEqualTo("mentoring-meeting-reminder");
            verify(context.getBean(MeetingReminderSweep.class)).tick();
        });
    }

    private static ClusteredTask.Schedule schedule(AssertableApplicationContext context) {

        return context.getBean("mentoringMeetingReminderTask", ClusteredTask.class).schedule();
    }
}
