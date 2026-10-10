package dev.axonia.recognition.api.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.SlotSource;

/**
 * Resolves the session and section type of presented slots, owned by the {@code events} module. Slot ids that no longer
 * resolve are omitted from the result, so callers must tolerate missing keys.
 */
public interface SlotSourceAccess {

    Map<Long, SlotSource> slotSources(Collection<Long> slotIds);

    /** Resolves a presented slot's authoritative attribution facts, org-scoped; empty when it does not exist. */
    Optional<ContributionSource> slotContribution(String organizationId, Long slotId);

    /** Ids of slots the user currently presents, org-scoped: the read-time source of presenter credit. */
    List<Long> slotsPresentedBy(String organizationId, String userId);
}
