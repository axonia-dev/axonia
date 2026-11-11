package dev.axonia.events.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.events.domain.FeedbackRules.WindowState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FeedbackRulesTest {

    private static final Duration MAX_AGE = Duration.ofDays(30);

    private static final Instant SCHEDULED_AT = Instant.parse("2026-11-06T12:00:00Z");

    private static final Instant CLOSED_AT = Instant.parse("2026-11-06T13:00:00Z");

    private static final Instant CLOSES_AT = Instant.parse("2026-11-13T13:00:00Z");

    @Test
    void closesAt_isSevenDaysAfterClose() {

        // given
        var session = session("HELD", CLOSED_AT);

        // when
        var closesAt = FeedbackRules.closesAt(session, MAX_AGE);

        // then
        assertThat(closesAt).contains(CLOSES_AT);
    }

    @Test
    void closesAt_isEmptyWhenClosedBeyondTheWrapUpWindow() {

        // given
        var session = session("HELD", SCHEDULED_AT.plus(Duration.ofDays(31)));

        // when
        var closesAt = FeedbackRules.closesAt(session, MAX_AGE);

        // then
        assertThat(closesAt).isEmpty();
    }

    @Test
    void closesAt_isOpenWhenClosedExactlyAtTheWrapUpBound() {

        // given
        var session = session("HELD", SCHEDULED_AT.plus(MAX_AGE));

        // when
        var closesAt = FeedbackRules.closesAt(session, MAX_AGE);

        // then
        assertThat(closesAt).contains(SCHEDULED_AT.plus(MAX_AGE).plus(FeedbackRules.WINDOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCHEDULED", "CANCELLED"})
    void closesAt_isEmptyWhenNotHeld(String status) {

        // given
        var session = session(status, CLOSED_AT);

        // when
        var closesAt = FeedbackRules.closesAt(session, MAX_AGE);

        // then
        assertThat(closesAt).isEmpty();
    }

    @Test
    void closesAt_isEmptyWhenHeldWithoutAClose() {

        // given
        var session = session("HELD", null);

        // when
        var closesAt = FeedbackRules.closesAt(session, MAX_AGE);

        // then
        assertThat(closesAt).isEmpty();
    }

    @Test
    void windowState_openUntilTheInstantItCloses() {

        // given
        var session = session("HELD", CLOSED_AT);

        // when
        var justBefore = FeedbackRules.windowState(session, MAX_AGE, CLOSES_AT.minusMillis(1));
        var atClose = FeedbackRules.windowState(session, MAX_AGE, CLOSES_AT);

        // then
        assertThat(justBefore).isEqualTo(WindowState.OPEN);
        assertThat(atClose).isEqualTo(WindowState.CLOSED);
    }

    @Test
    void windowState_noneWithoutAWindow() {

        // given
        var session = session("SCHEDULED", null);

        // when
        var state = FeedbackRules.windowState(session, MAX_AGE, CLOSED_AT);

        // then
        assertThat(state).isEqualTo(WindowState.NONE);
    }

    @Test
    void toTen_mapsTheFivePointScale() {

        // then
        assertThat(FeedbackRules.toTen(1, 5)).isEqualTo(1.0);
        assertThat(FeedbackRules.toTen(3, 5)).isEqualTo(5.5);
        assertThat(FeedbackRules.toTen(5, 5)).isEqualTo(10.0);
        assertThat(FeedbackRules.toTen(7, 10)).isEqualTo(7.0);
    }

    @Test
    void isScore_refusesOutsideTheScale() {

        // then
        assertThat(FeedbackRules.isScore(0, 10)).isFalse();
        assertThat(FeedbackRules.isScore(6, 5)).isFalse();
        assertThat(FeedbackRules.isScore(10, 10)).isTrue();
        assertThat(FeedbackRules.isScore(1, 5)).isTrue();
    }

    @Test
    void importedAnswer_refusesBothMissing() {

        // then
        assertThatIllegalArgumentException().isThrownBy(() -> new ImportedAnswer(null, null));
        assertThat(new ImportedAnswer(4, null).enjoyed()).isEqualTo(4);
        assertThat(new ImportedAnswer(null, 3).learned()).isEqualTo(3);
    }

    @Test
    void tally_averagesRoundToOneDecimal() {

        // given
        var tally = new FeedbackTally(3, 3, 25, 2, 15);

        // then
        assertThat(tally.enjoyedAverage()).isEqualTo(8.3);
        assertThat(tally.learnedAverage()).isEqualTo(7.5);
    }

    @Test
    void tally_roundsHalfUp() {

        // given
        var tally = new FeedbackTally(4, 4, 33, 4, 33);

        // then
        assertThat(tally.enjoyedAverage()).isEqualTo(8.3);
    }

    @Test
    void tally_nullAverageWithoutAnswers() {

        // given
        var tally = new FeedbackTally(0, 0, 0, 0, 0);

        // then
        assertThat(tally.enjoyedAverage()).isNull();
        assertThat(tally.learnedAverage()).isNull();
    }

    private static SessionRecord session(String status, Instant closedAt) {

        return SessionRecord.builder().status(status).scheduledAt(SCHEDULED_AT).closedAt(closedAt).build();
    }
}
