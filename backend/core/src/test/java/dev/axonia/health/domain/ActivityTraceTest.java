package dev.axonia.health.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTraceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");
    private static final Instant FROM = NOW.minus(Duration.ofDays(90));
    private static final List<Long> ZEROES = Collections.nCopies(90, 0L);

    @Test
    void daysOf_bucketsHeldDatesPerDayOldestFirst() {

        // given one session on the first day, two on the last, one on day 45
        var dates = List.of(FROM.plusSeconds(60), FROM.plus(Duration.ofDays(45)).plusSeconds(1), NOW.minusSeconds(60),
                NOW.minusSeconds(3600));

        // when
        var actual = ActivityTrace.daysOf(dates, FROM, 90);

        // then
        assertThat(actual).hasSize(90);
        assertThat(actual.get(0)).isEqualTo(1L);
        assertThat(actual.get(45)).isEqualTo(1L);
        assertThat(actual.get(89)).isEqualTo(2L);
        assertThat(actual.stream().mapToLong(Long::longValue).sum()).isEqualTo(4L);
    }

    @Test
    void daysOf_ignoresDatesOutsideTheWindow() {

        var dates = List.of(FROM.minusSeconds(1), NOW, NOW.plusSeconds(1));

        assertThat(ActivityTrace.daysOf(dates, FROM, 90)).isEqualTo(ZEROES);
    }

    @Test
    void daysOf_ofNothingIsAllZeroes() {

        assertThat(ActivityTrace.daysOf(List.of(), FROM, 90)).isEqualTo(ZEROES);
    }
}
