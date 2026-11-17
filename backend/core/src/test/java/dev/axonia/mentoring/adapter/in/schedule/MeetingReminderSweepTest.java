package dev.axonia.mentoring.adapter.in.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.mentoring.application.MeetingReminders;
import dev.axonia.organization.api.OrganizationIds;
import dev.axonia.shared.api.schedule.SweepHeartbeat;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The clustered scheduler never starts in a test context, so this is the only caller of the sweep's tick. */
@ExtendWith(MockitoExtension.class)
class MeetingReminderSweepTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T08:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OrganizationIds organizationIds;
    @Mock
    private MeetingReminders reminders;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private MeetingReminderSweep sut;

    @BeforeEach
    void buildTheSweep() {

        sut = new MeetingReminderSweep(organizationIds, reminders, new SweepHeartbeat(meters, CLOCK));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void oneFailingOrganizationDoesNotStopTheNext() {

        // given
        when(organizationIds.all()).thenReturn(List.of("acme", "default"));
        doThrow(new IllegalStateException("boom")).when(reminders).remindDue("acme");

        // when
        sut.tick();

        // then
        verify(reminders).remindDue("default");
        assertThat(runsOf("success")).isEqualTo(1.0);
        assertThat(runsOf("failure")).isZero();
    }

    @Test
    void eachOrganizationIsRemindedInsideItsOwnScope() {

        // given
        when(organizationIds.all()).thenReturn(List.of("acme"));
        var captured = new AtomicReference<Authentication>();
        doAnswer(invocation -> {
            captured.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(reminders).remindDue("acme");

        // when
        sut.tick();

        // then
        assertThat(captured.get()).isInstanceOfSatisfying(OrganizationBoundAuthentication.class,
                auth -> assertThat(auth.organizationId()).isEqualTo("acme"));
    }

    private double runsOf(String outcome) {

        return meters.get("axonia.sweep.runs").tag("sweep", "mentoring-meeting-reminder").tag("outcome", outcome)
                .counter().count();
    }
}
