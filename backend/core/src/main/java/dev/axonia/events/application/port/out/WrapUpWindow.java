package dev.axonia.events.application.port.out;

import java.time.Duration;

/**
 * How long after a session its wrap-up is still news. The same bound the wrap-up producer already stops looking back
 * at, so a session closed later than this cannot queue a wrap-up the producer would itself have treated as stale.
 */
@FunctionalInterface
public interface WrapUpWindow {

    Duration maxAge();
}
