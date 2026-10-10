package dev.axonia.community.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** The communities of an Organization, as another context reads them. */
public interface Communities {

    Community getBySlug(String slug);

    Community getById(Long id);

    /**
     * The name of the single community that IS a hub Organization, for a caller acting on an arbitrary Organization by
     * id. Empty when the Organization is not a hub.
     */
    Optional<String> hubCommunityName(String organizationId);

    /** Org-explicit, for a caller with no request to read the Organization from. */
    CommunityIdentity identityById(String organizationId, Long id);

    /** Keyed by id; an id that no longer resolves is absent rather than an exception. */
    Map<Long, CommunityIdentity> identitiesByIds(Collection<Long> ids);

    Page<Community> directory(String q, Pageable pageable);
}
