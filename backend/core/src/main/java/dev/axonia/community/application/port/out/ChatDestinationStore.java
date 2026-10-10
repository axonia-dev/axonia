package dev.axonia.community.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.community.domain.ChatDestinationRecord;

public interface ChatDestinationStore {

    List<ChatDestinationRecord> listOf(String organizationId, Long communityId);

    Optional<ChatDestinationRecord> find(String organizationId, Long id);

    long countOf(String organizationId, Long communityId);

    boolean nameTaken(String organizationId, Long communityId, String name);

    /** Ids alone, for the drain's expansion: it needs somewhere to send, never the URLs. */
    List<Long> idsOf(String organizationId, Long communityId);

    /**
     * Stores a new destination. A name the community's destinations already hold, lost to a concurrent create, is
     * refused as {@code CHAT_DESTINATION_NAME_TAKEN} with
     * {@link dev.axonia.community.domain.ChatDestinationLimits#NAME_TAKEN_MESSAGE}.
     */
    ChatDestinationRecord add(ChatDestinationRecord destination);

    ChatDestinationRecord update(ChatDestinationRecord destination);

    void remove(ChatDestinationRecord destination);
}
