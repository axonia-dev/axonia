package dev.axonia.community;

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
import dev.axonia.community.internal.CommunityKindRepository;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.mother.CommunityKindEntityMother;

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
    private CommunityKindRepository communityKindRepository;

    @Mock
    private CommunityRepository communityRepository;

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
        when(communityKindRepository.findByOrganizationIdOrderByLabelAsc("default"))
                .thenReturn(List.of(CommunityKindEntityMother.any().id(1L).label("Guild").build()));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).singleElement().extracting(CommunityKind::label).isEqualTo("Guild");
    }

    @Test
    void list_includesCommunityCounts() {

        // given
        when(communityKindRepository.findByOrganizationIdOrderByLabelAsc("default"))
                .thenReturn(List.of(CommunityKindEntityMother.any().id(1L).label("Guild").build()));
        when(communityRepository.countCommunitiesByKind("default"))
                .thenReturn(List.of(new CommunityRepository.KindCommunityCount(1L, 5L)));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).singleElement().extracting(CommunityKind::communityCount).isEqualTo(5L);
    }

    @Test
    void list_doesNotRequireReadPermission() {

        // editing a community needs the kind list, so reading it must not be gated by the admin read permission
        when(communityKindRepository.findByOrganizationIdOrderByLabelAsc("default"))
                .thenReturn(List.of(CommunityKindEntityMother.any().id(1L).label("Guild").build()));

        // when
        sut.list();

        // then
        verify(gate, never()).requirePermission(Permission.COMMUNITY_KIND_READ);
    }

    @Test
    void create_persistsNewKind() {

        // given
        when(communityKindRepository.existsByOrganizationIdAndLabel("default", "Tribe")).thenReturn(false);
        when(communityKindRepository.save(any()))
                .thenAnswer(inv -> CommunityKindEntityMother.any().id(2L).label("Tribe").build());

        // when
        var actual = sut.create(new CreateKindRequest("Tribe"));

        // then
        assertThat(actual.label()).isEqualTo("Tribe");
        assertThat(actual.id()).isEqualTo(2L);
    }

    @Test
    void create_duplicateLabelRejected() {

        // given
        when(communityKindRepository.existsByOrganizationIdAndLabel("default", "Guild")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.create(new CreateKindRequest("Guild"))).isInstanceOf(ConflictException.class);
        verify(communityKindRepository, never()).save(any());
    }

    @Test
    void create_deniedWithoutPermission() {

        doThrow(new ForbiddenException("nope")).when(gate).requirePermission(Permission.COMMUNITY_KIND_WRITE);
        assertThatThrownBy(() -> sut.create(new CreateKindRequest("Tribe"))).isInstanceOf(ForbiddenException.class);
        verify(communityKindRepository, never()).save(any());
    }

    @Test
    void rename_updatesLabel() {

        // given
        var kind = CommunityKindEntityMother.any().id(1L).label("Guild").build();
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindRepository.existsByOrganizationIdAndLabel("default", "Chapter")).thenReturn(false);
        when(communityKindRepository.save(any()))
                .thenAnswer(inv -> CommunityKindEntityMother.any().id(1L).label("Chapter").build());
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(4L);

        // when
        var actual = sut.rename(1L, new RenameKindRequest("Chapter"));

        // then
        assertThat(actual.label()).isEqualTo("Chapter");
        assertThat(actual.communityCount()).isEqualTo(4L);
    }

    @Test
    void rename_duplicateLabelRejected() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(CommunityKindEntityMother.any().id(1L).label("Guild").build()));
        when(communityKindRepository.existsByOrganizationIdAndLabel("default", "Chapter")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.rename(1L, new RenameKindRequest("Chapter")))
                .isInstanceOf(ConflictException.class);
        verify(communityKindRepository, never()).save(any());
    }

    @Test
    void rename_sameLabel_skipsDuplicateCheckAndSaves() {

        // given the label is unchanged — the existence check must be bypassed
        var kind = CommunityKindEntityMother.any().id(1L).label("Guild").build();
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindRepository.save(any())).thenAnswer(inv -> kind);
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(0L);

        // when
        var actual = sut.rename(1L, new RenameKindRequest("Guild"));

        // then
        assertThat(actual.label()).isEqualTo("Guild");
    }

    @Test
    void rename_unknownKind_notFound() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.rename(99L, new RenameKindRequest("X"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_unusedKind_deletesWithoutReassignment() {

        // given
        var kind = CommunityKindEntityMother.any().id(1L).build();
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(kind));
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(0L);

        // when
        sut.delete(1L, null);

        // then
        verify(communityRepository, never()).reassignKind(any(), any(), any());
        verify(communityKindRepository).delete(kind);
    }

    @Test
    void delete_inUseKind_reassignsThenDeletes() {

        // given
        var kind = CommunityKindEntityMother.any().id(1L).build();
        var target = CommunityKindEntityMother.any().id(2L).label("Chapter").build();
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(kind));
        when(communityKindRepository.findByOrganizationIdAndId("default", 2L)).thenReturn(Optional.of(target));
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(3L);

        // when
        sut.delete(1L, 2L);

        // then
        verify(communityRepository).reassignKind("default", 1L, 2L);
        verify(communityKindRepository).delete(kind);
    }

    @Test
    void delete_inUseKind_withoutTarget_rejected() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(CommunityKindEntityMother.any().id(1L).build()));
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(1L, null)).isInstanceOf(ConflictException.class);
        verify(communityKindRepository, never()).delete(any());
    }

    @Test
    void delete_reassignToItself_rejected() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(CommunityKindEntityMother.any().id(1L).build()));
        when(communityRepository.countByOrganizationIdAndKindId("default", 1L)).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> sut.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(communityKindRepository, never()).delete(any());
    }

    @Test
    void delete_unknownKind_notFound() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(99L, null)).isInstanceOf(NotFoundException.class);
    }
}
