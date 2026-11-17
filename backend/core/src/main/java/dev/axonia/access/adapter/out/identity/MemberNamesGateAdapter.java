package dev.axonia.access.adapter.out.identity;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.identity.api.spi.MemberNamesGate;
import lombok.RequiredArgsConstructor;

/** Satisfies the {@link MemberNamesGate} port by delegating to {@link AuthorizationGate}. */
@Component
@RequiredArgsConstructor
class MemberNamesGateAdapter implements MemberNamesGate {

    private final AuthorizationGate gate;

    @Override
    public void requireRead() {
        gate.requirePermission(Permission.MEMBER_READ);
    }

    @Override
    public void requireWrite() {
        gate.requirePermission(Permission.MEMBER_WRITE);
    }
}
