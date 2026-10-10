package dev.axonia.communication.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.domain.StoredPreference;

/**
 * The members' stored notification preferences. Every call names the organization explicitly, as defence in depth on
 * top of row-level security: one {@code userId} can legitimately hold a preference in more than one organization.
 */
public interface NotificationPreferenceStore {

    List<StoredPreference> storedFor(String organizationId, String userId);

    Optional<StoredPreference> storedFor(String organizationId, String userId, MessageType type);

    /** Writes the member's choice for one type in one atomic statement, whoever else is writing the same row. */
    void upsert(String organizationId, String userId, MessageType type, boolean enabled);
}
