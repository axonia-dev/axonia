package dev.axonia.community.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.application.port.out.ChatDestinationStore;
import dev.axonia.community.domain.ChatDestinationLimits;
import dev.axonia.community.domain.ChatDestinationRecord;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcChatDestinationStore implements ChatDestinationStore {

    private final ChatDestinationRepository destinations;

    @Override
    @Transactional(readOnly = true)
    public List<ChatDestinationRecord> listOf(String organizationId, Long communityId) {

        return destinations.findByOrganizationIdAndCommunityIdOrderByIdAsc(organizationId, communityId).stream()
                .map(JdbcChatDestinationStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatDestinationRecord> find(String organizationId, Long id) {

        return destinations.findByOrganizationIdAndId(organizationId, id).map(JdbcChatDestinationStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOf(String organizationId, Long communityId) {

        return destinations.countByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean nameTaken(String organizationId, Long communityId, String name) {

        return destinations.existsByOrganizationIdAndCommunityIdAndName(organizationId, communityId, name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsOf(String organizationId, Long communityId) {

        return destinations.findIdsByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    /**
     * The service's name check is read-then-write: two concurrent creates for the same name can both pass it and then
     * race the database's own {@code community_chat_destination_named_once} constraint, where one of them turns into a
     * {@link DataIntegrityViolationException} instead of the {@link ValidationException} the check already promises for
     * the same problem, non-concurrently.
     *
     * <p>
     * Narrowed to that one constraint by name, the same one {@code ChatDestinationRepositoryTest} asserts against: any
     * other {@code DataIntegrityViolationException} - a genuine outage, a constraint this method knows nothing about -
     * is not this race and must not be misreported as a name collision. It is rethrown unchanged.
     *
     * <p>
     * Deliberately not extended to the service's {@code MAX_DESTINATIONS} check: that race has no constraint behind it
     * to catch, an overshoot to eleven is harmless, and the cap is a service-level message by design, not a database
     * invariant.
     */
    @Override
    @Transactional
    public ChatDestinationRecord add(ChatDestinationRecord destination) {

        try {
            return toRecord(destinations.save(toEntity(destination)));
        } catch (DataIntegrityViolationException failure) {
            var message = failure.getMostSpecificCause().getMessage();
            if (message != null && message.contains("community_chat_destination_named_once")) {
                throw new ValidationException("CHAT_DESTINATION_NAME_TAKEN", ChatDestinationLimits.NAME_TAKEN_MESSAGE);
            }
            throw failure;
        }
    }

    @Override
    @Transactional
    public ChatDestinationRecord update(ChatDestinationRecord destination) {

        return toRecord(destinations.save(toEntity(destination)));
    }

    @Override
    public void remove(ChatDestinationRecord destination) {

        destinations.delete(toEntity(destination));
    }

    static ChatDestinationRecord toRecord(ChatDestinationEntity e) {

        return ChatDestinationRecord.builder().id(e.getId()).organizationId(e.getOrganizationId())
                .communityId(e.getCommunityId()).name(e.getName()).webhookUrl(e.getWebhookUrl()).broken(e.isBroken())
                .consecutiveFailures(e.getConsecutiveFailures()).lastPostedAt(e.getLastPostedAt())
                .lastFailureAt(e.getLastFailureAt()).lastError(e.getLastError()).createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    static ChatDestinationEntity toEntity(ChatDestinationRecord d) {

        return ChatDestinationEntity.builder().id(d.getId()).organizationId(d.getOrganizationId())
                .communityId(d.getCommunityId()).name(d.getName()).webhookUrl(d.getWebhookUrl()).broken(d.isBroken())
                .consecutiveFailures(d.getConsecutiveFailures()).lastPostedAt(d.getLastPostedAt())
                .lastFailureAt(d.getLastFailureAt()).lastError(d.getLastError()).createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt()).version(d.getVersion()).build();
    }
}
