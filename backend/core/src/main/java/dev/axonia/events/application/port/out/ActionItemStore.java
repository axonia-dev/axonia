package dev.axonia.events.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.application.ActionItemFilter;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.LateBindingPin;

public interface ActionItemStore {

    Optional<ActionItemRecord> findByOrganizationIdAndId(String organizationId, Long id);

    List<ActionItemRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    List<ActionItemRecord> findOpenByCheckpointSession(String organizationId, Long communityId, Long sessionId);

    List<ActionItemRecord> findByOriginSessionId(String organizationId, Long communityId, Long sessionId);

    Page<ActionItemRecord> findFiltered(String organizationId, Long communityId, ActionItemFilter filter,
            Pageable pageable);

    /** Every open item of the community that was already waiting gets this session as its checkpoint. */
    void pinLateBound(LateBindingPin pin, Instant scheduledAt, Instant at);

    /** A session that will never answer anything clears every open item that named it as its checkpoint. */
    void clearPinsFor(String organizationId, Long communityId, Long sessionId, Instant at);

    ActionItemRecord save(ActionItemRecord item);

    void delete(ActionItemRecord item);
}
