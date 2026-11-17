package dev.axonia.mentoring.application.port.out;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;

/** Each member's own zone, in which their calendar days are read; UTC for a member with none or an unknown one. */
public interface MemberClocks {

    ZoneId zoneOf(String org, String userId);

    /** Total: every id given is answered. */
    Map<String, ZoneId> zonesOf(String org, Collection<String> userIds);
}
