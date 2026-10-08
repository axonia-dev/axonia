package dev.axonia.access.internal;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class RoleBindingDirectoryGroupClaimsTest {

    @Mock
    private RoleBindingRepository repository;

    @InjectMocks
    private RoleBindingDirectoryGroupClaims sut;

    @Test
    void aKeyAGroupBindingNames_isClaimed() {

        // given
        when(repository.existsByOrganizationIdAndSubjectTypeAndSubjectId("acme", "GROUP", "platform-admins"))
                .thenReturn(true);

        // then
        assertThat(sut.isClaimedByARoleBinding("acme", "platform-admins")).isTrue();
    }

    @Test
    void aKeyNoBindingNames_isUnclaimed() {

        // given - the ordinary case, and the one that must stay cheap: every SCIM group push asks this
        when(repository.existsByOrganizationIdAndSubjectTypeAndSubjectId("acme", "GROUP", "everyone"))
                .thenReturn(false);

        // then
        assertThat(sut.isClaimedByARoleBinding("acme", "everyone")).isFalse();
    }
}
