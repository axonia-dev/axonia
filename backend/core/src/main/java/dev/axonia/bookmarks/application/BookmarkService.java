package dev.axonia.bookmarks.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.bookmarks.application.port.out.ArtifactAccess;
import dev.axonia.bookmarks.application.port.out.BookmarkStore;
import dev.axonia.bookmarks.application.port.out.CommunityAccess;
import dev.axonia.bookmarks.application.port.out.CurrentUser;
import dev.axonia.bookmarks.application.port.out.OrganizationContext;
import dev.axonia.bookmarks.application.port.out.SessionAccess;
import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.bookmarks.domain.SaveState;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SavedSession;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkStore bookmarkStore;
    private final ArtifactAccess artifactAccess;
    private final SessionAccess sessionAccess;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    @Transactional
    public SaveState save(SaveableRef ref) {
        String userId = currentUser.userId();
        Source source = source(ref);
        if (!communityAccess.isMember(source.communityId(), userId)) {
            throw new ForbiddenException("Not a member of this community");
        }
        bookmarkStore.add(Bookmark.builder().organizationId(organizationContext.organizationId())
                .communityId(source.communityId()).saveableType(ref.type()).saveableId(ref.id()).memberUserId(userId)
                .sourceAuthorUserId(source.authorUserId()).build());
        return saveState(ref);
    }

    @Transactional
    public void unsave(SaveableRef ref) {
        mine(ref).ifPresent(bookmarkStore::remove);
    }

    @Transactional
    public SaveState markHelpful(long artifactId, Boolean helped) {
        Bookmark bookmark = mine(SaveableRef.artifact(artifactId))
                .orElseThrow(() -> new ForbiddenException("Save the artifact before marking it helpful"));
        Instant helpedAt = helped == null ? null : clock.instant();
        bookmarkStore.update(bookmark.toBuilder().helped(helped).helpedAt(helpedAt).build());
        return saveState(SaveableRef.artifact(artifactId));
    }

    @Transactional(readOnly = true)
    public SaveState saveState(SaveableRef ref) {
        return mine(ref).map(e -> SaveState.builder().viewerHasSaved(true).viewerHelped(e.helped()).build())
                .orElseGet(() -> SaveState.builder().viewerHasSaved(false).viewerHelped(null).build());
    }

    @Transactional(readOnly = true)
    public List<SavedItemView> mySaved() {
        Map<Long, Optional<CommunitySummary>> communities = new HashMap<>();
        List<SavedItemView> views = new ArrayList<>();
        for (Bookmark e : bookmarkStore.listNewestFirst(organizationContext.organizationId(), currentUser.userId())) {
            CommunitySummary community = communities
                    .computeIfAbsent(e.communityId(), id -> Optional.ofNullable(communityAccess.summaryById(id)))
                    .orElse(null);
            views.add(hydrate(e, community));
        }
        return views;
    }

    private SavedItemView hydrate(Bookmark e, CommunitySummary community) {
        return switch (e.saveableType()) {
            case ARTIFACT -> SavedArtifactView.builder().artifact(artifactAccess.get(e.saveableId()))
                    .community(community).savedAt(e.createdAt()).helped(e.helped()).build();
            case SESSION -> SavedSessionView.builder().session(sessionAccess.get(e.saveableId())).community(community)
                    .savedAt(e.createdAt()).build();
        };
    }

    private Source source(SaveableRef ref) {
        return switch (ref.type()) {
            case ARTIFACT -> {
                Artifact a = artifactAccess.get(ref.id());
                yield new Source(a.communityId(), a.contributorUserId());
            }
            case SESSION -> {
                SavedSession s = sessionAccess.get(ref.id());
                yield new Source(s.communityId(), s.facilitatorUserId());
            }
        };
    }

    private Optional<Bookmark> mine(SaveableRef ref) {
        return bookmarkStore.find(organizationContext.organizationId(), currentUser.userId(), ref);
    }

    private record Source(long communityId, String authorUserId) {
    }
}
