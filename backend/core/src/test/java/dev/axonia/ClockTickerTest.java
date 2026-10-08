package dev.axonia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClockTickerTest {

    @Test
    void readsTheClocksInstantAsNanosecondsSinceTheEpoch() {

        // given
        var sut = new ClockTicker(Clock.fixed(Instant.parse("2026-10-01T08:00:00.123456789Z"), ZoneOffset.UTC));

        // when
        var actual = sut.read();

        // then
        assertThat(actual).isEqualTo(1_790_841_600_123_456_789L);
    }

    @Test
    void movesExactlyAsFarAsTheClockIsStepped() {

        // given
        var clock = new SteppableClock(Instant.parse("2026-10-01T08:00:00Z"));
        var sut = new ClockTicker(clock);
        var before = sut.read();

        // when
        clock.advance(Duration.ofMinutes(10).plusMillis(1));

        // then
        assertThat(sut.read() - before).isEqualTo(Duration.ofMinutes(10).plusMillis(1).toNanos());
    }

    /** Past 2262-04-11 the nanoseconds since the epoch do not fit a {@code long}; wrapping would read as the past. */
    @Test
    void refusesAnInstantWhoseNanosecondsDoNotFitALong() {

        // given
        var sut = new ClockTicker(Clock.fixed(Instant.parse("2263-01-01T00:00:00Z"), ZoneOffset.UTC));

        // when / then
        assertThatThrownBy(sut::read).isInstanceOf(ArithmeticException.class);
    }
}
