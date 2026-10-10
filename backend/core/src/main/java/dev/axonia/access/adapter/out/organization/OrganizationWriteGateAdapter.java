package dev.axonia.access.adapter.out.organization;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.organization.api.spi.OrganizationWriteGate;
import lombok.RequiredArgsConstructor;

/**
 * Adapter that satisfies the {@link OrganizationWriteGate} port (declared in the {@code organization} module) by
 * delegating to {@link AuthorizationGate}.
 *
 * <p>
 * Lives in {@code access} - {@code access} may depend on {@code organization} but not the other way round, so the
 * adapter sits on the side of the edge that is allowed and keeps the acyclic graph required by Spring Modulith intact.
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
