package dev.axonia.access.adapter.out.identity;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;

import static org.mockito.Mockito.verify;

@MockitoSettings
class MemberNamesGateAdapterTest {

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private MemberNamesGateAdapter sut;

    @Test
    void requireRead_requiresMemberRead() {

        sut.requireRead();

        verify(gate).requirePermission(Permission.MEMBER_READ);
    }

    @Test
    void requireWrite_requiresMemberWrite() {

        sut.requireWrite();

        verify(gate).requirePermission(Permission.MEMBER_WRITE);
    }
}
