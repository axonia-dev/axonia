package dev.axonia.health.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.health.api.SeriesPulse;

import static org.assertj.core.api.Assertions.assertThat;

class CadenceClassifierTest {

    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");

    private static Instant daysAgo(long days) {

        return NOW.minusSeconds(days * 86_400);
    }

    private static Instant daysAhead(long days) {

        return NOW.plusSeconds(days * 86_400);
    }

    @Test
    void classify_oneOffMonthlyIsSteady() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(50), daysAgo(20)), List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.medianGapDays()).isEqualTo(30);
        assertThat(actual.sessionsHeld()).isEqualTo(2);
    }

    @Test
    void classify_oneOffSilentPastOwnRhythmIsSlowing() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(100), daysAgo(85), daysAgo(70)), List.of(), null, NOW);

        // then 70 days of silence > 1.5 x the 15-day median
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
        assertThat(actual.medianGapDays()).isEqualTo(15);
    }

    @Test
    void classify_singleOneOffRecentIsSteady() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(30)), List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_singleOneOffOldButNextSoonIsSteady() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(80)), List.of(), daysAhead(10), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
    }

    @Test
    void classify_singleOneOffOldAndNextFarIsSlowing() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(80)), List.of(), daysAhead(60), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_singleOneOffOldNothingAheadIsSlowing() {

        // when
        var actual = CadenceClassifier.classify(List.of(daysAgo(80)), List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_sameDaySessionsAreOneOccasionNotAZeroDayRhythm() {

        // given a morning and an afternoon one-off session 3 days ago
        var held = List.of(daysAgo(3).minusSeconds(6 * 3600), daysAgo(3));

        // when
        var actual = CadenceClassifier.classify(held, List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.sessionsHeld()).isEqualTo(2);
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_monthlyDoubleHeadersReadByTheirOccasions() {

        // given a pair of sessions every 30 days, the last pair 10 days ago
        var held = List.of(daysAgo(70), daysAgo(70).plusSeconds(7200), daysAgo(40), daysAgo(40).plusSeconds(7200),
                daysAgo(10), daysAgo(10).plusSeconds(7200));

        // when
        var actual = CadenceClassifier.classify(held, List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.sessionsHeld()).isEqualTo(6);
        assertThat(actual.medianGapDays()).isEqualTo(30);
    }

    @Test
    void classify_adHocSeriesWithSeveralHeldReadsTheirOwnMedian() {

        // given an ad hoc series (no cadence) and sessions 30 and 25 days ago: the median of 5 days is the rhythm
        var actual = CadenceClassifier.classify(List.of(daysAgo(30), daysAgo(25)), List.of(new SeriesPulse(null)), null,
                NOW);

        // then 25 days of silence is past 1.5 x 5
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
        assertThat(actual.medianGapDays()).isEqualTo(5);
    }

    @Test
    void classify_gapJustUnderFifteenDaysRoundsToFifteen() {

        // given sessions 14 days 23 hours apart
        var held = List.of(daysAgo(20), daysAgo(20).plus(Duration.ofDays(14)).plus(Duration.ofHours(23)));

        // when
        var actual = CadenceClassifier.classify(held, List.of(), null, NOW);

        // then
        assertThat(actual.medianGapDays()).isEqualTo(15);
    }

    @Test
    void classify_gapJustOverFifteenDaysRoundsToFifteen() {

        // given sessions 15 days 1 hour apart
        var held = List.of(daysAgo(20), daysAgo(20).plus(Duration.ofDays(15)).plus(Duration.ofHours(1)));

        // when
        var actual = CadenceClassifier.classify(held, List.of(), null, NOW);

        // then
        assertThat(actual.medianGapDays()).isEqualTo(15);
    }

    @Test
    void classify_nothingHeldButSessionAheadIsStalled() {

        // when
        var actual = CadenceClassifier.classify(List.of(), List.of(), daysAhead(7), NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STALLED);
        assertThat(actual.sessionsHeld()).isZero();
    }

    @Test
    void classify_dormantOnlyWhenNothingHeldNothingAheadNoSeries() {

        // when
        var actual = CadenceClassifier.classify(List.of(), List.of(), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.DORMANT);
        assertThat(actual.sessionsHeld()).isZero();
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_stalledWhenActiveSeriesButNothingHeld() {

        // when
        var actual = CadenceClassifier.classify(List.of(), List.of(new SeriesPulse(7)), null, NOW);

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
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), null, NOW);

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
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), null, NOW);

        // then median gap 22 days > 1.5 * 7
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
        assertThat(actual.medianGapDays()).isEqualTo(22);
    }

    @Test
    void classify_slowingWhenLastHeldLongAgoDespiteGoodMedian() {

        // given a tight historical rhythm but nothing held for 30 days
        var held = List.of(daysAgo(44), daysAgo(37), daysAgo(30));

        // when
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7)), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_singleHeldSessionReadsAgainstRecency() {

        // given one session 5 days ago on a weekly cadence — no median yet
        var actual = CadenceClassifier.classify(List.of(daysAgo(5)), List.of(new SeriesPulse(7)), null, NOW);

        // then
        assertThat(actual.status()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.medianGapDays()).isNull();
    }

    @Test
    void classify_adHocOnlyUsesRecencyWindow() {

        // given only an ad-hoc series (no cadence hint)
        var adHoc = List.of(new SeriesPulse(null));

        // when / then recent activity is steady, stale activity is slowing
        assertThat(CadenceClassifier.classify(List.of(daysAgo(20)), adHoc, null, NOW).status())
                .isEqualTo(CadenceStatus.STEADY);
        assertThat(CadenceClassifier.classify(List.of(daysAgo(60)), adHoc, null, NOW).status())
                .isEqualTo(CadenceStatus.SLOWING);
    }

    @Test
    void classify_usesShortestCadenceAmongActiveSeries() {

        // given a weekly and a monthly series; rhythm is monthly-ish
        var held = List.of(daysAgo(35), daysAgo(5));

        // when the weekly hint (7d) is the expectation
        var actual = CadenceClassifier.classify(held, List.of(new SeriesPulse(7), new SeriesPulse(30)), null, NOW);

        // then a 30-day gap is slowing against a weekly expectation
        assertThat(actual.status()).isEqualTo(CadenceStatus.SLOWING);
    }
}
