package dev.axonia.bookmarks;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import dev.axonia.bookmarks.internal.BookmarkEntity;
import dev.axonia.bookmarks.internal.BookmarkRepository;
import dev.axonia.knowledge.Artifact;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
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
        try {
            bookmarkRepository.save(BookmarkEntity.builder().organizationId(organizationContext.organizationId())
                    .communityId(source.communityId()).saveableType(ref.type()).saveableId(ref.id())
                    .memberUserId(userId).sourceAuthorUserId(source.authorUserId()).build());
        } catch (DataIntegrityViolationException idempotent) {
            // already saved — treat as success
        }
        return saveState(ref);
    }

    @Transactional
    public void unsave(SaveableRef ref) {
        mine(ref).ifPresent(bookmarkRepository::delete);
    }

    @Transactional
    public SaveState markHelpful(long artifactId, Boolean helped) {
        BookmarkEntity entity = mine(SaveableRef.artifact(artifactId))
                .orElseThrow(() -> new ForbiddenException("Save the artifact before marking it helpful"));
        Instant helpedAt = helped == null ? null : clock.instant();
        bookmarkRepository.save(entity.toBuilder().helped(helped).helpedAt(helpedAt).build());
        return saveState(SaveableRef.artifact(artifactId));
    }

    @Transactional(readOnly = true)
    public SaveState saveState(SaveableRef ref) {
        return mine(ref).map(e -> SaveState.builder().viewerHasSaved(true).viewerHelped(e.getHelped()).build())
                .orElseGet(() -> SaveState.builder().viewerHasSaved(false).viewerHelped(null).build());
    }

    @Transactional(readOnly = true)
    public List<SavedItemView> mySaved() {
        Map<Long, Optional<CommunitySummary>> communities = new HashMap<>();
        List<SavedItemView> views = new ArrayList<>();
        for (BookmarkEntity e : bookmarkRepository.findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc(
                organizationContext.organizationId(), currentUser.userId())) {
            CommunitySummary community = communities
                    .computeIfAbsent(e.getCommunityId(), id -> Optional.ofNullable(communityAccess.summaryById(id)))
                    .orElse(null);
            views.add(hydrate(e, community));
        }
        return views;
    }

    private SavedItemView hydrate(BookmarkEntity e, CommunitySummary community) {
        return switch (e.getSaveableType()) {
            case ARTIFACT -> SavedArtifactView.builder().artifact(artifactAccess.get(e.getSaveableId()))
                    .community(community).savedAt(e.getCreatedAt()).helped(e.getHelped()).build();
            case SESSION -> SavedSessionView.builder().session(sessionAccess.get(e.getSaveableId()))
                    .community(community).savedAt(e.getCreatedAt()).build();
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

    private Optional<BookmarkEntity> mine(SaveableRef ref) {
        return bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId(
                organizationContext.organizationId(), currentUser.userId(), ref.type(), ref.id());
    }

    private record Source(long communityId, String authorUserId) {
    }
}
