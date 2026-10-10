package dev.axonia.access.adapter.out.identity;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.identity.api.spi.DirectoryGroupClaims;
import lombok.RequiredArgsConstructor;

/**
 * Answers {@link DirectoryGroupClaims} from {@code role_binding}: a key is claimed when some binding in the
 * Organization already names it as a {@code GROUP} subject.
 *
 * <p>
 * The role the binding grants is deliberately not part of the answer. The question SCIM asks is whether accepting this
 * group would silently hand out authority somebody pre-bound, and that is true of any bound role, not only the
 * dangerous ones - a role's permission set can be widened later, at which point a group waved through today would grow
 * with it.
 */
@Component
@RequiredArgsConstructor
class RoleBindingDirectoryGroupClaims implements DirectoryGroupClaims {

    private static final String GROUP = "GROUP";

    private final RoleBindingStore bindings;

    @Override
    @Transactional(readOnly = true)
    public boolean isClaimedByARoleBinding(String organizationId, String reconciliationKey) {

        return bindings.namesSubject(organizationId, GROUP, reconciliationKey);
    }
}
