package dev.axonia.events.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.events.api.SectionType;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.domain.SectionTypeRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SectionTypeServiceTest {

    @Mock
    private SectionTypeStore sectionTypeStore;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private SectionTypeService sut;

    @Test
    void list() {

        // given
        var entity = SectionTypeRecordMother.any().id(1L).label("Deep Dive").expectsPresenter(true).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdOrderByLabelAsc("default")).thenReturn(List.of(entity));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(SectionType.builder().id(1L).label("Deep Dive").expectsPresenter(true).build());
    }

    @Test
    void list_doesNotRequireReadPermission() {

        // editing an event needs the section-type list, so reading it must not be gated by the admin read permission
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdOrderByLabelAsc("default")).thenReturn(List.of());

        // when
        sut.list();

        // then
        verify(gate, never()).requirePermission(Permission.SECTION_TYPE_READ);
    }

    @Test
    void create() {

        // given
        var request = new CreateSectionTypeRequest("Round Table", false, null, null);
        var saved = SectionTypeRecordMother.any().id(5L).label("Round Table").expectsPresenter(false).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.existsByOrganizationIdAndLabel("default", "Round Table")).thenReturn(false);
        when(sectionTypeStore.save(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

        // when
        var actual = sut.create(request);

        // then
        assertThat(actual).usingRecursiveAssertion()
                .isEqualTo(SectionType.builder().id(5L).label("Round Table").expectsPresenter(false).build());
    }

    @Test
    void create_defaultsExpectsPresenterTrueWhenNull() {

        // given
        var request = new CreateSectionTypeRequest("Workshop", null, null, null);
        var saved = SectionTypeRecordMother.any().id(6L).label("Workshop").expectsPresenter(true).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.existsByOrganizationIdAndLabel("default", "Workshop")).thenReturn(false);
        when(sectionTypeStore.save(
                org.mockito.ArgumentMatchers.argThat(SectionTypeRecord -> SectionTypeRecord.isExpectsPresenter())))
                .thenReturn(saved);

        // when
        var actual = sut.create(request);

        // then
        assertThat(actual.expectsPresenter()).isTrue();
    }

    @Test
    void create_conflict() {

        // given
        var request = new CreateSectionTypeRequest("Talk", true, null, null);
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.existsByOrganizationIdAndLabel("default", "Talk")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Talk");
    }

    @Test
    void update() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).label("Old Name").build();
        var request = new UpdateSectionTypeRequest("New Name", true, null, null);
        var saved = SectionTypeRecordMother.any().id(3L).label("New Name").expectsPresenter(true).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.existsByOrganizationIdAndLabel("default", "New Name")).thenReturn(false);
        when(sectionTypeStore.save(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

        // when
        var actual = sut.update(3L, request);

        // then
        assertThat(actual.label()).isEqualTo("New Name");
    }

    @Test
    void update_notFound() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.update(99L, new UpdateSectionTypeRequest("X", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_duplicateLabelRejected() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).label("Old Name").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.existsByOrganizationIdAndLabel("default", "Talk")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.update(3L, new UpdateSectionTypeRequest("Talk", null, null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Talk");
    }

    @Test
    void delete_unused() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.countStructureRefs(3L)).thenReturn(0L);
        when(sectionTypeStore.countSectionRefs(3L)).thenReturn(0L);

        // when
        sut.delete(3L, null);

        // then
        verify(sectionTypeStore).delete(entity);
    }

    @Test
    void delete_notFound() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(99L, null)).isInstanceOf(NotFoundException.class);
        verify(sectionTypeStore, times(0)).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_inUseWithoutTargetRejected() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.countStructureRefs(3L)).thenReturn(2L);
        when(sectionTypeStore.countSectionRefs(3L)).thenReturn(0L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("in use");
    }

    @Test
    void delete_inUseReassigns() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).build();
        var target = SectionTypeRecordMother.any().id(4L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.findByOrganizationIdAndId("default", 4L)).thenReturn(Optional.of(target));
        when(sectionTypeStore.countStructureRefs(3L)).thenReturn(2L);
        when(sectionTypeStore.countSectionRefs(3L)).thenReturn(0L);

        // when
        sut.delete(3L, 4L);

        // then
        verify(sectionTypeStore).reassignStructureRefs(3L, 4L);
        verify(sectionTypeStore).reassignSectionRefs(3L, 4L);
        verify(sectionTypeStore).delete(entity);
    }

    @Test
    void delete_reassignToSelfRejected() {

        // given
        var entity = SectionTypeRecordMother.any().id(3L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.countStructureRefs(3L)).thenReturn(1L);
        when(sectionTypeStore.countSectionRefs(3L)).thenReturn(0L);

        // when / then
        assertThatThrownBy(() -> sut.delete(3L, 3L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("reassign");
    }

    @Test
    void delete_inUseBySectionReassignsBoth() {

        // given — referenced only by a section, not a structure
        var entity = SectionTypeRecordMother.any().id(3L).build();
        var target = SectionTypeRecordMother.any().id(4L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sectionTypeStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(sectionTypeStore.findByOrganizationIdAndId("default", 4L)).thenReturn(Optional.of(target));
        when(sectionTypeStore.countStructureRefs(3L)).thenReturn(0L);
        when(sectionTypeStore.countSectionRefs(3L)).thenReturn(1L);

        // when
        sut.delete(3L, 4L);

        // then
        verify(sectionTypeStore).reassignSectionRefs(3L, 4L);
        verify(sectionTypeStore).delete(entity);
    }
}
