package dev.axonia.access.adapter.out.organization;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;

import static org.mockito.Mockito.verify;

@MockitoSettings
class OrganizationWriteGateAdapterTest {

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private OrganizationWriteGateAdapter sut;

    @Test
    void requireWrite_delegatesToAuthorizationGateWithOrganizationWritePermission() {

        // when
        sut.requireWrite();

        // then
        verify(gate).requirePermission(Permission.ORGANIZATION_WRITE);
    }

    @Test
    void requireCommunicationWrite_delegatesToAuthorizationGateWithCommunicationWritePermission() {

        // when
        sut.requireCommunicationWrite();

        // then
        verify(gate).requirePermission(Permission.COMMUNICATION_WRITE);
    }
}
