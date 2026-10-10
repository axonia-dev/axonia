package dev.axonia.access.adapter.out.identity;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.identity.api.spi.ScimTokenGate;
import lombok.RequiredArgsConstructor;

/** Satisfies the {@link ScimTokenGate} port by delegating to {@link AuthorizationGate}. */
@Component
@RequiredArgsConstructor
class ScimTokenGateAdapter implements ScimTokenGate {

    private final AuthorizationGate gate;

    @Override
    public void requireRead() {
        gate.requirePermission(Permission.SCIM_READ);
    }

    @Override
    public void requireWrite() {
        gate.requirePermission(Permission.SCIM_WRITE);
    }
}
