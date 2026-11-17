package dev.axonia.mentoring.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringWindowsTest {

    private static final Instant SENT = Instant.parse("2026-10-01T09:00:00Z");

    private static Proposal proposal(ProposalStatus status) {

        return Proposal.builder().status(status).createdAt(SENT).answeredAt(SENT).build();
    }

    @Test
    void aProposalLapsesThirtyDaysAfterItWasSent() {

        // given
        var open = proposal(ProposalStatus.OPEN);
        var lastMoment = SENT.plus(Duration.ofDays(30)).minusMillis(1);
        var lapsedAt = SENT.plus(Duration.ofDays(30));

        // when / then
        assertThat(MentoringWindows.isOpen(open, lastMoment)).isTrue();
        assertThat(MentoringWindows.isLapsed(open, lastMoment)).isFalse();
        assertThat(MentoringWindows.isOpen(open, lapsedAt)).isFalse();
        assertThat(MentoringWindows.isLapsed(open, lapsedAt)).isTrue();
    }

    @Test
    void anAnsweredProposalNeverLapses() {

        // when
        var actual = MentoringWindows.isLapsed(proposal(ProposalStatus.ACCEPTED), SENT.plus(Duration.ofDays(90)));

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void aDeclineHoldsForThirtyDays() {

        // given
        var declined = proposal(ProposalStatus.DECLINED);

        // when / then
        assertThat(MentoringWindows.isDeclinedRecently(declined, SENT.plus(Duration.ofDays(29)))).isTrue();
        assertThat(MentoringWindows.isDeclinedRecently(declined, SENT.plus(Duration.ofDays(30)))).isFalse();
        assertThat(MentoringWindows.isDeclinedRecently(proposal(ProposalStatus.WITHDRAWN), SENT)).isFalse();
    }

    @Test
    void newWordsWaitForTheEndAndThenStayWritableForAYear() {

        // given
        var ongoing = Pairing.builder().mentorUserId("mia").menteeUserId("noah").startedAt(SENT).build();
        var ended = ongoing.toBuilder().endedAt(SENT).build();

        // when / then
        assertThat(MentoringWindows.isWordsWritable(ongoing, false, SENT)).isFalse();
        assertThat(MentoringWindows.isWordsNotYet(ongoing, false)).isTrue();
        assertThat(MentoringWindows.isWordsWritable(ended, false, SENT.plus(Duration.ofDays(365)))).isTrue();
        assertThat(MentoringWindows.isWordsNotYet(ended, false)).isFalse();
        assertThat(MentoringWindows.isWordsWritable(ended, false, SENT.plus(Duration.ofDays(365)).plusMillis(1)))
                .isFalse();
    }

    @Test
    void wordsWrittenDuringThePairingStayChangeableWhileItIsOngoingAndForAYearAfter() {

        // given
        var ongoing = Pairing.builder().mentorUserId("mia").menteeUserId("noah").startedAt(SENT).build();
        var ended = ongoing.toBuilder().endedAt(SENT).build();

        // when / then
        assertThat(MentoringWindows.isWordsWritable(ongoing, true, SENT.plus(Duration.ofDays(900)))).isTrue();
        assertThat(MentoringWindows.isWordsNotYet(ongoing, true)).isFalse();
        assertThat(MentoringWindows.isWordsWritable(ended, true, SENT.plus(Duration.ofDays(365)))).isTrue();
        assertThat(MentoringWindows.isWordsWritable(ended, true, SENT.plus(Duration.ofDays(366)))).isFalse();
    }

    @Test
    void wordsNeedBothPeople() {

        // given
        var noMentor = Pairing.builder().menteeUserId("noah").startedAt(SENT).build();
        var noMentee = Pairing.builder().mentorUserId("mia").startedAt(SENT).endedAt(SENT).build();

        // when / then
        assertThat(MentoringWindows.isWordsWritable(noMentor, true, SENT)).isFalse();
        assertThat(MentoringWindows.isWordsWritable(noMentee, true, SENT)).isFalse();
        assertThat(MentoringWindows.isWordsNotYet(noMentor, false)).isFalse();
        assertThat(MentoringWindows.isWordsNotYet(noMentee, false)).isFalse();
    }
}
