package dev.axonia.bookmarks.application;

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

import dev.axonia.bookmarks.application.port.out.ArtifactAccess;
import dev.axonia.bookmarks.application.port.out.BookmarkStore;
import dev.axonia.bookmarks.application.port.out.CommunityAccess;
import dev.axonia.bookmarks.application.port.out.CurrentUser;
import dev.axonia.bookmarks.application.port.out.OrganizationContext;
import dev.axonia.bookmarks.application.port.out.SessionAccess;
import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.BookmarkMother;
import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.bookmarks.domain.SaveState;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SaveableType;
import dev.axonia.bookmarks.domain.SavedSession;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.shared.api.error.ForbiddenException;

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
    BookmarkStore bookmarkStore;
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
        sut = new BookmarkService(bookmarkStore, artifactAccess, sessionAccess, communityAccess, currentUser,
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
        when(bookmarkStore.find("default", "bob", SaveableRef.session(500L))).thenReturn(Optional.empty());

        sut.save(SaveableRef.session(500L));

        var captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkStore).add(captor.capture());
        assertThat(captor.getValue().organizationId()).isEqualTo("default");
        assertThat(captor.getValue().communityId()).isEqualTo(7L);
        assertThat(captor.getValue().saveableType()).isEqualTo(SaveableType.SESSION);
        assertThat(captor.getValue().saveableId()).isEqualTo(500L);
        assertThat(captor.getValue().memberUserId()).isEqualTo("bob");
        assertThat(captor.getValue().sourceAuthorUserId()).isEqualTo("alice");
    }

    @Test
    void saving_an_artifact_persists_with_the_contributor_as_author() {
        when(artifactAccess.get(100L)).thenReturn(artifact(1L, "alice"));
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.empty());

        sut.save(SaveableRef.artifact(100L));

        var captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkStore).add(captor.capture());
        assertThat(captor.getValue().communityId()).isEqualTo(1L);
        assertThat(captor.getValue().saveableType()).isEqualTo(SaveableType.ARTIFACT);
        assertThat(captor.getValue().saveableId()).isEqualTo(100L);
        assertThat(captor.getValue().sourceAuthorUserId()).isEqualTo("alice");
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
        Bookmark existing = BookmarkMother.anyArtifact().id(7L).build();
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.of(existing));

        SaveState state = sut.save(SaveableRef.artifact(100L));

        assertThat(state.viewerHasSaved()).isTrue();
        assertThat(state.viewerHelped()).isEqualTo(existing.helped());
    }

    @Test
    void unsave_deletes_the_matching_bookmark() {
        Bookmark existing = BookmarkMother.anyArtifact().id(7L).build();
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.of(existing));

        sut.unsave(SaveableRef.artifact(100L));

        verify(bookmarkStore).remove(existing);
    }

    @Test
    void unsave_is_a_noop_when_nothing_was_saved() {
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.empty());

        sut.unsave(SaveableRef.artifact(100L));

        verify(bookmarkStore, never()).remove(any());
    }

    @Test
    void mySaved_resolves_an_unresolvable_community_at_most_once_per_request() {
        Bookmark first = BookmarkMother.anyArtifact().id(1L).communityId(9L).saveableId(100L).build();
        Bookmark second = BookmarkMother.anyArtifact().id(2L).communityId(9L).saveableId(101L).build();
        when(bookmarkStore.listNewestFirst("default", "bob")).thenReturn(List.of(first, second));
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
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.markHelpful(100L, true)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markHelpful_stamps_the_helped_flag_and_time() {
        Bookmark existing = BookmarkMother.anyArtifact().id(7L).build();
        when(bookmarkStore.find("default", "bob", SaveableRef.artifact(100L))).thenReturn(Optional.of(existing));

        sut.markHelpful(100L, true);

        var captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkStore).update(captor.capture());
        assertThat(captor.getValue().helped()).isTrue();
        assertThat(captor.getValue().helpedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
    }

    @Test
    void mySaved_hydrates_each_kind_newest_first() {
        Bookmark art = BookmarkMother.anyArtifact().id(1L).build();
        Bookmark ses = BookmarkMother.anySession().id(2L).communityId(7L).build();
        when(bookmarkStore.listNewestFirst("default", "bob")).thenReturn(List.of(ses, art));
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
