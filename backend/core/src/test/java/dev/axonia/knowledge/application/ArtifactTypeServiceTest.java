package dev.axonia.knowledge.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.knowledge.api.ArtifactType;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.StoredArtifactType;
import dev.axonia.knowledge.domain.StoredArtifactTypeMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

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
    private ArtifactTypeStore artifactTypeStore;

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
        var entity = StoredArtifactTypeMother.any().id(1L).label("Recording").build();
        when(artifactTypeStore.listByLabel("default")).thenReturn(List.of(entity));
        when(artifactTypeStore.artifactCountsByType("default")).thenReturn(Map.of());

        // when
        var actual = sut.list();

        // then
        assertThat(actual).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(ArtifactType.builder().id(1L).label("Recording").build());
    }

    @Test
    void list_includesArtifactCounts() {

        // given two types, one classifying four artifacts
        var recording = StoredArtifactTypeMother.any().id(1L).label("Recording").build();
        var slides = StoredArtifactTypeMother.any().id(2L).label("Slides").build();
        when(artifactTypeStore.listByLabel("default")).thenReturn(List.of(recording, slides));
        when(artifactTypeStore.artifactCountsByType("default")).thenReturn(Map.of(1L, 4L));

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
        var saved = StoredArtifactTypeMother.any().id(5L).label("Slides").icon("presentation").accentColor("#d9871f")
                .build();
        when(artifactTypeStore.labelExists("default", "Slides")).thenReturn(false);
        when(artifactTypeStore.save(any())).thenReturn(saved);

        // when
        var actual = sut.create(request);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(StoredArtifactType.class);
        verify(artifactTypeStore).save(captor.capture());
        assertThat(captor.getValue().icon()).isEqualTo("presentation");
        assertThat(captor.getValue().accentColor()).isEqualTo("#d9871f");
        assertThat(actual).usingRecursiveAssertion().isEqualTo(
                ArtifactType.builder().id(5L).label("Slides").icon("presentation").accentColor("#d9871f").build());
    }

    @Test
    void create_conflict() {

        // given
        var request = new CreateArtifactTypeRequest("RFC", null, null);
        when(artifactTypeStore.labelExists("default", "RFC")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ConflictException.class).hasMessageContaining("RFC")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_EXISTS"));
    }

    @Test
    void create_deniedWithoutPermission() {

        doThrow(new ForbiddenException("nope")).when(gate).requirePermission(Permission.LIBRARY_TYPE_WRITE);
        assertThatThrownBy(() -> sut.create(new CreateArtifactTypeRequest("Slides", null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(artifactTypeStore, never()).save(any());
    }

    @Test
    void update() {

        // given
        var request = new UpdateArtifactTypeRequest("Decisions", "file-check", "#6e3ceb");
        var entity = StoredArtifactTypeMother.any().id(7L).label("ADR").build();
        var saved = StoredArtifactTypeMother.any().id(7L).label("Decisions").icon("file-check").accentColor("#6e3ceb")
                .build();
        when(artifactTypeStore.find("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.labelExists("default", "Decisions")).thenReturn(false);
        when(artifactTypeStore.save(any())).thenReturn(saved);

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
        var entity = StoredArtifactTypeMother.any().id(7L).label("ADR").build();
        var saved = StoredArtifactTypeMother.any().id(7L).label("ADR").icon("file-check").accentColor("#6e3ceb")
                .build();
        when(artifactTypeStore.find("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.save(any())).thenReturn(saved);

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual.label()).isEqualTo("ADR");
        verify(artifactTypeStore, times(0)).labelExists("default", "ADR");
    }

    @Test
    void update_toDifferentExistingLabelRejected() {

        // given
        var request = new UpdateArtifactTypeRequest("Slides", null, null);
        var entity = StoredArtifactTypeMother.any().id(7L).label("ADR").build();
        when(artifactTypeStore.find("default", 7L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.labelExists("default", "Slides")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.update(7L, request)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Slides")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_EXISTS"));
        verify(artifactTypeStore, times(0)).save(any());
    }

    @Test
    void update_notFound() {

        // given
        var request = new UpdateArtifactTypeRequest("Whatever", null, null);
        when(artifactTypeStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.update(99L, request)).isInstanceOf(NotFoundException.class);
        verify(artifactTypeStore, times(0)).save(any());
    }

    @Test
    void delete_unused() {

        // given
        var entity = StoredArtifactTypeMother.any().id(3L).build();
        when(artifactTypeStore.find("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.artifactCount(3L)).thenReturn(0L);

        // when
        sut.delete(3L, null);

        // then
        verify(artifactTypeStore).delete(entity);
    }

    @Test
    void delete_notFound() {

        // given
        when(artifactTypeStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(99L, null)).isInstanceOf(NotFoundException.class);
        verify(artifactTypeStore, times(0)).delete(any());
    }

    @Test
    void delete_inUseWithoutTargetRejected() {

        // given
        var entity = StoredArtifactTypeMother.any().id(3L).build();
        when(artifactTypeStore.find("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.artifactCount(3L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("in use")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ARTIFACT_TYPE_IN_USE"));
    }

    @Test
    void delete_inUseReassigns() {

        // given
        var entity = StoredArtifactTypeMother.any().id(3L).build();
        var target = StoredArtifactTypeMother.any().id(4L).build();
        when(artifactTypeStore.find("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.find("default", 4L)).thenReturn(Optional.of(target));
        when(artifactTypeStore.artifactCount(3L)).thenReturn(2L);

        // when
        sut.delete(3L, 4L);

        // then
        verify(artifactTypeStore).reassignArtifacts(3L, 4L);
        verify(artifactTypeStore).delete(entity);
    }

    @Test
    void delete_reassignToSelfRejected() {

        // given
        var entity = StoredArtifactTypeMother.any().id(3L).build();
        when(artifactTypeStore.find("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.artifactCount(3L)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, 3L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("reassign")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("INVALID_REASSIGNMENT"));
    }

    @Test
    void delete_inUseReassignTargetNotFound() {

        // given
        var entity = StoredArtifactTypeMother.any().id(3L).build();
        when(artifactTypeStore.find("default", 3L)).thenReturn(Optional.of(entity));
        when(artifactTypeStore.find("default", 99L)).thenReturn(Optional.empty());
        when(artifactTypeStore.artifactCount(3L)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, 99L)).isInstanceOf(NotFoundException.class);
    }
}
