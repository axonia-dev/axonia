package dev.axonia.identity.internal;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import dev.axonia.identity.IdentityProviderDirectory.IdpGroup;
import lombok.RequiredArgsConstructor;

/**
 * Lists an Organization's SCIM-synced directory groups from {@code directory_group}, keyed by the
 * {@code reconciliation_key} that a binding's {@code subject_id} matches by construction. This is the preferred picker
 * source: it contains exactly the groups inside the customer's provisioning scope, so every suggestion is bindable.
 *
 * <p>
 * Provider-agnostic on purpose: it is not a {@link ProviderGroupSource} keyed by IdP vendor, because SCIM sync is
 * orthogonal to which vendor brokers login.
 *
 * <p>
 * <strong>Discovery only - never on the auth path.</strong>
 */
@Component
@RequiredArgsConstructor
class SyncedDirectoryGroupSource {

    private static final int MAX_GROUPS = 1000;

    private final DirectoryGroupRepository repository;

    List<IdpGroup> listGroups(String organizationId) {
        return repository.findByOrganizationId(organizationId, PageRequest.of(0, MAX_GROUPS)).stream()
                .filter(g -> g.getReconciliationKey() != null)
                .map(g -> new IdpGroup(g.getReconciliationKey(), g.getDisplayName())).toList();
    }
}
