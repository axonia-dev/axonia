package dev.axonia.events.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.events.api.Session;
import dev.axonia.events.application.SessionPageQuery;

/** A page of sessions read one query per level of the aggregate, rather than one query per parent row. */
public interface SessionListing {

    List<Session> page(SessionPageQuery query);

    /**
     * When each kept session of a community falls in {@code [from, to)}, earliest first; cancelled and skipped drop.
     */
    List<Instant> instants(String organizationId, long communityId, Instant from, Instant to);

    /** The earliest kept session a community has ever had, cancelled and skipped ones aside. */
    Optional<Instant> firstSessionAt(String organizationId, long communityId);
}
