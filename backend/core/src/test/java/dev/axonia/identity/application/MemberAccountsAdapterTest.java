package dev.axonia.identity.application;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemberAccountsAdapterTest {

    @Mock
    UserStore userStore;

    MemberAccountsAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new MemberAccountsAdapter(userStore);
    }

    @Test
    void isActive_trueForActiveProfile() {
        when(userStore.findByOrganizationIdAndUserId("default", "u-1"))
                .thenReturn(Optional.of(UserAccount.builder().userId("u-1").active(true).build()));
        assertThat(sut.isActive("default", "u-1")).isTrue();
    }

    @Test
    void isActive_falseForInactiveProfile() {
        when(userStore.findByOrganizationIdAndUserId("default", "u-1"))
                .thenReturn(Optional.of(UserAccount.builder().userId("u-1").active(false).build()));
        assertThat(sut.isActive("default", "u-1")).isFalse();
    }

    @Test
    void isActive_falseWhenProfileMissing() {
        when(userStore.findByOrganizationIdAndUserId("default", "ghost")).thenReturn(Optional.empty());
        assertThat(sut.isActive("default", "ghost")).isFalse();
    }
}
