package dev.axonia.health.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.health.CadenceStatus;
import dev.axonia.health.SeriesPulse;

import static org.assertj.core.api.Assertions.assertThat;

class CadenceClassifierTest {

    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");

    private static Instant daysAgo(long days) {

        return NOW.minusSeconds(days * 86_400);
    }

    @Test
    void classify_dormantWhenNoActiveSeries() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(10)), List.of(), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.DORMANT);
        assertThat(actual.sessionsHeld()).isEqualTo(1);
    }

    @Test
    void classify_stalledWhenActiveSeriesButNothingHeld() {

        // when
        var actual = CadenceClassifier.classify(List.of(), List.of(new SeriesPulse(7)), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STALLED);
        assertThat(actual.sessionsHeld()).isZero();
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_steadyOnWeeklyCadence() {

        // given weekly sessions, last one 4 days ago
        var held = List.of(daysAgo(25), daysAgo(18), daysAgo(11), daysAgo(4));

        // when
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.sessionsHeld()).isEqualTo(4);
        assertThat(actual.medianGapDays()).isEqualTo(7);
    }

    @Test
    void classify_slowingWhenGapWidens() {

        // given a weekly series whose sessions drifted to ~3-week gaps
        var held = List.of(daysAgo(50), daysAgo(28), daysAgo(6));

        // when
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), NOW);

        // then median gap 22 days > 1.5 * 7
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
        assertThat(actual.medianGapDays()).isEqualTo(22);
    }

    @Test
    void classify_slowingWhenLastHeldLongAgoDespiteGoodMedian() {

        // given a tight historical rhythm but nothing held for 30 days
        var held = List.of(daysAgo(44), daysAgo(37), daysAgo(30));

        // when
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_singleHeldSessionReadsAgainstRecency() {

        // given one session 5 days ago on a weekly cadence — no median yet
        var actual = CadenceClassifier.classify(List.of(daysAgo(5)), List.of(new SeriesPulse(7)), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_adHocOnlyUsesRecencyWindow() {

        // given only an ad-hoc series (no cadence hint)
        var adHoc = List.of(new SeriesPulse(null));

        // when / then recent activity is steady, stale activity is slowing
        assertThat(CadenceClassifier.classify(List.of(daysAgo(20)), adHoc, NOW).status())
                .isEqualTo(CadenceStatus.STEADY);
        assertThat(CadenceClassifier.classify(List.of(daysAgo(60)), adHoc, NOW).status())
                .isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_usesShortestCadenceAmongActiveSeries() {

        // given a weekly and a monthly series; rhythm is monthly-ish
        var held = List.of(daysAgo(35), daysAgo(5));

        // when the weekly hint (7d) is the expectation
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7), new SeriesPulse(30)), NOW);

        // then a 30-day gap is slowing against a weekly expectation
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }
}
