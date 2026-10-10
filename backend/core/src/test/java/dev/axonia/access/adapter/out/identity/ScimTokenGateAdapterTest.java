package dev.axonia.access.adapter.out.identity;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;

import static org.mockito.Mockito.verify;

@MockitoSettings
class ScimTokenGateAdapterTest {

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private ScimTokenGateAdapter sut;

    @Test
    void requireRead_requiresScimRead() {

        // when
        sut.requireRead();

        // then
        verify(gate).requirePermission(Permission.SCIM_READ);
    }

    @Test
    void requireWrite_requiresScimWrite() {

        // when
        sut.requireWrite();

        // then
        verify(gate).requirePermission(Permission.SCIM_WRITE);
    }
}
