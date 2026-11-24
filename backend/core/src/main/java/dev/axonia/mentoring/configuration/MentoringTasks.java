package dev.axonia.mentoring.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.mentoring.adapter.in.schedule.MeetingReminderSweep;
import dev.axonia.mentoring.adapter.in.schedule.MentorListingSweep;
import dev.axonia.shared.api.schedule.ClusteredTask;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;

/** {@link MeetingReminderSweep} and {@link MentorListingSweep} each run once across the cluster. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MeetingReminderSchedule.class, MentorListingSchedule.class})
public class MentoringTasks {

    @Bean
    ClusteredTask mentoringMeetingReminderTask(MeetingReminderSweep sweep, MeetingReminderSchedule schedule) {

        return new ClusteredTask(Sweep.MENTORING_MEETING_REMINDER.tag(),
                new ClusteredTask.Every(schedule.interval(), schedule.initialDelay()), sweep::tick);
    }

    @Bean
    ClusteredTask mentoringListingTask(MentorListingSweep sweep, MentorListingSchedule schedule) {

        return new ClusteredTask(Sweep.MENTORING_LISTING.tag(),
                new ClusteredTask.Every(schedule.interval(), schedule.initialDelay()), sweep::tick);
    }
}
