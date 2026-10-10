package dev.axonia.access.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.domain.SubjectBinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcRoleBindingStoreTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    RoleBindingRepository bindingRepository;

    @InjectMocks
    JdbcRoleBindingStore sut;

    @Test
    void find_mapsEveryColumnOfTheBinding() {

        // given
        when(bindingRepository.findById(1L)).thenReturn(Optional.of(entity(1L)));

        // when
        var actual = sut.find(1L);

        // then
        assertThat(actual).contains(binding(1L));
    }

    @Test
    void listIn_mapsTheOrganizationsBindings() {

        // given
        when(bindingRepository.findByOrganizationId("acme")).thenReturn(List.of(entity(1L), entity(2L)));

        // when
        var actual = sut.listIn("acme");

        // then
        assertThat(actual).containsExactly(binding(1L), binding(2L));
    }

    @Test
    void boundTo_mapsTheRolesBindings() {

        // given
        when(bindingRepository.findByRoleId(7L)).thenReturn(List.of(entity(1L)));

        // when
        var actual = sut.boundTo(7L);

        // then
        assertThat(actual).containsExactly(binding(1L));
    }

    @Test
    void findBySubject_mapsTheSubjectsBindings() {

        // given
        when(bindingRepository.findByOrganizationIdAndSubjectTypeAndSubjectId("acme", "GROUP", "g-1"))
                .thenReturn(List.of(entity(1L)));

        // when
        var actual = sut.findBySubject("acme", "GROUP", "g-1");

        // then
        assertThat(actual).containsExactly(binding(1L));
    }

    @Test
    void namesSubject_answersWhatTheRepositoryAnswers() {

        // given
        when(bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectId("acme", "GROUP", "g-1"))
                .thenReturn(true);

        // when / then
        assertThat(sut.namesSubject("acme", "GROUP", "g-1")).isTrue();
    }

    @Test
    void subjectsHolding_answersTheRepositorysDistinctSubjects() {

        // given
        when(bindingRepository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission("acme", "USER",
                "scim:write")).thenReturn(List.of("u-1", "u-2"));

        // when / then
        assertThat(sut.subjectsHolding("acme", "USER", "scim:write")).containsExactly("u-1", "u-2");
    }

    @Test
    void exists_answersWhatTheRepositoryAnswers() {

        // given
        when(bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId("acme", "GROUP", "g-1", 7L))
                .thenReturn(true);

        // when / then
        assertThat(sut.exists("acme", "GROUP", "g-1", 7L)).isTrue();
    }

    @Test
    void save_writesEveryFieldAndReturnsTheSavedBinding() {

        // given
        when(bindingRepository.save(any(RoleBindingEntity.class))).thenReturn(entity(1L));

        // when
        var actual = sut.save(binding(null));

        // then
        var captor = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).save(captor.capture());
        assertWritten(captor.getValue(), null);
        assertThat(actual).isEqualTo(binding(1L));
    }

    @Test
    void delete_deletesTheBindingAtItsVersion() {

        // when
        sut.delete(binding(1L));

        // then
        var captor = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).delete(captor.capture());
        assertWritten(captor.getValue(), 1L);
    }

    @Test
    void deleteById_deletesTheBinding() {

        // when
        sut.deleteById(4L);

        // then
        verify(bindingRepository).deleteById(4L);
    }

    private static void assertWritten(RoleBindingEntity written, Long id) {

        assertThat(written.getId()).isEqualTo(id);
        assertThat(written.getOrganizationId()).isEqualTo("acme");
        assertThat(written.getSubjectType()).isEqualTo("GROUP");
        assertThat(written.getSubjectId()).isEqualTo("g-1");
        assertThat(written.getSubjectLabel()).isEqualTo("Engineering");
        assertThat(written.getRoleId()).isEqualTo(7L);
        assertThat(written.getCreatedAt()).isEqualTo(CREATED);
        assertThat(written.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(written.getVersion()).isEqualTo(3L);
    }

    private static RoleBindingEntity entity(Long id) {

        return RoleBindingEntity.builder().id(id).organizationId("acme").subjectType("GROUP").subjectId("g-1")
                .subjectLabel("Engineering").roleId(7L).createdAt(CREATED).updatedAt(UPDATED).version(3L).build();
    }

    private static SubjectBinding binding(Long id) {

        return SubjectBinding.builder().id(id).organizationId("acme").subjectType("GROUP").subjectId("g-1")
                .subjectLabel("Engineering").roleId(7L).createdAt(CREATED).updatedAt(UPDATED).version(3L).build();
    }
}
