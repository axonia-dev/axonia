package dev.axonia.identity.internal;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemberAccountsAdapterTest {

    @Mock
    UserRepository userRepository;

    MemberAccountsAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new MemberAccountsAdapter(userRepository);
    }

    @Test
    void isActive_trueForActiveProfile() {
        when(userRepository.findByOrganizationIdAndUserId("default", "u-1"))
                .thenReturn(Optional.of(UserEntity.builder().userId("u-1").active(true).build()));
        assertThat(sut.isActive("default", "u-1")).isTrue();
    }

    @Test
    void isActive_falseForInactiveProfile() {
        when(userRepository.findByOrganizationIdAndUserId("default", "u-1"))
                .thenReturn(Optional.of(UserEntity.builder().userId("u-1").active(false).build()));
        assertThat(sut.isActive("default", "u-1")).isFalse();
    }

    @Test
    void isActive_falseWhenProfileMissing() {
        when(userRepository.findByOrganizationIdAndUserId("default", "ghost")).thenReturn(Optional.empty());
        assertThat(sut.isActive("default", "ghost")).isFalse();
    }
}
