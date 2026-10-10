package dev.axonia.identity.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.IdentityProviderDirectory.IdpGroup;
import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.application.port.out.ProviderGroupSource;
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

    private final DirectoryGroupStore store;

    List<IdpGroup> listGroups(String organizationId) {
        return store.findByOrganizationId(organizationId, MAX_GROUPS).stream()
                .filter(g -> g.reconciliationKey() != null)
                .map(g -> new IdpGroup(g.reconciliationKey(), g.displayName())).toList();
    }
}
