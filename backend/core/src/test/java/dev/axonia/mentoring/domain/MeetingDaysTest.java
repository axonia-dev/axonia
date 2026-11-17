package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingDaysTest {

    private static final LocalDate STARTED = LocalDate.parse("2026-11-01");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");
    private static final LocalDate MEETING = LocalDate.parse("2026-11-19");
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Instant SET_EARLIER = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void dayOfReadsTheDayInTheZone() {

        // when
        var actual = MeetingDays.dayOf(Instant.parse("2026-11-15T12:30:00Z"), ZoneId.of("Pacific/Auckland"));

        // then
        assertThat(actual).isEqualTo(LocalDate.parse("2026-11-16"));
    }

    @Test
    void nullMeansToday() {

        // when
        var actual = MeetingDays.meetingDay(null, STARTED, TODAY);

        // then
        assertThat(actual).isEqualTo(TODAY);
    }

    @Test
    void theStartDayAndTodayAreAllowed() {

        // when / then
        assertThat(MeetingDays.meetingDay(STARTED, STARTED, TODAY)).isEqualTo(STARTED);
        assertThat(MeetingDays.meetingDay(TODAY, STARTED, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void aDayBeforeTheStartOrAfterTodayIsRefused() {

        // when / then
        assertThatThrownBy(() -> MeetingDays.meetingDay(STARTED.minusDays(1), STARTED, TODAY))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_DAY");
        assertThatThrownBy(() -> MeetingDays.meetingDay(TODAY.plusDays(1), STARTED, TODAY))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_DAY");
    }

    @Test
    void todayAndLaterAreUpcoming() {

        // when / then
        assertThat(MeetingDays.upcoming(TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(MeetingDays.upcoming(MEETING, TODAY)).isEqualTo(MEETING);
    }

    @Test
    void yesterdayIsNotUpcoming() {

        // when / then
        assertThatThrownBy(() -> MeetingDays.upcoming(TODAY.minusDays(1), TODAY))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PAST");
        assertThatThrownBy(() -> MeetingDays.upcoming(null, TODAY)).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "field_invalid");
    }

    @Test
    void reminderDueOnlyFromNineTheDayBefore() {

        // when / then
        assertThat(MeetingDays.reminderDue(MEETING, SET_EARLIER, Instant.parse("2026-11-18T07:59:00Z"), ZURICH))
                .isFalse();
        assertThat(MeetingDays.reminderDue(MEETING, SET_EARLIER, Instant.parse("2026-11-18T08:00:00Z"), ZURICH))
                .isTrue();
        assertThat(MeetingDays.reminderDue(MEETING, SET_EARLIER, Instant.parse("2026-11-18T22:59:00Z"), ZURICH))
                .isTrue();
        assertThat(MeetingDays.reminderDue(MEETING, SET_EARLIER, Instant.parse("2026-11-19T08:00:00Z"), ZURICH))
                .isFalse();
        assertThat(MeetingDays.reminderDue(MEETING, SET_EARLIER, Instant.parse("2026-11-17T08:00:00Z"), ZURICH))
                .isFalse();
    }

    @Test
    void aDateSetTheDayBeforeIsNotReminded() {

        // given
        var setTheDayBefore = Instant.parse("2026-11-18T06:00:00Z");

        // when
        var actual = MeetingDays.reminderDue(MEETING, setTheDayBefore, Instant.parse("2026-11-18T10:00:00Z"), ZURICH);

        // then
        assertThat(actual).isFalse();
    }
}
