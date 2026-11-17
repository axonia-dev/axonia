package dev.axonia.mentoring.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairHoldTest {

    private static final Instant SENT = Instant.parse("2026-10-01T09:00:00Z");
    private static final String MIA = "mia";
    private static final String NOAH = "noah";

    /** {@code sender} proposes to mentor the other person. */
    private static Proposal.ProposalBuilder from(String sender, long id, Instant at) {

        var other = MIA.equals(sender) ? NOAH : MIA;
        return Proposal.builder().id(id).communityId(7L).mentorUserId(sender).menteeUserId(other)
                .proposedBy(Role.MENTOR).topics(List.of("Kafka")).message("Hi").status(ProposalStatus.OPEN)
                .createdAt(at);
    }

    private static Proposal declined(Proposal.ProposalBuilder proposal, Instant at) {

        return proposal.status(ProposalStatus.DECLINED).answeredAt(at).build();
    }

    @Test
    void onlyTheSenderWaitsAfterADecline() {

        // given
        var between = List.of(declined(from(MIA, 1L, SENT), SENT.plus(Duration.ofDays(1))));
        var now = SENT.plus(Duration.ofDays(30));

        // when / then
        assertThat(PairHold.of(between, MIA, now)).isEqualTo(PairHold.DECLINED_RECENTLY);
        assertThat(PairHold.of(between, NOAH, now)).isEqualTo(PairHold.NONE);
        assertThat(PairHold.of(between, MIA, SENT.plus(Duration.ofDays(31)))).isEqualTo(PairHold.NONE);
        assertThat(PairHold.declinedRecently(between, MIA, now)).contains(between.getFirst());
    }

    @Test
    void theDeclinersOwnProposalLiftsTheWait() {

        // given
        var first = declined(from(MIA, 1L, SENT), SENT.plus(Duration.ofDays(1)));
        var back = from(NOAH, 2L, SENT.plus(Duration.ofDays(2))).status(ProposalStatus.WITHDRAWN)
                .answeredAt(SENT.plus(Duration.ofDays(3))).build();
        var now = SENT.plus(Duration.ofDays(4));

        // when / then
        assertThat(PairHold.of(List.of(first, back), MIA, now)).isEqualTo(PairHold.NONE);
        assertThat(PairHold.of(List.of(back, first), NOAH, now)).isEqualTo(PairHold.NONE);
    }

    @Test
    void aLapsedReopenedProposalCountsFromItsReopening() {

        // given
        var reopenedAt = SENT.plus(Duration.ofDays(20));
        var reopened = from(MIA, 1L, SENT).reopenedAt(reopenedAt).build();

        // when / then
        assertThat(MentoringWindows.openSince(reopened)).isEqualTo(reopenedAt);
        assertThat(PairHold.of(List.of(reopened), MIA, reopenedAt.plus(Duration.ofDays(30)).minusMillis(1)))
                .isEqualTo(PairHold.OPEN);
        assertThat(MentoringWindows.isLapsed(reopened, reopenedAt.plus(Duration.ofDays(30)))).isTrue();
        assertThat(PairHold.of(List.of(reopened), MIA, reopenedAt.plus(Duration.ofDays(30)))).isEqualTo(PairHold.NONE);
    }

    @Test
    void anOpenProposalHoldsBothAndOnlyItRefusesAReopening() {

        // given
        var open = List.of(from(MIA, 1L, SENT).build());

        // when / then
        assertThatThrownBy(() -> PairHold.of(open, NOAH, SENT).requireNoneOpen()).isInstanceOfSatisfying(
                ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("MENTORING_PROPOSAL_OPEN"));
        assertThatCode(PairHold.DECLINED_RECENTLY::requireNoneOpen).doesNotThrowAnyException();
        assertThatCode(PairHold.NONE::requireNoneOpen).doesNotThrowAnyException();
        assertThat(PairHold.of(List.of(), MIA, SENT)).isEqualTo(PairHold.NONE);
    }

    @Test
    void twoProposalsSentAtOnceAreOrderedById() {

        // given
        var older = declined(from(NOAH, 1L, SENT), SENT.plus(Duration.ofDays(1)));
        var newer = declined(from(MIA, 2L, SENT), SENT.plus(Duration.ofDays(1)));
        var unsaved = from(NOAH, 0L, SENT).id(null).status(ProposalStatus.WITHDRAWN).build();

        // when / then
        assertThat(PairHold.of(List.of(newer, older, unsaved), MIA, SENT.plus(Duration.ofDays(2))))
                .isEqualTo(PairHold.DECLINED_RECENTLY);
    }
}
