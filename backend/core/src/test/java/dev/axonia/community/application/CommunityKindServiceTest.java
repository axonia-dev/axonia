package dev.axonia.community.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityKind;
import dev.axonia.community.domain.CommunityKindRecordMother;
import dev.axonia.community.domain.KindCommunityCount;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityKindServiceTest {

    @Mock
    private CommunityKindStore communityKindStore;

    @Mock
    private CommunityStore communityStore;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private AuthorizationGate gate;

    @InjectMocks
    private CommunityKindService sut;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void list_returnsKindsAsModels() {

        // given
        when(communityKindStore.listByLabel("default"))
                .thenReturn(List.of(CommunityKindRecordMother.any().id(1L).label("Guild").build()));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).singleElement().extracting(CommunityKind::label).isEqualTo("Guild");
    }

    @Test
    void list_includesCommunityCounts() {

        // given
        when(communityKindStore.listByLabel("default"))
                .thenReturn(List.of(CommunityKindRecordMother.any().id(1L).label("Guild").build()));
        when(communityStore.countByKind("default")).thenReturn(List.of(new KindCommunityCount(1L, 5L)));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).singleElement().extracting(CommunityKind::communityCount).isEqualTo(5L);
    }

    @Test
    void list_doesNotRequireReadPermission() {

        // editing a community needs the kind list, so reading it must not be gated by the admin read permission
        when(communityKindStore.listByLabel("default"))
                .thenReturn(List.of(CommunityKindRecordMother.any().id(1L).label("Guild").build()));

        // when
        sut.list();

        // then
        verify(gate, never()).requirePermission(Permission.COMMUNITY_KIND_READ);
    }

    @Test
    void create_persistsNewKind() {

        // given
        when(communityKindStore.labelTaken("default", "Tribe")).thenReturn(false);
        when(communityKindStore.save(any()))
                .thenAnswer(inv -> CommunityKindRecordMother.any().id(2L).label("Tribe").build());

        // when
        var actual = sut.create(new CreateKindRequest("Tribe"));

        // then
        assertThat(actual.label()).isEqualTo("Tribe");
        assertThat(actual.id()).isEqualTo(2L);
    }

    @Test
    void create_duplicateLabelRejected() {

        // given
        when(communityKindStore.labelTaken("default", "Guild")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.create(new CreateKindRequest("Guild"))).isInstanceOf(ConflictException.class);
        verify(communityKindStore, never()).save(any());
    }

    @Test
    void create_deniedWithoutPermission() {

        doThrow(new ForbiddenException("nope")).when(gate).requirePermission(Permission.COMMUNITY_KIND_WRITE);
        assertThatThrownBy(() -> sut.create(new CreateKindRequest("Tribe"))).isInstanceOf(ForbiddenException.class);
        verify(communityKindStore, never()).save(any());
    }

    @Test
    void rename_updatesLabel() {

        // given
        var kind = CommunityKindRecordMother.any().id(1L).label("Guild").build();
        when(communityKindStore.find("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindStore.labelTaken("default", "Chapter")).thenReturn(false);
        when(communityKindStore.save(any()))
                .thenAnswer(inv -> CommunityKindRecordMother.any().id(1L).label("Chapter").build());
        when(communityStore.countOfKind("default", 1L)).thenReturn(4L);

        // when
        var actual = sut.rename(1L, new RenameKindRequest("Chapter"));

        // then
        assertThat(actual.label()).isEqualTo("Chapter");
        assertThat(actual.communityCount()).isEqualTo(4L);
    }

    @Test
    void rename_duplicateLabelRejected() {

        // given
        when(communityKindStore.find("default", 1L))
                .thenReturn(Optional.of(CommunityKindRecordMother.any().id(1L).label("Guild").build()));
        when(communityKindStore.labelTaken("default", "Chapter")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.rename(1L, new RenameKindRequest("Chapter")))
                .isInstanceOf(ConflictException.class);
        verify(communityKindStore, never()).save(any());
    }

    @Test
    void rename_sameLabel_skipsDuplicateCheckAndSaves() {

        // given the label is unchanged - the existence check must be bypassed
        var kind = CommunityKindRecordMother.any().id(1L).label("Guild").build();
        when(communityKindStore.find("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindStore.save(any())).thenAnswer(inv -> kind);
        when(communityStore.countOfKind("default", 1L)).thenReturn(0L);

        // when
        var actual = sut.rename(1L, new RenameKindRequest("Guild"));

        // then
        assertThat(actual.label()).isEqualTo("Guild");
    }

    @Test
    void rename_unknownKind_notFound() {

        // given
        when(communityKindStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.rename(99L, new RenameKindRequest("X"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_unusedKind_deletesWithoutReassignment() {

        // given
        var kind = CommunityKindRecordMother.any().id(1L).build();
        when(communityKindStore.find("default", 1L)).thenReturn(Optional.of(kind));
        when(communityStore.countOfKind("default", 1L)).thenReturn(0L);

        // when
        sut.delete(1L, null);

        // then
        verify(communityStore, never()).reassignKind(any(), any(), any());
        verify(communityKindStore).delete(kind);
    }

    @Test
    void delete_inUseKind_reassignsThenDeletes() {

        // given
        var kind = CommunityKindRecordMother.any().id(1L).build();
        var target = CommunityKindRecordMother.any().id(2L).label("Chapter").build();
        when(communityKindStore.find("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindStore.find("default", 2L)).thenReturn(Optional.of(target));
        when(communityStore.countOfKind("default", 1L)).thenReturn(3L);

        // when
        sut.delete(1L, 2L);

        // then
        verify(communityStore).reassignKind("default", 1L, 2L);
        verify(communityKindStore).delete(kind);
    }

    @Test
    void delete_inUseKind_withoutTarget_rejected() {

        // given
        when(communityKindStore.find("default", 1L))
                .thenReturn(Optional.of(CommunityKindRecordMother.any().id(1L).build()));
        when(communityStore.countOfKind("default", 1L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(1L, null)).isInstanceOf(ConflictException.class);
        verify(communityKindStore, never()).delete(any());
    }

    @Test
    void delete_reassignToItself_rejected() {

        // given
        when(communityKindStore.find("default", 1L))
                .thenReturn(Optional.of(CommunityKindRecordMother.any().id(1L).build()));
        when(communityStore.countOfKind("default", 1L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(communityKindStore, never()).delete(any());
    }

    @Test
    void delete_unknownKind_notFound() {

        // given
        when(communityKindStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(99L, null)).isInstanceOf(NotFoundException.class);
    }
}
