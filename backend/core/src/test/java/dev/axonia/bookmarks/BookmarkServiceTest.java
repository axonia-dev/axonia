package dev.axonia.bookmarks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.ForbiddenException;
import dev.axonia.bookmarks.internal.BookmarkEntity;
import dev.axonia.bookmarks.internal.BookmarkRepository;
import dev.axonia.bookmarks.mother.BookmarkEntityMother;
import dev.axonia.knowledge.Artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    BookmarkRepository bookmarkRepository;
    @Mock
    ArtifactAccess artifactAccess;
    @Mock
    SessionAccess sessionAccess;
    @Mock
    CommunityAccess communityAccess;
    @Mock
    CurrentUser currentUser;
    @Mock
    OrganizationContext organizationContext;
    Clock clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), ZoneOffset.UTC);

    BookmarkService sut;

    @BeforeEach
    void setUp() {
        sut = new BookmarkService(bookmarkRepository, artifactAccess, sessionAccess, communityAccess, currentUser,
                organizationContext, clock);
        when(currentUser.userId()).thenReturn("bob");
        // Not every test reaches the persistence path that reads this — lenient avoids a false
        // UnnecessaryStubbingException on e.g. the not-a-member test, which throws before then.
        lenient().when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void saving_a_session_checks_membership_and_persists_with_facilitator_as_author() {
        when(sessionAccess.get(500L)).thenReturn(SavedSession.builder().id(500L).communityId(7L).title("Retro")
                .facilitatorUserId("alice").slotCount(3).build());
        when(communityAccess.isMember(7L, "bob")).thenReturn(true);
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.SESSION, 500L)).thenReturn(Optional.empty());

        sut.save(SaveableRef.session(500L));

        var captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo("default");
        assertThat(captor.getValue().getCommunityId()).isEqualTo(7L);
        assertThat(captor.getValue().getSaveableType()).isEqualTo(SaveableType.SESSION);
        assertThat(captor.getValue().getSaveableId()).isEqualTo(500L);
        assertThat(captor.getValue().getMemberUserId()).isEqualTo("bob");
        assertThat(captor.getValue().getSourceAuthorUserId()).isEqualTo("alice");
    }

    @Test
    void saving_an_artifact_persists_with_the_contributor_as_author() {
        when(artifactAccess.get(100L)).thenReturn(artifact(1L, "alice"));
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.empty());

        sut.save(SaveableRef.artifact(100L));

        var captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getCommunityId()).isEqualTo(1L);
        assertThat(captor.getValue().getSaveableType()).isEqualTo(SaveableType.ARTIFACT);
        assertThat(captor.getValue().getSaveableId()).isEqualTo(100L);
        assertThat(captor.getValue().getSourceAuthorUserId()).isEqualTo("alice");
    }

    @Test
    void saving_when_not_a_member_is_forbidden() {
        when(artifactAccess.get(100L)).thenReturn(artifact(1L, "alice"));
        when(communityAccess.isMember(1L, "bob")).thenReturn(false);

        assertThatThrownBy(() -> sut.save(SaveableRef.artifact(100L))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void saving_twice_is_idempotent_and_returns_the_current_save_state() {
        when(artifactAccess.get(100L)).thenReturn(artifact(1L, "alice"));
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        BookmarkEntity existing = BookmarkEntityMother.anyArtifact().id(7L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.of(existing));
        when(bookmarkRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate save"));

        SaveState state = sut.save(SaveableRef.artifact(100L));

        assertThat(state.viewerHasSaved()).isTrue();
        assertThat(state.viewerHelped()).isEqualTo(existing.getHelped());
    }

    @Test
    void unsave_deletes_the_matching_bookmark() {
        BookmarkEntity existing = BookmarkEntityMother.anyArtifact().id(7L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.of(existing));

        sut.unsave(SaveableRef.artifact(100L));

        verify(bookmarkRepository).delete(existing);
    }

    @Test
    void unsave_is_a_noop_when_nothing_was_saved() {
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.empty());

        sut.unsave(SaveableRef.artifact(100L));

        verify(bookmarkRepository, never()).delete(any());
    }

    @Test
    void mySaved_resolves_an_unresolvable_community_at_most_once_per_request() {
        BookmarkEntity first = BookmarkEntityMother.anyArtifact().id(1L).communityId(9L).saveableId(100L).build();
        BookmarkEntity second = BookmarkEntityMother.anyArtifact().id(2L).communityId(9L).saveableId(101L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc("default", "bob"))
                .thenReturn(List.of(first, second));
        when(artifactAccess.get(100L)).thenReturn(artifact(9L, "alice"));
        when(artifactAccess.get(101L)).thenReturn(artifact(9L, "alice"));
        when(communityAccess.summaryById(9L)).thenReturn(null);

        List<SavedItemView> views = sut.mySaved();

        assertThat(views).hasSize(2);
        assertThat(((SavedArtifactView) views.get(0)).community()).isNull();
        assertThat(((SavedArtifactView) views.get(1)).community()).isNull();
        verify(communityAccess, times(1)).summaryById(9L);
    }

    @Test
    void markHelpful_requires_an_existing_save() {
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.markHelpful(100L, true)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markHelpful_stamps_the_helped_flag_and_time() {
        BookmarkEntity existing = BookmarkEntityMother.anyArtifact().id(7L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.of(existing));

        sut.markHelpful(100L, true);

        var captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getHelped()).isTrue();
        assertThat(captor.getValue().getHelpedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
    }

    @Test
    void mySaved_hydrates_each_kind_newest_first() {
        BookmarkEntity art = BookmarkEntityMother.anyArtifact().id(1L).build();
        BookmarkEntity ses = BookmarkEntityMother.anySession().id(2L).communityId(7L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc("default", "bob"))
                .thenReturn(List.of(ses, art));
        when(sessionAccess.get(500L))
                .thenReturn(SavedSession.builder().id(500L).communityId(7L).title("Retro").build());
        when(artifactAccess.get(100L)).thenReturn(artifact(1L, "alice"));
        when(communityAccess.summaryById(7L)).thenReturn(new CommunitySummary(7L, "guild", "Guild", null));
        when(communityAccess.summaryById(1L)).thenReturn(new CommunitySummary(1L, "chapter", "Chapter", null));

        List<SavedItemView> views = sut.mySaved();

        assertThat(views).hasSize(2);
        assertThat(views.get(0)).isInstanceOf(SavedSessionView.class);
        assertThat(views.get(1)).isInstanceOf(SavedArtifactView.class);
    }

    private static Artifact artifact(long communityId, String contributor) {
        return Artifact.builder().id(100L).communityId(communityId).typeId(3L).type("rfc").title("An RFC")
                .contributorUserId(contributor).loveCount(0).build();
    }
}
