package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingPlanTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-18");

    private static PlannedMeeting meeting(long id, LocalDate on, boolean held) {

        return PlannedMeeting.builder().id(id).pairingId(90L).on(on).topicIds(List.of()).plannedBy("noah")
                .plannedAt(Instant.parse("2026-11-10T10:00:00Z")).held(held).build();
    }

    @Test
    void aPastDayIsRefused() {

        // when / then
        assertThatThrownBy(() -> MeetingPlan.plannable(TODAY.minusDays(1), TODAY))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PAST")
                .hasMessage("Pick today or a later day.");
        assertThatThrownBy(() -> MeetingPlan.plannable(null, TODAY)).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "field_invalid");
    }

    @Test
    void todayIsPlannable() {

        // when / then
        assertThat(MeetingPlan.plannable(TODAY, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void dayThreeHundredSixtySixIsPlannable() {

        // when / then
        assertThat(MeetingPlan.plannable(TODAY.plusDays(366), TODAY)).isEqualTo(TODAY.plusDays(366));
    }

    @Test
    void dayThreeHundredSixtySevenIsRefused() {

        // when / then
        assertThatThrownBy(() -> MeetingPlan.plannable(TODAY.plusDays(367), TODAY))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOO_FAR")
                .hasMessage("Pick a day within a year.");
    }

    @Test
    void aHeldMeetingIsNeitherUpcomingNorOwed() {

        // given
        var heldToday = meeting(1L, TODAY, true);
        var heldBefore = meeting(2L, TODAY.minusDays(3), true);

        // when / then
        assertThat(MeetingPlan.isUpcoming(heldToday, TODAY)).isFalse();
        assertThat(MeetingPlan.isOwed(heldToday, TODAY)).isFalse();
        assertThat(MeetingPlan.isUpcoming(heldBefore, TODAY)).isFalse();
        assertThat(MeetingPlan.isOwed(heldBefore, TODAY)).isFalse();
    }

    @Test
    void aPassedMeetingWithoutNotesIsOwed() {

        // given
        var passed = meeting(1L, TODAY.minusDays(1), false);
        var today = meeting(2L, TODAY, false);

        // when / then
        assertThat(MeetingPlan.isOwed(passed, TODAY)).isTrue();
        assertThat(MeetingPlan.isUpcoming(passed, TODAY)).isFalse();
        assertThat(MeetingPlan.isOwed(today, TODAY)).isFalse();
        assertThat(MeetingPlan.isUpcoming(today, TODAY)).isTrue();
    }

    @Test
    void aDayAlreadyPlannedIsTaken() {

        // given
        var planned = List.of(meeting(1L, TODAY, false), meeting(2L, TODAY.plusDays(7), false));

        // when / then
        assertThatThrownBy(() -> MeetingPlan.requireFree(planned, TODAY.plusDays(7)))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TAKEN")
                .hasMessage("A meeting is already planned for that day.");
        MeetingPlan.requireFree(planned, TODAY.plusDays(8));
    }

    @Test
    void twelveUpcomingLeaveNoRoomWhilePassedAndHeldOnesDoNotCount() {

        // given
        var twelve = new ArrayList<PlannedMeeting>();
        for (var i = 0; i < 12; i++) {
            twelve.add(meeting(i, TODAY.plusDays(i), false));
        }
        var eleven = new ArrayList<>(twelve.subList(0, 11));
        eleven.add(meeting(20L, TODAY.minusDays(1), false));
        eleven.add(meeting(21L, TODAY.minusDays(2), true));

        // when / then
        assertThatThrownBy(() -> MeetingPlan.requireRoom(twelve, TODAY)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_LIMIT")
                .hasMessage("Twelve meetings are planned already. Remove one first.");
        MeetingPlan.requireRoom(eleven, TODAY);
    }

    @Test
    void topicsAreTheBoardsOwnAtMostFive() {

        // given
        var board = Set.of(1L, 2L, 3L, 4L, 5L, 6L);

        // when / then
        assertThat(MeetingPlan.topics(null, board)).isEmpty();
        assertThat(MeetingPlan.topics(List.of(3L, 1L, 3L), board)).containsExactly(3L, 1L);
        assertThat(MeetingPlan.topics(List.of(1L, 2L, 3L, 4L, 5L), board)).hasSize(5);
        assertThatThrownBy(() -> MeetingPlan.topics(List.of(1L, 2L, 3L, 4L, 5L, 6L), board))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOPICS")
                .hasMessage("A meeting names at most five topics.");
        assertThatThrownBy(() -> MeetingPlan.topics(List.of(1L, 99L), board)).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOPICS");
    }

    @Test
    void theEarliestOwedMeetingLeadsThenTheSoonestUpcoming() {

        // given
        var heldEarlier = meeting(1L, TODAY.minusDays(9), true);
        var owed = meeting(2L, TODAY.minusDays(2), false);
        var earlierOwed = meeting(3L, TODAY.minusDays(4), false);
        var later = meeting(4L, TODAY.plusDays(7), false);
        var soonest = meeting(5L, TODAY, false);

        // when / then
        assertThat(MeetingPlan.leading(List.of(heldEarlier, owed, earlierOwed, later, soonest), TODAY))
                .contains(earlierOwed);
        assertThat(MeetingPlan.leading(List.of(heldEarlier, later, soonest), TODAY)).contains(soonest);
        assertThat(MeetingPlan.leading(List.of(heldEarlier), TODAY)).isEmpty();
        assertThat(MeetingPlan.upcoming(List.of(heldEarlier, owed, later, soonest), TODAY)).isEqualTo(2);
    }
}
