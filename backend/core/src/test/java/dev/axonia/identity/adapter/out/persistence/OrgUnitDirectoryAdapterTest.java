package dev.axonia.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrgUnitDirectoryAdapterTest {

    private static final String ORGANIZATION = "default";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrgUnitDirectoryAdapter sut;

    @Test
    void teamOf_userWithUnit_returnsOrgUnit() {

        // given
        var user = UserEntity.builder().userId("sub-1").organizationId(ORGANIZATION).businessUnit("Acme")
                .department("Platform").team("Payments").build();
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, "sub-1")).thenReturn(Optional.of(user));

        // when
        var result = sut.teamOf(ORGANIZATION, "sub-1");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().businessUnit()).isEqualTo("Acme");
        assertThat(result.get().department()).isEqualTo("Platform");
        assertThat(result.get().team()).isEqualTo("Payments");
    }

    @Test
    void teamOf_userWithNoUnitData_returnsEmpty() {

        // given (self-serve: all labels null)
        var user = UserEntity.builder().userId("sub-2").organizationId(ORGANIZATION).build();
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, "sub-2")).thenReturn(Optional.of(user));

        // then
        assertThat(sut.teamOf(ORGANIZATION, "sub-2")).isEmpty();
    }

    @Test
    void teamOf_userNotFound_returnsEmpty() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, "missing")).thenReturn(Optional.empty());

        // then
        assertThat(sut.teamOf(ORGANIZATION, "missing")).isEmpty();
    }

    @Test
    void teamsOf_returnsOnlyUsersThatHaveAUnit() {

        // given
        var withUnit = UserEntity.builder().userId("sub-1").organizationId(ORGANIZATION).team("Payments").build();
        var withoutUnit = UserEntity.builder().userId("sub-2").organizationId(ORGANIZATION).build();
        when(userRepository.findByOrganizationIdAndUserIdIn(ORGANIZATION, List.of("sub-1", "sub-2")))
                .thenReturn(List.of(withUnit, withoutUnit));

        // when
        var result = sut.teamsOf(ORGANIZATION, List.of("sub-1", "sub-2"));

        // then
        assertThat(result).containsOnlyKeys("sub-1");
        assertThat(result.get("sub-1").team()).isEqualTo("Payments");
    }
}
