package dev.axonia.knowledge;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import dev.axonia.knowledge.mother.ArtifactTypeEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactTypeServiceTest {

    @Mock
    private ArtifactTypeRepository artifactTypeRepository;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private ArtifactTypeService sut;

    @BeforeEach
    void setUp() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void list() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(1L).label("Recording").build();
        when(artifactTypeRepository.findByOrganizationIdOrderByLabelAsc("default")).thenReturn(List.of(entity));
        when(artifactTypeRepository.countArtifactsByType("default")).thenReturn(List.of());

        // when
        var actual = sut.list();

        // then
        assertThat(actual).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(ArtifactType.builder().id(1L).label("Recording").build());
    }

    @Test
    void list_includesArtifactCounts() {

        // given two types, one classifying four artifacts
        var recording = ArtifactTypeEntityMother.any().id(1L).label("Recording").build();
        var slides = ArtifactTypeEntityMother.any().id(2L).label("Slides").build();
        when(artifactTypeRepository.findByOrganizationIdOrderByLabelAsc("default"))
                .thenReturn(List.of(recording, slides));
        when(artifactTypeRepository.countArtifactsByType("default"))
                .thenReturn(List.of(new ArtifactTypeRepository.TypeArtifactCount(1L, 4L)));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).extracting(ArtifactType::id, ArtifactType::artifactCount).containsExactly(tuple(1L, 4L),
                tuple(2L, 0L));
    }

    @Test
    void create() {

        // given
        var request = new CreateArtifactTypeRequest("Slides", "presentation", "#d9871f");
        var saved = ArtifactTypeEntityMother.any().id(5L).label("Slides").icon("presentation").accentColor("#d9871f")
                .build();
        when(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "Slides")).thenReturn(false);
        when(artifactTypeRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.create(request);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(ArtifactTypeEntity.class);
        verify(artifactTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getIcon()).isEqualTo("presentation");
        assertThat(captor.getValue().getAccentColor()).isEqualTo("#d9871f");
        assertThat(actual).usingRecursiveAssertion().isEqualTo(
                ArtifactType.builder().id(5L).label("Slides").icon("presentation").accentColor("#d9871f").build());
    }

    @Test
    void create_conflict() {

        // given
        var request = new CreateArtifactTypeRequest("RFC", null, null);
        when(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "RFC")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ConflictException.class).hasMessageContaining("RFC")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_EXISTS"));
    }

    @Test
    void create_deniedWithoutPermission() {

        doThrow(new ForbiddenException("nope")).when(gate).requirePermission(Permission.LIBRARY_TYPE_WRITE);
        assertThatThrownBy(() -> sut.create(new CreateArtifactTypeRequest("Slides", null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(artifactTypeRepository, never()).save(any());
    }

    @Test
    void update() {

        // given
        var request = new UpdateArtifactTypeRequest("Decisions", "file-check", "#6e3ceb");
        var entity = ArtifactTypeEntityMother.any().id(7L).label("ADR").build();
        var saved = ArtifactTypeEntityMother.any().id(7L).label("Decisions").icon("file-check").accentColor("#6e3ceb")
                .build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "Decisions")).thenReturn(false);
        when(artifactTypeRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual).usingRecursiveAssertion().isEqualTo(
                ArtifactType.builder().id(7L).label("Decisions").icon("file-check").accentColor("#6e3ceb").build());
    }

    @Test
    void update_toOwnLabelAllowed() {

        // given
        var request = new UpdateArtifactTypeRequest("ADR", "file-check", "#6e3ceb");
        var entity = ArtifactTypeEntityMother.any().id(7L).label("ADR").build();
        var saved = ArtifactTypeEntityMother.any().id(7L).label("ADR").icon("file-check").accentColor("#6e3ceb")
                .build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual.label()).isEqualTo("ADR");
        verify(artifactTypeRepository, times(0)).existsByOrganizationIdAndLabel("default", "ADR");
    }

    @Test
    void update_toDifferentExistingLabelRejected() {

        // given
        var request = new UpdateArtifactTypeRequest("Slides", null, null);
        var entity = ArtifactTypeEntityMother.any().id(7L).label("ADR").build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "Slides")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.update(7L, request)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Slides")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_EXISTS"));
        verify(artifactTypeRepository, times(0)).save(any());
    }

    @Test
    void update_notFound() {

        // given
        var request = new UpdateArtifactTypeRequest("Whatever", null, null);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.update(99L, request)).isInstanceOf(NotFoundException.class);
        verify(artifactTypeRepository, times(0)).save(any());
    }

    @Test
    void delete_unused() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(3L).build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.countArtifactRefs(3L)).thenReturn(0L);

        // when
        sut.delete(3L, null);

        // then
        verify(artifactTypeRepository).delete(entity);
    }

    @Test
    void delete_notFound() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(99L, null)).isInstanceOf(NotFoundException.class);
        verify(artifactTypeRepository, times(0)).delete(any());
    }

    @Test
    void delete_inUseWithoutTargetRejected() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(3L).build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.countArtifactRefs(3L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("in use")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_IN_USE"));
    }

    @Test
    void delete_inUseReassigns() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(3L).build();
        var target = ArtifactTypeEntityMother.any().id(4L).build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 4L)).thenReturn(Optional.of(target));
        when(artifactTypeRepository.countArtifactRefs(3L)).thenReturn(2L);

        // when
        sut.delete(3L, 4L);

        // then
        verify(artifactTypeRepository).reassignArtifactRefs(3L, 4L);
        verify(artifactTypeRepository).delete(entity);
    }

    @Test
    void delete_reassignToSelfRejected() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(3L).build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.countArtifactRefs(3L)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, 3L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("reassign")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("INVALID_REASSIGNMENT"));
    }

    @Test
    void delete_inUseReassignTargetNotFound() {

        // given
        var entity = ArtifactTypeEntityMother.any().id(3L).build();
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());
        when(artifactTypeRepository.countArtifactRefs(3L)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, 99L)).isInstanceOf(NotFoundException.class);
    }
}
