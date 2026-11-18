package dev.axonia.mentoring.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.mentoring.api.NextMeetingDay;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringViewsTest {

    private static final Instant SENT = Instant.parse("2026-10-01T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-17");
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private static final MentoringCommunity GUILD = new MentoringCommunity(7L, "platform", "Platform", false, true);

    private static Proposal proposal(ProposalStatus status) {

        return Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka")).message("Hi").status(status).createdAt(SENT)
                .answeredAt(status == ProposalStatus.OPEN ? null : SENT).build();
    }

    private static String statusAt(ProposalStatus status, Instant now) {

        return view(proposal(status), new CallerTime(now, ZoneOffset.UTC)).status();
    }

    private static ProposalView view(Proposal proposal, CallerTime at) {

        return MentoringViews.proposal(proposal, GUILD, new MentoringPerson("noah", "Noah", null, null), at);
    }

    private static Pairing.PairingBuilder pairing() {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah").startedAt(SENT);
    }

    @Test
    void aPartnersGivenNameTravelsWithThePairing() {

        var partner = new MentoringPerson("noah", "Aurélia Rochat", "Aurélia", null);

        assertThat(MentoringViews.pairing(pairing().build(), GUILD, "mia", partner).build().partnerGivenName())
                .isEqualTo("Aurélia");
    }

    @Test
    void aPartnerWithNoGivenNameAndADeletedPartnerCarryNone() {

        var unread = new MentoringPerson("noah", "Rochat, Aurélia-NORTHWIND", null, null);

        assertThat(MentoringViews.pairing(pairing().build(), GUILD, "mia", unread).build().partnerGivenName()).isNull();
        assertThat(MentoringViews.pairing(pairing().build(), GUILD, "mia", MentoringPerson.deleted("noah")).build()
                .partnerGivenName()).isNull();
    }

    private static Topic topic(String label, TopicState state) {

        return Topic.builder().pairingId(90L).label(label).state(state).build();
    }

    private static PairingSummary glance(Pairing pairing, List<Topic> trail, NoteTally tally) {

        return glance(pairing, trail, tally, List.of());
    }

    private static PairingSummary glance(Pairing pairing, List<Topic> trail, NoteTally tally,
            List<PlannedMeeting> meetings) {

        return MentoringViews
                .glance(PairingSummary.builder(), pairing, new PairingProgress(trail, tally, meetings), TODAY).build();
    }

    private static PairingSummary planned(PlannedMeeting... meetings) {

        return glance(pairing().build(), List.of(), NoteTally.NONE, List.of(meetings));
    }

    private static PlannedMeeting meeting(long id, LocalDate on) {

        return PlannedMeeting.builder().id(id).pairingId(90L).on(on).topicIds(List.of()).plannedBy("mia")
                .plannedAt(SENT).build();
    }

    @Test
    void anUnansweredProposalReadsAsLapsedAfterThirtyDays() {

        assertThat(statusAt(ProposalStatus.OPEN, SENT.plus(Duration.ofDays(1)))).isEqualTo("OPEN");
        assertThat(statusAt(ProposalStatus.OPEN, SENT.plus(Duration.ofDays(30)))).isEqualTo("LAPSED");
        assertThat(statusAt(ProposalStatus.DECLINED, SENT.plus(Duration.ofDays(30)))).isEqualTo("DECLINED");
    }

    @Test
    void lapsesOnCountsFromTheReopening() {

        // given
        var reopened = proposal(ProposalStatus.OPEN).toBuilder().reopenedAt(Instant.parse("2026-11-10T09:00:00Z"))
                .build();

        // when
        var actual = view(reopened, new CallerTime(Instant.parse("2026-11-17T09:00:00Z"), ZURICH));

        // then
        assertThat(actual.status()).isEqualTo("OPEN");
        assertThat(actual.lapsesOn()).isEqualTo(LocalDate.parse("2026-12-10"));
    }

    @Test
    void lapsesOnIsNullOnceClosed() {

        var at = new CallerTime(SENT.plus(Duration.ofDays(1)), ZURICH);

        assertThat(view(proposal(ProposalStatus.DECLINED), at).lapsesOn()).isNull();
        assertThat(
                view(proposal(ProposalStatus.OPEN), new CallerTime(SENT.plus(Duration.ofDays(30)), ZURICH)).lapsesOn())
                .isNull();
        assertThat(view(proposal(ProposalStatus.OPEN), at).lapsesOn()).isEqualTo(LocalDate.parse("2026-10-31"));
    }

    @Test
    void glanceNamesTheFirstTopicInProgress() {

        // when
        var actual = glance(pairing().build(),
                List.of(topic("Docker", TopicState.DONE), topic("Kafka", TopicState.IN_PROGRESS),
                        topic("Helm", TopicState.IN_PROGRESS), topic("Feedback", TopicState.TODO)),
                new NoteTally(3, 2, LocalDate.parse("2026-11-09")));

        // then
        assertThat(actual.stops()).containsExactly("DONE", "IN_PROGRESS", "IN_PROGRESS", "TODO");
        assertThat(actual.nowTopic()).isEqualTo("Kafka");
        assertThat(actual.notes()).isEqualTo(3);
        assertThat(actual.meetings()).isEqualTo(2);
        assertThat(actual.lastMetOn()).isEqualTo(LocalDate.parse("2026-11-09"));
    }

    @Test
    void glanceDropsThePlanOfAnEndedPairing() {

        // given
        var ended = pairing().endedAt(SENT.plus(Duration.ofDays(40))).build();

        // when
        var actual = glance(ended, List.of(), NoteTally.NONE,
                List.of(meeting(1L, TODAY.minusDays(1)), meeting(2L, TODAY.plusDays(2))));

        // then
        assertThat(actual.nextMeeting()).isNull();
        assertThat(actual.laterPlanned()).isZero();
    }

    @Test
    void theSummaryShowsTheOwedMeetingFirstThenTheSoonest() {

        // given
        var owed = meeting(1L, TODAY.minusDays(3));
        var laterOwed = meeting(2L, TODAY.minusDays(1));
        var held = meeting(3L, TODAY.minusDays(5)).toBuilder().held(true).build();
        var today = meeting(4L, TODAY);
        var later = meeting(5L, TODAY.plusDays(7));

        // when / then
        assertThat(planned(held, laterOwed, owed, today, later).nextMeeting())
                .isEqualTo(new NextMeetingDay(TODAY.minusDays(3), true));
        assertThat(planned(held, later, today).nextMeeting()).isEqualTo(new NextMeetingDay(TODAY, false));
        assertThat(planned(held).nextMeeting()).isNull();
    }

    @Test
    void laterPlannedCountsTheOtherUpcomingMeetings() {

        // given
        var owed = meeting(1L, TODAY.minusDays(1));
        var held = meeting(2L, TODAY).toBuilder().held(true).build();
        var soon = meeting(3L, TODAY.plusDays(1));
        var later = meeting(4L, TODAY.plusDays(8));
        var last = meeting(5L, TODAY.plusDays(15));

        // when / then
        assertThat(planned(owed, held, soon, later, last).laterPlanned()).isEqualTo(3);
        assertThat(planned(held, soon, later, last).laterPlanned()).isEqualTo(2);
        assertThat(planned(soon).laterPlanned()).isZero();
        assertThat(planned(owed).laterPlanned()).isZero();
    }

    @Test
    void glanceOfAnEmptyJournalIsZeros() {

        // when
        var actual = glance(pairing().build(), List.of(), NoteTally.NONE);

        // then
        assertThat(actual.stops()).isEmpty();
        assertThat(actual.nowTopic()).isNull();
        assertThat(actual.nextMeeting()).isNull();
        assertThat(actual.laterPlanned()).isZero();
        assertThat(actual.notes()).isZero();
        assertThat(actual.meetings()).isZero();
        assertThat(actual.lastMetOn()).isNull();
    }
}
