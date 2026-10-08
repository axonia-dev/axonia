package dev.axonia.access.internal;

import org.springframework.stereotype.Component;

import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.organization.OrganizationWriteGate;
import lombok.RequiredArgsConstructor;

/**
 * Adapter that satisfies the {@link OrganizationWriteGate} port (declared in the {@code organization} module) by
 * delegating to {@link AuthorizationGate}.
 *
 * <p>
 * Lives in {@code access.internal} - {@code access} may depend on {@code organization} but not the other way round, so
 * the adapter sits on the side of the edge that is allowed and keeps the acyclic graph required by Spring Modulith
 * intact.
 */
@Component
@RequiredArgsConstructor
class OrganizationWriteGateAdapter implements OrganizationWriteGate {

    private final AuthorizationGate gate;

    @Override
    public void requireWrite() {

        gate.requirePermission(Permission.ORGANIZATION_WRITE);
    }

    @Override
    public void requireCommunicationWrite() {

        gate.requirePermission(Permission.COMMUNICATION_WRITE);
    }
}
