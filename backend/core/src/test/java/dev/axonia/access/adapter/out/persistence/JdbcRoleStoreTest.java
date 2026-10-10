package dev.axonia.access.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.domain.RoleDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcRoleStoreTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    RoleRepository roleRepository;

    @InjectMocks
    JdbcRoleStore sut;

    @Test
    void find_mapsEveryColumnOfTheRole() {

        // given
        when(roleRepository.findById(1L)).thenReturn(Optional.of(entity(1L)));

        // when
        var actual = sut.find(1L);

        // then
        assertThat(actual).contains(role(1L));
    }

    @Test
    void find_isEmptyForAnUnknownRole() {

        assertThat(sut.find(9L)).isEmpty();
    }

    @Test
    void findAll_mapsEveryRoleFound() {

        // given
        when(roleRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(entity(1L), entity(2L)));

        // when
        var actual = sut.findAll(Set.of(1L, 2L));

        // then
        assertThat(actual).containsExactly(role(1L), role(2L));
    }

    @Test
    void listIn_mapsTheOrganizationsRoles() {

        // given
        when(roleRepository.findByOrganizationId("acme")).thenReturn(List.of(entity(1L)));

        // when
        var actual = sut.listIn("acme");

        // then
        assertThat(actual).containsExactly(role(1L));
    }

    @Test
    void findByName_mapsTheNamedRole() {

        // given
        when(roleRepository.findByOrganizationIdAndName("acme", "Owner")).thenReturn(Optional.of(entity(1L)));

        // when
        var actual = sut.findByName("acme", "Owner");

        // then
        assertThat(actual).contains(role(1L));
    }

    @Test
    void save_writesEveryFieldAndReturnsTheSavedRole() {

        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(entity(1L));

        // when
        var actual = sut.save(role(null));

        // then
        var captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(written -> {
            assertThat(written.getId()).isNull();
            assertThat(written.getOrganizationId()).isEqualTo("acme");
            assertThat(written.getName()).isEqualTo("Owner");
            assertThat(written.getDescription()).isEqualTo("Full control");
            assertThat(written.isSystem()).isTrue();
            assertThat(written.getSlug()).isEqualTo("owner");
            assertThat(written.getPermissions()).extracting(RolePermissionEntity::getPermission)
                    .containsExactly("role:read");
            assertThat(written.getCreatedAt()).isEqualTo(CREATED);
            assertThat(written.getUpdatedAt()).isEqualTo(UPDATED);
            assertThat(written.getVersion()).isEqualTo(3L);
        });
        assertThat(actual).isEqualTo(role(1L));
    }

    @Test
    void save_keepsARepeatedPermissionAsTwoRows() {

        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(entity(1L));

        // when
        sut.save(role(null).toBuilder().permissions(List.of("role:read", "role:read")).build());

        // then
        var captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).hasSize(2);
    }

    @Test
    void deleteById_deletesTheRole() {

        // when
        sut.deleteById(4L);

        // then
        verify(roleRepository).deleteById(4L);
    }

    private static RoleEntity entity(Long id) {

        return RoleEntityMother.any().id(id).organizationId("acme").name("Owner").description("Full control")
                .system(true).slug("owner").permissions(Set.of(RoleEntityMother.permission("role:read")))
                .createdAt(CREATED).updatedAt(UPDATED).version(3L).build();
    }

    private static RoleDefinition role(Long id) {

        return RoleDefinition.builder().id(id).organizationId("acme").name("Owner").description("Full control")
                .system(true).slug("owner").permissions(List.of("role:read")).createdAt(CREATED).updatedAt(UPDATED)
                .version(3L).build();
    }
}
