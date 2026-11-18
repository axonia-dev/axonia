package dev.axonia.community.api.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import dev.axonia.community.api.LobbySession;

/**
 * What the events context tells the communities lobby, for a batch of communities of the implementer's own current
 * organization. A community absent from an answer has nothing to tell.
 */
public interface SessionShowcase {

    /**
     * The start of the earliest session, neither cancelled nor skipped, that is not over at {@code now}. A timed
     * session is over once its duration has run (at its start when it has none); an untimed one at the end of its date
     * in its own zone. So a session under way, or an untimed one later today, is still next.
     */
    Map<Long, Instant> nextSessionsAt(Set<Long> communityIds, Instant now);

    Map<Long, SessionSignals> signals(Set<Long> communityIds, Instant now);

    /**
     * {@code next} as {@link #nextSessionsAt}, {@code latest} the latest held session already over, {@code held} the
     * count of held sessions, {@code latestTopicTitle} the newest proposed topic; each nullable but {@code held}.
     */
    record SessionSignals(LobbySession next, LobbySession latest, long held, String latestTopicTitle) {
    }
}
