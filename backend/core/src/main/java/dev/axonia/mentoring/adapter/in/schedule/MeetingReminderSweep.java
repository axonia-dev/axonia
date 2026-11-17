package dev.axonia.mentoring.adapter.in.schedule;

import org.springframework.stereotype.Component;

import dev.axonia.mentoring.application.MeetingReminders;
import dev.axonia.mentoring.configuration.MentoringTasks;
import dev.axonia.organization.api.OrganizationIds;
import dev.axonia.shared.api.schedule.SweepHeartbeat;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the meeting reminders that are due, one organization scope at a time. {@link MentoringTasks} runs {@link #tick}
 * once across the cluster.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingReminderSweep {

    private final OrganizationIds organizationIds;
    private final MeetingReminders reminders;
    private final SweepHeartbeat heartbeat;

    public void tick() {

        heartbeat.record(Sweep.MENTORING_MEETING_REMINDER, () -> {
            for (var organizationId : organizationIds.all()) {
                try {
                    OrganizationScope.runAs(organizationId, () -> reminders.remindDue(organizationId));
                } catch (RuntimeException e) {
                    log.warn("Meeting reminders failed for organization {}: {}", organizationId,
                            e.getClass().getSimpleName());
                }
            }
        });
    }
}
