package dev.axonia.access.adapter.out.identity;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.application.port.out.RoleBindingStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class RoleBindingDirectoryGroupClaimsTest {

    @Mock
    private RoleBindingStore bindings;

    @InjectMocks
    private RoleBindingDirectoryGroupClaims sut;

    @Test
    void aKeyAGroupBindingNames_isClaimed() {

        // given
        when(bindings.namesSubject("acme", "GROUP", "platform-admins")).thenReturn(true);

        // then
        assertThat(sut.isClaimedByARoleBinding("acme", "platform-admins")).isTrue();
    }

    @Test
    void aKeyNoBindingNames_isUnclaimed() {

        // given - the ordinary case, and the one that must stay cheap: every SCIM group push asks this
        when(bindings.namesSubject("acme", "GROUP", "everyone")).thenReturn(false);

        // then
        assertThat(sut.isClaimedByARoleBinding("acme", "everyone")).isFalse();
    }
}
