package dev.axonia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock a test steps by hand, shared rather than copied into each test class. Every Caffeine cache reads the
 * application's clock through {@link ClockTicker}, so stepping this one expires cache entries exactly as it moves every
 * other deadline.
 */
public final class SteppableClock extends Clock {

    private volatile Instant now;

    public SteppableClock(Instant now) {

        this.now = now;
    }

    public void advance(Duration by) {

        now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {

        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {

        return this;
    }

    @Override
    public Instant instant() {

        return now;
    }
}
